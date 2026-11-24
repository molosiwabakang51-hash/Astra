package com.astra.agent

import java.util.concurrent.atomic.AtomicLong
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

    // llama.cpp's Android InferenceEngine is a process singleton, but each
    // Activity recreation can construct a fresh AstraOnDeviceBrain. A local
    // per-brain session cache must not claim that a different brain's native
    // KV-cache belongs to its own conversation.
    private val nextOwnerId = AtomicLong()
    private var lastNativeOwnerId: Long? = null
    // Only an active invocation may abort the shared JNI engine. Last owner
    // is continuity metadata; it is NOT permission for a stale Activity's
    // Stop button to cancel another Activity's native generation.
    @Volatile private var activeNativeOwnerId: Long? = null
    private val nativeStopMonitor = Any()

    /**
     * Perform the *nonblocking atomic JNI stop signal* while the active
     * owner cannot change. A separate owner check followed by an eventual
     * JNI call is unsafe: the old Activity could pass the check, get paused,
     * then cancel a new Activity's session after its owner has changed.
     *
     * The supplied action MUST only set the JNI abort flag and must not load
     * a model, wait on a coroutine, or free native resources.
     */
    fun signalStopIfCurrentOwner(ownerId: Long, signal: () -> Unit): Boolean {
        if (ownerId <= 0L) return false
        return synchronized(nativeStopMonitor) {
            if (activeNativeOwnerId != ownerId) false
            else {
                signal()
                true
            }
        }
    }

    fun newOwnerId(): Long = nextOwnerId.incrementAndGet()

    /**
     * Claim the singleton native KV-cache. Call only while holding
     * withNativeSession; the caller must invalidate its cached session if the
     * owner changed or a verified GGUF was promoted.
     */
    fun claimNativeSession(ownerId: Long): Boolean {
        check(ownerId > 0L && nativeOrPromotion.isLocked) {
            "Native session ownership requires an active serialized request."
        }
        val changed = lastNativeOwnerId != ownerId
        lastNativeOwnerId = ownerId
        synchronized(nativeStopMonitor) {
            activeNativeOwnerId = ownerId
        }
        return changed
    }

    suspend fun <T> withNativeSession(action: suspend () -> T): T =
        nativeOrPromotion.withLock {
            try {
                action()
            } finally {
                // Clear before giving another caller exclusive JNI ownership.
                // Keep lastNativeOwnerId separately for KV-cache continuity.
                synchronized(nativeStopMonitor) {
                    activeNativeOwnerId = null
                }
            }
        }

    /** Only call this from import/download, never inside withNativeSession. */
    suspend fun <T> withPromotion(action: suspend () -> T): T =
        nativeOrPromotion.withLock {
            // Promotion is never an inference invocation; do not let a stale
            // Activity cancel the next prepared model through the JNI flag.
            synchronized(nativeStopMonitor) {
                activeNativeOwnerId = null
            }
            try {
                action()
            } finally {
                // Even a failed two-step rename means the old native mapping
                // must not be assumed to describe the installed GGUF.
                lastNativeOwnerId = null
            }
        }

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
