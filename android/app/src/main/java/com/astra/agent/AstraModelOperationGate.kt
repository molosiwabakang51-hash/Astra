package com.astra.agent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide single-owner gate for native Qwen3 1.7B and changes to its
 * installed GGUF. A per-brain mutex cannot protect a second brain instance or
 * a Settings import/download that replaces the model while JNI uses it.
 *
 * Staging a new model may run concurrently with inference; promotion of the
 * fully verified temporary file must wait until the native session exits.
 * Cooperative cancellation NEVER releases the gate before the native call
 * has returned and the inference coroutine has unwound.
 *
 * This cannot interrupt a blocked JNI call. Do not introduce a timeout or
 * a force-unlock here: that would permit concurrent use of one native model.
 */
internal object AstraModelOperationGate {
    private val nativeOrPromotion = Mutex()
    private val activeImport = Mutex()
    private val activeDownload = Mutex()

    suspend fun <T> withNativeSession(action: suspend () -> T): T =
        nativeOrPromotion.withLock { action() }

    /** Only call this from import/download, never inside withNativeSession. */
    suspend fun <T> withPromotion(action: suspend () -> T): T =
        nativeOrPromotion.withLock { action() }

    /** Two imports use the same .importing file; reject overlapping attempts. */
    suspend fun <T> withImport(action: suspend () -> T): T {
        check(activeImport.tryLock()) {
            "A Qwen3 1.7B model import is already in progress. " +
                "Finish or cancel the current import before trying again."
        }
        try {
            return action()
        } finally {
            activeImport.unlock()
        }
    }

    /** Resume and ranged download use the same .downloading file. */
    suspend fun <T> withDownload(action: suspend () -> T): T {
        check(activeDownload.tryLock()) {
            "A Qwen3 1.7B model download is already in progress. " +
                "Finish or cancel the current download before trying again."
        }
        try {
            return action()
        } finally {
            activeDownload.unlock()
        }
    }
}
