package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AstraResponseRetryTest {
    private fun tail(answer: String) = listOf(
        AstraResponseRetry.Message("You", "Explain bond convexity"),
        AstraResponseRetry.Message("Astra", answer)
    )

    @Test fun offersRetryAfterStoppedOrFailedModel() {
        for (answer in listOf(
            "Offline response stopped.",
            "The response was interrupted when Astra closed.",
            "Part of the answer\n\n[Response stopped]",
            "Part\n\n[Response stopped]\n\nSources: local notes",
            "Offline model unavailable: model missing.",
            "Offline model unavailable. Android may not have enough available RAM. " +
                "Retry from Chat or verify the 1.7B model in Settings.",
            "Offline model unavailable. The offline AI engine did not finish starting in time.",
            "Offline response interrupted.",
            "The offline model did not produce a usable answer. Please try again.",
            "Offline model unavailable. Offline generation exceeded its time budget. Retry after cleanup."
        )) {
            assertEquals("Explain bond convexity",
                AstraResponseRetry.eligibleQuestion(tail(answer), false))
        }
    }

    @Test fun neverReplaysSuccessOrBusyGeneration() {
        assertNull(AstraResponseRetry.eligibleQuestion(tail("A good answer."), false))
        assertNull(AstraResponseRetry.eligibleQuestion(tail("Offline response stopped."), true))
        assertNull(AstraResponseRetry.eligibleQuestion(emptyList(), false))
        assertNull(AstraResponseRetry.eligibleQuestion(
            listOf(AstraResponseRetry.Message("You", "x"),
                AstraResponseRetry.Message("You", "Offline response stopped.")), false))
    }

    @Test fun rejectsEmptyPromptsAndUnrelatedLastMessages() {
        assertNull(AstraResponseRetry.eligibleQuestion(listOf(
            AstraResponseRetry.Message("You", " "),
            AstraResponseRetry.Message("Astra", "Offline response stopped.")), false))
        assertNull(AstraResponseRetry.eligibleQuestion(listOf(
            AstraResponseRetry.Message("Astra", "x"),
            AstraResponseRetry.Message("Astra", "Offline response stopped.")), false))
    }
}
