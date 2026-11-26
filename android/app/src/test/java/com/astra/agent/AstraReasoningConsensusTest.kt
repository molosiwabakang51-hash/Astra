package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraReasoningConsensusTest {
    private val integer = "What is the final count? Reply with only the integer."
    private val binary = "Does the conclusion follow? Reply YES or NO only."
    private fun attempt(text: String, complete: Boolean = true) =
        AstraReasoningConsensus.Attempt(text, complete)
    private fun select(q: String, vararg a: AstraReasoningConsensus.Attempt) =
        AstraReasoningConsensus.select(q, a.toList())

    @Test fun changesOnlyWhenTwoCompleteRetriesAgreeOnCanonicalScalar() {
        assertEquals(1, select(integer, attempt("19"), attempt("20"), attempt("+020")))
        assertEquals(1, select(binary, attempt("YES"), attempt("no."), attempt("NO")))
        assertEquals(0, select(integer, attempt("20"), attempt("+020"), attempt("20")))
        assertEquals(0, select(integer, attempt("19"), attempt("20"), attempt("21")))
        assertEquals(0, select(integer, attempt("19"), attempt("20"), attempt("20", false)))
        assertEquals(0, select(integer, attempt("19"), attempt("Answer: 20"), attempt("20")))
    }

    @Test fun neverTreatsIncompleteOrFreeFormTextAsAgreement() {
        assertEquals(1, select(integer, attempt("partial", false), attempt("20"), attempt("20")))
        assertEquals(0, select(integer, attempt("19"),
            attempt(AstraInterruptedReply.budgetLimited("20")), attempt("20")))
        assertEquals(0, select(binary, attempt("NO"), attempt("Yes because..."), attempt("YES")))
        assertEquals(0, select(integer, attempt("19"), attempt("20"), attempt("20.5")))
        assertEquals(0, select(integer, attempt("19"), attempt("20")))
    }

    @Test fun onlyExplicitScalarRequestsAreEligible() {
        for (q in listOf("Explain the calculation.", "Translate: \"$integer\"",
            "EARLIER CHAT EXCERPTS: $integer", "ORIGINAL QUESTION: $integer",
            "SAME-CHAT FOLLOW-UP: $integer", "Source passage.\n$integer")) {
            assertEquals(q, 0, select(q, attempt("19"), attempt("20"), attempt("20")))
        }
    }
}
