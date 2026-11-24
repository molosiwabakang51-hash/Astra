package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraCognitiveSessionTest {
    private val domain = AstraCognitiveAssessment.Domain.QUANTITATIVE
    private val items = (1..4).map {
        AstraCognitiveAssessment.Item("q-$it", domain, 2, "2 + 2?", setOf("4"))
    }

    @Test fun answersAdvanceExactlyOnceAndSessionEnds() {
        val session = AstraCognitiveSession(domain, items)
        assertEquals(4, session.remaining)
        assertEquals("q-1", session.currentItem?.id)
        assertTrue(session.answer(AstraCognitiveAssessment.Attempt("q-1", "4")).correct)
        assertEquals(3, session.remaining)
        assertEquals("q-2", session.currentItem?.id)
        (2..4).forEach { session.answer(AstraCognitiveAssessment.Attempt("q-$it", "4")) }
        assertTrue(session.finished)
        assertNull(session.currentItem)
        assertEquals(1.0, session.summary().accuracy!!, 0.0)
        assertEquals(3, session.recommendedDifficulty(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfOrderAnswersRejected() {
        AstraCognitiveSession(domain, items).answer(AstraCognitiveAssessment.Attempt("q-2", "4"))
    }

    @Test(expected = IllegalStateException::class)
    fun completedSessionRejectsExtraAnswers() {
        val session = AstraCognitiveSession(domain, items)
        items.forEach { session.answer(AstraCognitiveAssessment.Attempt(it.id, "4")) }
        session.answer(AstraCognitiveAssessment.Attempt("q-4", "4"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateQuestionIdsRejected() {
        AstraCognitiveSession(domain, listOf(items[0], items[0]))
    }

    @Test(expected = IllegalArgumentException::class)
    fun crossDomainQuestionsRejected() {
        AstraCognitiveSession(domain, listOf(items[0].copy(domain = AstraCognitiveAssessment.Domain.VERBAL)))
    }
}
