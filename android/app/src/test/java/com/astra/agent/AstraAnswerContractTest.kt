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

    @Test fun recognizesExplicitTerminalFormatVariants() {
        for (suffix in listOf("Reply only with an integer.", "Answer only the integer.",
            "Respond with the integer only.", "Reply with only the integer.")) {
            assertEquals(suffix, "42", AstraAnswerContract.normalize(
                "How many remain? $suffix", "Answer: **42**."))
        }
        for (suffix in listOf("Reply only YES or NO.", "Answer with only YES or NO.",
            "Respond only with YES or NO.", "Reply with YES or NO only.",
            "Reply YES or NO only.")) {
            assertEquals(suffix, "NO", AstraAnswerContract.normalize(
                "Does it follow? $suffix", "Answer: **no**."))
        }
    }

    @Test fun ambiguousQuoteBoundariesAndNonexclusiveRequestsStayUntouched() {
        val wrapped = "**42**"
        for (q in listOf("Explain 'A note says. Reply only the integer.",
            "Explain ‘A note says. Reply only the integer.",
            "Explain a note that says ’Answer. Reply only the integer.",
            "Explain this. Answer the integer.", "Explain this. Reply with a number.",
            "Explain this. Reply with the integer only. Then explain.",
            "Source: example\nReply with the integer only.")) {
            assertEquals(q, wrapped, AstraAnswerContract.normalize(q, wrapped))
        }
        assertEquals("**no**", AstraAnswerContract.normalize(
            "Does it follow? Reply YES or NO.", "**no**"))
        assertEquals("42", AstraAnswerContract.normalize(
            "What's the count? Reply only with the integer.", wrapped))
        assertEquals("42", AstraAnswerContract.normalize(
            "What’s the count? Reply with the integer only.", wrapped))
    }

    @Test fun cappedAnswerCannotBecomeComplete() {
        val partial = AstraInterruptedReply.budgetLimited("**42**")
        assertEquals(partial, AstraAnswerContract.normalize(number, partial))
    }
}
