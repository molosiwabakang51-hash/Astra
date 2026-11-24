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

    @Test fun difficultyRequiresDistinctItemsInOneDomain() {
        fun result(id: String, domain: AstraCognitiveAssessment.Domain, answer: String, hints: Int = 0) =
            AstraCognitiveAssessment.evaluate(
                item.copy(id = id, domain = domain),
                AstraCognitiveAssessment.Attempt(id, answer, hints = hints)
            )
        val d = AstraCognitiveAssessment.Domain.QUANTITATIVE
        val other = AstraCognitiveAssessment.Domain.VERBAL
        val correct = (1..4).map { result("quant-$it", d, "84") }
        val wrong = (1..3).map { result("wrong-$it", d, "0") }
        assertEquals(3, AstraCognitiveAssessment.nextDifficulty(2, d, correct))
        assertEquals(1, AstraCognitiveAssessment.nextDifficulty(2, d, wrong))
        assertEquals(2, AstraCognitiveAssessment.nextDifficulty(2, d, List(4) { correct[0] }))
        assertEquals(2, AstraCognitiveAssessment.nextDifficulty(2, d, correct.take(2)))
        assertEquals(2, AstraCognitiveAssessment.nextDifficulty(2, d, correct.map { it.copy(hints = 1) }))
        assertEquals(2, AstraCognitiveAssessment.nextDifficulty(2, d, correct.take(2) + correct.take(2).map { it.copy(itemId = "verbal-${it.itemId}", domain = other) }))
        assertEquals(5, AstraCognitiveAssessment.nextDifficulty(5, d, correct))
    }
}
