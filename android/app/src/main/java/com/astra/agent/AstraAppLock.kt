package com.astra.agent

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.view.WindowManager

/**
 * System-owned authentication: never handle fingerprint templates,
 * voiceprints, PINs or passwords in application code.
 *
 * This is an opt-in UI access gate. It does NOT encrypt already-stored
 * SQLite/files/preferences and is not a replacement for Keystore encryption.
 */
internal object AstraAppLock {
    private const val PREFS = "astra"
    private const val ENABLED_KEY = "astra_app_lock_enabled"
    private const val BACKGROUND_DELAY_KEY = "astra_app_lock_background_delay_ms"
    private const val IDLE_TIMEOUT_KEY = "astra_app_lock_idle_timeout_ms"

    private val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED_KEY, enabled).commit()) {
            "Could not save Astra's app lock preference."
        }
    }

    /**
     * Lock-delay preference defaults to immediate for existing installs.
     * Only four bounded, recognized values are accepted; corrupt preferences fail closed.
     */
    fun backgroundDelayMillis(context: Context): Long {
        val saved = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(BACKGROUND_DELAY_KEY, 0L)
        }.getOrDefault(0L)
        return if (saved in AstraAppLockPolicy.backgroundDelaysMillis) saved else 0L
    }

    fun setBackgroundDelayMillis(context: Context, delayMillis: Long) {
        require(delayMillis in AstraAppLockPolicy.backgroundDelaysMillis)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(BACKGROUND_DELAY_KEY, delayMillis).commit()) {
            "Could not save Astra's background lock delay."
        }
    }

    /**
     * Foreground inactivity lock is opt-in, including for previously secured installs.
     * An invalid or unreadable stored preference is treated as the disabled default;
     * it cannot bypass the independently enforced cold-launch/background lock.
     */
    fun idleTimeoutMillis(context: Context): Long {
        val saved = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(IDLE_TIMEOUT_KEY, 0L)
        }.getOrDefault(0L)
        return if (saved in AstraAppLockPolicy.idleTimeoutsMillis) saved else 0L
    }

    fun setIdleTimeoutMillis(context: Context, timeoutMillis: Long) {
        require(timeoutMillis in AstraAppLockPolicy.idleTimeoutsMillis)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(IDLE_TIMEOUT_KEY, timeoutMillis).commit()) {
            "Could not save Astra's inactivity lock timeout."
        }
    }

    fun canUnlock(context: Context): Boolean = runCatching {
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    fun protectRecentsAndScreenshots(activity: Activity, lockedFeatureEnabled: Boolean) {
        if (lockedFeatureEnabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            activity.setRecentsScreenshotEnabled(false)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            activity.setRecentsScreenshotEnabled(true)
        }
    }

    fun authenticate(
        activity: Activity,
        onSuccess: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        if (!canUnlock(activity)) {
            onUnavailable()
            return
        }
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("Unlock Astra")
                .setSubtitle("Use your fingerprint or Android screen lock")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // No access is granted on cancellation, lockout or OS failure.
                        onUnavailable()
                    }
                }
            )
        } catch (_: RuntimeException) {
            // No bypass on unsupported hardware or transient Android errors.
            onUnavailable()
        }
    }
}

/** Independent, JVM-testable access rules for the opt-in secure workspace. */
internal object AstraAppLockPolicy {
    val backgroundDelaysMillis: List<Long> = listOf(0L, 30_000L, 120_000L, 300_000L)
    // Off is the migration-safe default; selected limits apply only while active.
    val idleTimeoutsMillis: List<Long> = listOf(0L, 120_000L, 300_000L, 900_000L)

    fun idleTimeoutLabel(timeoutMillis: Long): String = when (timeoutMillis) {
        0L -> "Off"
        120_000L -> "After 2 minutes"
        300_000L -> "After 5 minutes"
        900_000L -> "After 15 minutes"
        else -> "Off"
    }

    fun shouldLockForInactivity(
        enabled: Boolean, timeoutMillis: Long, idleElapsedMillis: Long
    ): Boolean {
        if (!enabled || timeoutMillis == 0L) return false
        if (timeoutMillis !in idleTimeoutsMillis) return false
        // Time should be monotonic; an impossible clock regression locks closed.
        return idleElapsedMillis < 0L || idleElapsedMillis >= timeoutMillis
    }

    fun labelForDelay(delayMillis: Long): String = when (delayMillis) {
        0L -> "Immediately"
        30_000L -> "After 30 seconds"
        120_000L -> "After 2 minutes"
        300_000L -> "After 5 minutes"
        else -> "Immediately"
    }

    fun requiresUnlock(enabled: Boolean, unlocked: Boolean): Boolean = enabled && !unlocked
    fun unlockedOnColdLaunch(enabled: Boolean): Boolean = !enabled

    // Retain the original immediate-lock policy for existing callers and tests.
    fun unlockedAfterBackground(enabled: Boolean): Boolean = !enabled

    /**
     * Monotonic elapsed-time check. If time moves backwards, configuration is invalid,
     * or the configured delay has elapsed, the secured workspace must lock.
     */
    fun unlockedAfterBackground(
        enabled: Boolean, delayMillis: Long, elapsedMillis: Long
    ): Boolean {
        if (!enabled) return true
        return delayMillis in backgroundDelaysMillis &&
            delayMillis > 0L && elapsedMillis >= 0L && elapsedMillis < delayMillis
    }
}
