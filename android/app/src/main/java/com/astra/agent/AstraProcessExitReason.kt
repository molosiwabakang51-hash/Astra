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
    fun latest(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Android process exit reason unavailable on this OS."
        }
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return "Android process exit reason unavailable."
            val exit = manager.getHistoricalProcessExitReasons(
                context.packageName, 0, 5
            ).firstOrNull { it.processName == context.packageName }
                ?: return "No matching historical Astra process exit was recorded."
            describe(exit.reason)
        } catch (_: SecurityException) {
            "Android did not permit reading the process exit reason."
        } catch (_: RuntimeException) {
            "Android could not provide a process exit reason."
        }
    }

    /**
     * Restrict model fallback to an OS-confirmed native crash / low-memory
     * termination *after* the interrupted inference checkpoint began.
     * Do not confuse older crashes, user force-stop, Java exceptions or
     * an absent process-exit record with evidence of a model crash.
     */
    internal fun nativeOrMemoryExitSince(
        context: Context, inferenceBeganMs: Long
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || inferenceBeganMs <= 0L) {
            return false
        }
        return runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return@runCatching false
            val now = System.currentTimeMillis()
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                .firstOrNull { it.processName == context.packageName }
                ?.let { wasNativeOrMemoryCrashDuringInference(
                    it.reason, inferenceBeganMs, it.timestamp, now
                ) } ?: false
        }.getOrDefault(false)
    }

    /** Pure time/reason validation, independently JVM-tested. */
    internal fun wasNativeOrMemoryCrashDuringInference(
        reason: Int, beganMs: Long, exitMs: Long, nowMs: Long
    ): Boolean =
        reason in setOf(
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_LOW_MEMORY
        ) &&
            beganMs > 0L &&
            nowMs >= beganMs &&
            exitMs >= beganMs &&
            exitMs <= nowMs + 30_000L &&
            exitMs - beganMs <= 24L * 60 * 60 * 1000

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
