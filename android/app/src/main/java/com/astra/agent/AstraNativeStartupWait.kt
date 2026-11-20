package com.astra.agent

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wait for the JNI engine to report readiness without confusing an internal
 * initialization timeout with a deliberate owner cancellation.
 *
 * withTimeoutOrNull recognizes only its own deadline. A cancelled chat job,
 * a conversation switch, or a caller's earlier deadline still propagates
 * CancellationException, preserving session and chat-recovery semantics.
 */
internal object AstraNativeStartupWait {
    const val TIMEOUT_MS = 30_000L
    const val TIMEOUT_ADVICE =
        "Qwen3 1.7B native engine initialization did not finish in time. " +
            "Try again after closing other apps or restarting Astra. " +
            "Your model, notes and chats are retained."

    private data class Finished<out T>(val value: T)

    suspend fun <T> awaitReady(
        timeoutMs: Long = TIMEOUT_MS,
        operation: suspend () -> T
    ): T {
        require(timeoutMs > 0L) { "Native engine timeout must be positive." }
        return withTimeoutOrNull(timeoutMs) {
            // Preserve successful nullable values without confusing them
            // with a withTimeoutOrNull timeout.
            Finished(operation())
        }?.value ?: throw IllegalStateException(TIMEOUT_ADVICE)
    }
}
