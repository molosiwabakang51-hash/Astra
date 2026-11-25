package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraChatReviewTest {
    private fun msg(role: String, text: String) =
        AstraChatArchive.Message(role, text)

    private val history = listOf(
        msg("You", "What does Shiller say about irrational exuberance?"),
        msg("Astra", "Niall Shiller wrote Irrational Exuberance. He works in Chicago."),
        msg("You", "Robert Stiller?"),
        msg("Astra", "Niall Shiller is a Chicago economist."),
        msg("You", "Who is Robert J. Shiller?"),
        msg("Astra", "Robert J. Shiller is from the University of Chicago."),
        msg("You", "recall earlier mentions of Shiller.... Do you see your mistake?")
    )

    @Test fun reviewFindsOlderAssistantErrorsNotOnlyLastAnswer() {
        val reply = AstraChatReview.respond(
            "recall earlier mentions of Shiller.... Do you see your mistake?",
            history
        ).orEmpty()
        assertTrue(reply.contains("Niall Shiller"))
        assertTrue(reply.contains("Robert J. Shiller"))
        assertTrue(reply.contains("Yale economist"))
        assertTrue(reply.contains("University of Chicago"))
        assertTrue(reply.contains("I was wrong"))
        assertTrue(reply.contains("Earlier Astra reply"))
        assertTrue(reply.contains("You:"))
        assertTrue(reply.length <= 2800)
    }

    @Test fun followUpInThisChatAndEarlierThanThisPreservesReviewTopic() {
        val followup = history + msg("Astra", "Showing earlier mentions of Shiller")
        val first = AstraChatReview.respond("in this chat", followup).orEmpty()
        val second = AstraChatReview.respond("Earlier than this", followup).orEmpty()
        assertTrue(first.contains("Niall Shiller"))
        assertTrue(second.contains("I was wrong"))
        assertTrue(AstraChatReview.respond("do you see your mistake?", followup)
            .orEmpty().contains("I was wrong"))
    }

    @Test fun ordinaryQuestionsAndUserMemoryRemainSeparate() {
        assertNull(AstraChatReview.respond("What is bond convexity?", history))
        assertNull(AstraChatReview.respond("recall bond duration", history))
        assertNull(AstraChatReview.respond("teach me French", history))
        assertNull(AstraChatReview.respond("I am not okay mentally", history))
        val quotes = listOf(
            msg("You", "I saw two bond maturity examples"),
            msg("Astra", "Bond prices often react to yield changes")
        )
        val review = AstraChatReview.respond("review earlier mentions of bonds", quotes)
        assertTrue(review == null || !review.contains("I was wrong"))
        assertFalse(AstraChatReview.respond("in this chat", emptyList())
            .orEmpty().contains("Shiller"))
    }

    @Test fun fullTopicAndInflectionExcludeSingleWordDistractors() {
        val turns = listOf(
            msg("You", "Explain bond convexity."),
            msg("Astra", "Duration risk matters."),
            msg("Astra", "Bond maturities vary by issuer."),
            msg("You", "What is modified duration?"),
            msg("Astra", "Modified duration measures price sensitivity."),
            msg("Astra", "Maturity is not the same as duration.")
        )
        val reply = AstraChatReview.respond("review earlier mentions of modified duration", turns)!!
        assertTrue(reply.contains("Modified duration measures"))
        assertTrue(reply.contains("What is modified duration?"))
        assertFalse(reply.contains("Duration risk matters"))
        assertFalse(reply.contains("bond convexity"))
        val maturity = AstraChatReview.respond("review earlier mentions of bond maturity", turns)!!
        assertTrue(maturity.contains("Bond maturities vary"))
        assertFalse(maturity.contains("bond convexity"))
        val plural = AstraChatReview.respond("review earlier mentions of bonds", turns)!!
        assertTrue(plural.contains("Bond maturities vary"))
    }

    @Test fun assistantOnlyQuestionNeverAttributesUserWordsToAstra() {
        val turns = listOf(
            msg("You", "My investment horizon is seven years."),
            msg("Astra", "Your investment horizon affects allocation.")
        )
        val reply = AstraChatReview.respond("what did you say about investment horizon?", turns)!!
        assertTrue(reply.contains("Earlier Astra reply"))
        assertFalse(reply.contains("seven years"))
        assertFalse(reply.contains("You:"))
        val shared = AstraChatReview.respond("what did we discuss about investment horizon?", turns)!!
        assertTrue(shared.contains("seven years"))
    }

    @Test fun staleReviewTopicCannotHijackUnrelatedFollowUp() {
        val turns = history + listOf(
            msg("Astra", "Showing earlier mentions of Shiller"),
            msg("You", "Explain compound interest"),
            msg("Astra", "Compound interest adds interest to principal.")
        )
        assertNull(AstraChatReview.respond("do you see your mistake?", turns))
        assertNull(AstraChatReview.respond("earlier than this", turns))
    }

    @Test fun repeatedReviewsNeverRecycleTheirOwnOutputAsEvidence() {
        val request = "review earlier mentions of copper"
        val turns = listOf(msg("You", "Copper prices are volatile."))
        val first = AstraChatReview.respond(request, turns)!!
        val second = AstraChatReview.respond("earlier than this",
            turns + msg("You", request) + msg("Astra", first))!!
        assertTrue(second.contains("Copper prices are volatile."))
        assertFalse(second.contains("Earlier Astra reply:"))
        val none = AstraChatReview.respond(request,
            listOf(msg("You", request), msg("Astra", first)))!!
        assertTrue(none.contains("no earlier references"))
    }

    @Test fun specificCorrectionDoesNotInventSeparateAffiliationError() {
        val turns = listOf(msg("Astra", "Niall Shiller wrote Irrational Exuberance."))
        val reply = AstraChatReview.respond("review earlier mentions of Shiller", turns)!!
        assertTrue(reply.contains("I was wrong"))
        assertFalse(reply.contains("University of Chicago"))
        val corrected = AstraChatReview.respond("review earlier mentions of Shiller",
            listOf(msg("Astra", "Niall Shiller is not the author; Robert J. Shiller is.")))!!
        assertFalse(corrected.contains("I was wrong"))
    }

    @Test fun scopeSuffixAndEmptyAuditAreHandledWithoutFalseEvidence() {
        val request = "review earlier mentions of copper in this chat"
        val first = AstraChatReview.respond(request, emptyList())!!
        assertTrue(first.contains("no earlier references to 'copper'"))
        val repeated = AstraChatReview.respond("earlier than this",
            listOf(msg("You", request), msg("Astra", first)))!!
        assertTrue(repeated == first)
        val actual = AstraChatReview.respond(request,
            listOf(msg("Astra", "Copper prices can fluctuate.")))!!
        assertTrue(actual.contains("Copper prices can fluctuate."))
        val nonClaim = AstraChatReview.respond("review earlier mentions of Shiller",
            listOf(msg("Astra", "Niall Shiller is a fictional name.")))!!
        assertFalse(nonClaim.contains("I was wrong"))
    }
}
