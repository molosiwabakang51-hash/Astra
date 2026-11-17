package com.astra.agent

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Exposes Android's recorded reason for Astra's latest process death.
 * Never reads a tombstone, stack trace, model file, or conversation.
 * A reason is a diagnostic hint, not proof of the inference root cause.
 */
internal object AstraProcessExitReason {
    /**
     * Match an Android process exit to the current persisted inference only.
     * An unrelated crash from a previous app launch must not be presented as
     * evidence for this interruption. Invalid timestamps fail closed.
     */
    internal fun matchesCheckpointExit(
        inferenceStartedAtMs: Long,
        exitAtMs: Long,
        observedAtMs: Long
    ): Boolean = inferenceStartedAtMs > 0L &&
        inferenceStartedAtMs <= observedAtMs &&
        exitAtMs >= inferenceStartedAtMs &&
        exitAtMs <= observedAtMs

    fun latest(context: Context, inferenceStartedAtMs: Long? = null): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Android process exit reason unavailable on this OS."
        }
        val observedAtMs = System.currentTimeMillis()
        if (inferenceStartedAtMs != null &&
            (inferenceStartedAtMs <= 0L || inferenceStartedAtMs > observedAtMs)
        ) {
            return "Inference start timestamp unavailable; no exit cause attributed."
        }
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return "Android process exit reason unavailable."
            val exit = manager.getHistoricalProcessExitReasons(
                context.packageName, 0, 10
            ).firstOrNull {
                it.processName == context.packageName &&
                    (inferenceStartedAtMs == null || matchesCheckpointExit(
                        inferenceStartedAtMs, it.timestamp, observedAtMs
                    ))
            } ?: return if (inferenceStartedAtMs == null) {
                "No historical Astra process exit was recorded."
            } else {
                "No matching Android process exit was recorded after " +
                    "the interrupted inference began."
            }
            labelForContext(exit.reason, inferenceStartedAtMs != null)
        } catch (_: SecurityException) {
            "Android did not permit reading the process exit reason."
        } catch (_: RuntimeException) {
            "Android could not provide a process exit reason."
        }
    }

    /** General Settings history is never attributed to the current chat. */
    internal fun labelForContext(reason: Int, correlatedWithInference: Boolean): String =
        if (correlatedWithInference) describe(reason)
        else "Latest historical Astra process exit (not linked to this chat): " +
            describe(reason)

    fun describe(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE ->
            "Android recorded a native crash (JNI/C++); capture the crash log."
        ApplicationExitInfo.REASON_LOW_MEMORY ->
            "Android recorded low-memory termination. The 1.7B model may exceed usable RAM."
        ApplicationExitInfo.REASON_CRASH ->
            "Android recorded a Java/Kotlin crash; capture the crash log."
        ApplicationExitInfo.REASON_ANR ->
            "Android recorded an app-not-responding termination."
        ApplicationExitInfo.REASON_USER_REQUESTED ->
            "Android recorded a user-requested process stop; this is not crash proof."
        else ->
            "Android recorded process exit reason code $reason; cause not established."
    }
}
