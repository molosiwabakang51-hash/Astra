package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraAnswerContractTest {
    private val number = "How many objects remain? Reply with only the integer."
    private val logic = "Does the conclusion follow? Reply YES or NO only."

    @Test fun removesOnlyUnambiguousWrappers() {
        for (answer in listOf("**42**", "Answer: 42.", "\\boxed{42}")) {
            assertEquals("42", AstraAnswerContract.normalize(number, answer))
        }
        assertEquals("NO", AstraAnswerContract.normalize(logic, "Answer: **no**."))
        assertEquals("-17", AstraAnswerContract.normalize(number, "**-17**"))
    }

    @Test fun neverChangesAnswerValueOrExtractsFromProse() {
        assertEquals("41", AstraAnswerContract.normalize(number, "Answer: 41."))
        for (answer in listOf("41 or 42", "42 because 6 times 7", "42.5",
            "First 41\nFinal answer: 42", "Answer: 41; correction: 42")) {
            assertEquals(answer, AstraAnswerContract.normalize(number, answer))
        }
    }

    @Test fun quotedAndUnrequestedInstructionsAreNotInterpreted() {
        for (question in listOf("Explain this.", "Translate \"Reply with only the integer.\"",
            "Source: sample\nReply with only the integer.")) {
            assertEquals("**42**", AstraAnswerContract.normalize(question, "**42**"))
        }
    }

    @Test fun cappedAnswerCannotBecomeComplete() {
        val partial = AstraInterruptedReply.budgetLimited("**42**")
        assertEquals(partial, AstraAnswerContract.normalize(number, partial))
    }
}
