package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraConditionalReviewTest {
    private val trace = "Trace conditional counter: x=6; repeat 5 times: if x is even then x=x/2 else x=x*3+1. What is x?"
    private fun pair(q: String, a: String) = listOf(
        AstraChatArchive.Message("You", q), AstraChatArchive.Message("Astra", a)
    )

    @Test fun explicitReviewComputesAndDisclosesExactScope() {
        val result = AstraCognitiveReview.resolve("Verify this conditional trace: $trace", emptyList())
            as AstraCognitiveReview.Result.Local
        assertTrue(result.answer.contains("result: 8."))
        assertTrue(result.answer.contains("5 bounded iterations"))
        assertTrue(result.answer.contains("condition true 3 times"))
        assertTrue(result.answer.contains("Only the explicitly supplied"))
        assertEquals(AstraCognitiveReview.Result.NotRequested,
            AstraCognitiveReview.resolve(trace, emptyList()))
    }

    @Test fun priorAnswerReviewCorrectsOnlyUnambiguousScalar() {
        val wrong = AstraCognitiveReview.resolve("Verify your answer", pair("model only: $trace", "16"))
            as AstraCognitiveReview.Result.Local
        assertTrue(wrong.answer.contains("incorrect; corrected result: 8."))
        for (scalar in listOf("8", "+008", "008")) {
            val correct = AstraCognitiveReview.resolve("Verify your answer", pair(trace, scalar))
                as AstraCognitiveReview.Result.Local
            assertTrue(correct.answer.contains("correct."))
        }
        for (ambiguous in listOf("8 or 16", "8 because it is even", "++8", "8.0")) {
            val reference = AstraCognitiveReview.resolve("Verify your answer", pair(trace, ambiguous))
                as AstraCognitiveReview.Result.Local
            assertTrue(ambiguous, reference.answer.contains("result: 8."))
            assertTrue(ambiguous, reference.answer.contains("preceding response was not compared"))
            assertFalse(ambiguous, reference.answer.contains("incorrect; corrected result"))
            assertFalse(ambiguous, reference.answer.contains("— correct."))
        }
    }

    @Test fun unsupportedInputsNeverBecomeIndependentEvidence() {
        val invalid = trace.replace("x=x/2", "x=x/0")
        val rejected = AstraCognitiveReview.resolve("Check this conditional trace: $invalid", emptyList())
            as AstraCognitiveReview.Result.Local
        assertTrue(rejected.answer.contains("could not verify"))
        assertFalse(rejected.answer.contains("INDEPENDENT CONDITIONAL CHECK"))
        assertTrue(AstraCognitiveReview.resolve("Verify your answer", pair(invalid, "8"))
            is AstraCognitiveReview.Result.Native)
        assertEquals(AstraCognitiveReview.Result.NotRequested,
            AstraCognitiveReview.resolve("Translate: Verify this conditional trace: $trace", emptyList()))
        val partial = AstraInterruptedReply.budgetLimited("8")
        val incomplete = AstraCognitiveReview.resolve("Verify your answer", pair(trace, partial))
            as AstraCognitiveReview.Result.Local
        assertFalse(incomplete.answer.contains("INDEPENDENT CONDITIONAL CHECK"))
    }
}
