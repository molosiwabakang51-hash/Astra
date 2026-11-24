package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraCognitiveAssessmentTest {
    private val item = AstraCognitiveAssessment.Item(
        "quant-001", AstraCognitiveAssessment.Domain.QUANTITATIVE, 2,
        "What is 12 multiplied by 7?", setOf("84", "eighty-four")
    )

    @Test fun objectiveAnswersAndHints() {
        val result = AstraCognitiveAssessment.evaluate(
            item, AstraCognitiveAssessment.Attempt(item.id, " 84 ", hints = 1, confidence = 70)
        )
        assertTrue(result.correct)
        assertEquals(1, result.hints)
        assertEquals(70, result.confidence)
        assertFalse(AstraCognitiveAssessment.evaluate(
            item, AstraCognitiveAssessment.Attempt(item.id, "85")
        ).correct)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedItemsRejected() {
        AstraCognitiveAssessment.evaluate(item, AstraCognitiveAssessment.Attempt("other", "84"))
    }

    @Test fun noUnsupportedAbilityScore() {
        assertNull(AstraCognitiveAssessment.summary(emptyList()).accuracy)
        val a = AstraCognitiveAssessment.evaluate(item, AstraCognitiveAssessment.Attempt(item.id, "84"))
        val b = AstraCognitiveAssessment.evaluate(item, AstraCognitiveAssessment.Attempt(item.id, "85"))
        assertEquals(0.5, AstraCognitiveAssessment.summary(listOf(a, b)).accuracy!!, 0.0001)
    }

    @Test fun difficultyRespondsToIndependentPerformance() {
        val correct = AstraCognitiveAssessment.evaluate(item, AstraCognitiveAssessment.Attempt(item.id, "84"))
        val wrong = AstraCognitiveAssessment.evaluate(item, AstraCognitiveAssessment.Attempt(item.id, "0"))
        val hinted = AstraCognitiveAssessment.evaluate(item, AstraCognitiveAssessment.Attempt(item.id, "84", hints = 1))
        assertEquals(3, AstraCognitiveAssessment.nextDifficulty(2, List(4) { correct }))
        assertEquals(1, AstraCognitiveAssessment.nextDifficulty(2, List(3) { wrong }))
        assertEquals(2, AstraCognitiveAssessment.nextDifficulty(2, List(4) { hinted }))
        assertEquals(2, AstraCognitiveAssessment.nextDifficulty(2, listOf(correct)))
        assertEquals(5, AstraCognitiveAssessment.nextDifficulty(5, List(4) { correct }))
    }
}
