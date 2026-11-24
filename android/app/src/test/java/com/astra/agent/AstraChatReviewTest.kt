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
}
