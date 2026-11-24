package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Integration contract: the public, privacy-safe error emitted by the brain
 * must be understood by the chat retry policy, not just a synthetic message.
 */
class AstraTimeoutRetryIntegrationTest {
    @Test fun sanitizedGenerationDeadlineOffersRetryOnlyAfterCleanup() {
        val safe = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.MODEL_CHAT,
            IllegalStateException(
                "Offline generation exceeded its time budget. Retry after cleanup."
            )
        )
        val tail = listOf(
            AstraResponseRetry.Message("You", "Explain bond convexity"),
            AstraResponseRetry.Message("Astra", safe)
        )
        assertEquals(
            "Explain bond convexity",
            AstraResponseRetry.eligibleQuestion(tail, busy = false)
        )
        assertNull(AstraResponseRetry.eligibleQuestion(tail, busy = true))
        assertFalse(safe.contains("Retry after cleanup."))
    }

    @Test fun unrelatedSanitizedFailureCannotReplaySuccessfulTurn() {
        val safe = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.MODEL_CHAT,
            IllegalStateException("unexpected native error")
        )
        val tail = listOf(
            AstraResponseRetry.Message("You", "Explain bond convexity"),
            AstraResponseRetry.Message("Astra", safe)
        )
        assertEquals(
            "Explain bond convexity",
            AstraResponseRetry.eligibleQuestion(tail, busy = false)
        )
        assertNull(AstraResponseRetry.eligibleQuestion(
            listOf(
                AstraResponseRetry.Message("You", "Explain bond convexity"),
                AstraResponseRetry.Message("Astra", "Convexity measures curvature.")
            ),
            busy = false
        ))
    }
}
