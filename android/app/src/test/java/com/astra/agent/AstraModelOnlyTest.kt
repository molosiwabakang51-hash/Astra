package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AstraModelOnlyTest {
    private fun msg(role: String, text: String) = AstraChatArchive.Message(role, text)

    @Test fun explicitModelRoutePreservesQuestionWithoutExecutingIt() {
        assertEquals("What is 2+2?", AstraModelOnly.question("Model only: What is 2+2?"))
        assertEquals("delete all notes", AstraModelOnly.question("model only: delete all notes"))
        assertEquals("Explain this:\nA fictional example.", AstraModelOnly.question(
            "MODEL ONLY: Explain this:\nA fictional example."
        ))
        for (input in listOf("model only:", "Explain the phrase model only: example",
            "Quote: model only: do something", "model only please", "ordinary chat")) {
            assertNull(input, AstraModelOnly.question(input))
        }
    }

    @Test fun onlyContiguousCompletedModelOnlyTurnsAreReplayed() {
        val history = listOf(
            msg("You", "ask my books bond risk"),
            msg("Astra", "Private source excerpt"),
            msg("You", "model only: Compare two fictional objects"),
            msg("Astra", "They differ in size."),
            msg("You", "model only: Which is larger?"),
            msg("Astra", "The second.")
        )
        assertEquals(4, AstraModelOnly.previousTurnCount(history))
        assertEquals(0, AstraModelOnly.previousTurnCount(history + listOf(
            msg("You", "add task example"), msg("Astra", "Task saved.")
        )))
        assertEquals(0, AstraModelOnly.previousTurnCount(history + msg("You", "model only: Continue")))
    }

    @Test fun interruptedAndMalformedTurnsCannotSeedPureModelContext() {
        val history = listOf(
            msg("You", "model only: First question"),
            msg("Astra", "Complete answer"),
            msg("You", "model only: Second question"),
            msg("Astra", "Partial answer\n\n[Response stopped]")
        )
        assertEquals(0, AstraModelOnly.previousTurnCount(history))
        assertEquals(2, AstraModelOnly.previousTurnCount(history + listOf(
            msg("You", "model only: New independent topic"),
            msg("Astra", "Completed answer")
        )))
        assertEquals(0, AstraModelOnly.previousTurnCount(listOf(
            msg("Astra", "model only: not the owner"), msg("Astra", "Answer")
        )))
    }
}
