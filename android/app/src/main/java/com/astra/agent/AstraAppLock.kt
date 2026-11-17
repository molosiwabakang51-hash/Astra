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

    fun canUnlock(context: Context): Boolean =
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

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
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Unlock Astra")
            .setSubtitle("Use your fingerprint or Android screen lock")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        try {
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
    fun requiresUnlock(enabled: Boolean, unlocked: Boolean): Boolean = enabled && !unlocked
    fun unlockedOnColdLaunch(enabled: Boolean): Boolean = !enabled
    fun unlockedAfterBackground(enabled: Boolean): Boolean = !enabled
}
