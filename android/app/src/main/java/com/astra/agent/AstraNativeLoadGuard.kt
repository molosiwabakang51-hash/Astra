package com.astra.agent

import android.app.ActivityManager
import android.content.Context

/**
 * Defensive preflight before loading a fresh native GGUF context.
 *
 * Android exposes its own low-memory threshold for the current device. Do
 * not guess a minimum RAM requirement from the model file size or the JVM
 * heap: llama.cpp allocates native memory outside the managed Java heap.
 *
 * This reduces the risk of launching a JNI load while Android is already
 * reporting memory pressure. It cannot prevent all OOM, JNI crashes or
 * unsupported-hardware failures, and a physical arm64 test is still required.
 */
internal object AstraNativeLoadGuard {
    const val RETRY_ADVICE =
        "Android reports low available memory. Close other apps or restart " +
        "your phone, then retry offline AI. Your model and conversations are " +
        "still stored locally."

    /** Pure policy for JVM tests; unknown/invalid system metrics fail open. */
    fun shouldDefer(
        androidLowMemory: Boolean,
        availableRamBytes: Long,
        deviceLowMemoryThresholdBytes: Long
    ): Boolean = androidLowMemory ||
        (availableRamBytes >= 0L &&
            deviceLowMemoryThresholdBytes > 0L &&
            availableRamBytes < deviceLowMemoryThresholdBytes)

    /**
     * If ActivityManager cannot report RAM, preserve existing behavior rather
     * than inventing a device restriction. This check runs only for new loads,
     * not for already-loaded chat turns.
     */
    fun requireCapacity(context: Context) {
        val memory = runCatching {
            val manager = context.applicationContext.getSystemService(
                Context.ACTIVITY_SERVICE
            ) as? ActivityManager ?: return
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }.getOrNull() ?: return

        if (shouldDefer(memory.lowMemory, memory.availMem, memory.threshold)) {
            throw AstraNativeLowMemoryException(RETRY_ADVICE)
        }
    }
}

internal class AstraNativeLowMemoryException(message: String) :
    IllegalStateException(message)
