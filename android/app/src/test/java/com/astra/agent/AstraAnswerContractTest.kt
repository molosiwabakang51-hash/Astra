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

    @Test fun explicitUnknownContractPreservesExactKnownOrUnknownValue() {
        for (suffix in listOf("Reply with only the integer, or UNKNOWN if not established.",
            "Answer only with an integer, or unknown if not established.",
            "Respond with the integer only, or UNKNOWN if not established.")) {
            val question = "What was my greenhouse budget? $suffix"
            for (answer in listOf("unknown", "**unknown**", "Answer: UNKNOWN.", "\\boxed{unknown}")) {
                assertEquals(answer, "UNKNOWN", AstraAnswerContract.normalize(question, answer))
            }
            for (answer in listOf("**42**", "Final answer: 42.", "\\boxed{42}")) {
                assertEquals(answer, "42", AstraAnswerContract.normalize(question, answer))
            }
            assertEquals("-17", AstraAnswerContract.normalize(question, "Answer: -17."))
        }
    }

    @Test fun uncertaintyNormalizationDoesNotInventOrInferAbstention() {
        val question = "What was my greenhouse budget? Reply with only the integer, or UNKNOWN if not established."
        for (answer in listOf("I don't know.", "Unknown because the amount was not recorded.",
            "42 or UNKNOWN", "Answer: UNKNOWN; actually 42", "UNKNOWN\n42", "N/A",
            "Answer: unknown?", "Answer: 42.5", "unknown or 42", "42 coins")) {
            assertEquals(answer, AstraAnswerContract.normalize(question, answer))
        }
        for (partial in listOf(AstraInterruptedReply.budgetLimited("**UNKNOWN**"),
            AstraInterruptedReply.budgetLimited("**42**"),
            AstraInterruptedReply.EMPTY_MODEL_REPLY)) {
            assertEquals(partial, AstraAnswerContract.normalize(question, partial))
        }
    }

    @Test fun unknownContractRequiresUnambiguousTerminalOwnerInstruction() {
        val answer = "Answer: **unknown**."
        for (question in listOf(number, logic, "What is the amount?",
            "Explain this. Reply with only the integer, or UNKNOWN if not established. Then explain.",
            "Translate 'Reply with only the integer, or UNKNOWN if not established.",
            "Source: note\nReply with only the integer, or UNKNOWN if not established.",
            "ORIGINAL QUESTION: What is the amount? Reply with only the integer, or UNKNOWN if not established.",
            "How many remain? Reply with the integer or UNKNOWN.")) {
            assertEquals(question, answer, AstraAnswerContract.normalize(question, answer))
        }
    }

    @Test fun generatedScalarContractsNeverChangeNumericValue() {
        val random = java.util.Random(194829L)
        val question = "What was my greenhouse budget? Reply with only the integer, or UNKNOWN if not established."
        repeat(200) {
            val number = random.nextInt(20001) - 10000
            assertEquals(number.toString(), AstraAnswerContract.normalize(
                question, "Final answer: **$number**."))
            val ambiguous = "$number or UNKNOWN"
            assertEquals(ambiguous, AstraAnswerContract.normalize(question, ambiguous))
        }
    }

    @Test fun cappedAnswerCannotBecomeComplete() {
        val partial = AstraInterruptedReply.budgetLimited("**42**")
        assertEquals(partial, AstraAnswerContract.normalize(number, partial))
    }
}
