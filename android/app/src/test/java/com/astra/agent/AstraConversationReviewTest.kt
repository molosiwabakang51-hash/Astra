package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraConversationReviewTest {
    private fun msg(role: String, text: String) =
        AstraChatArchive.Message(role, text)

    private val chat = listOf(
        msg("You", "what does Shiller say about irrational exuberance"),
        msg("Astra", "Niall Shiller wrote Irrational Exuberance. Professor at Chicago."),
        msg("You", "Robert Stiller?"),
        msg("Astra", "You likely meant Niall Shiller."),
        msg("You", "who is Robert J Shiller?"),
        msg("Astra", "Robert J. Shiller is an economist at Chicago."),
        msg("You", "recall earlier mentions of Shiller")
    )

    @Test fun correctionOwnsEarlierMistakeRatherThanDoublingDown() {
        val answer = AstraConversationReview.respond("do you see your mistake?", chat)!!
        assertTrue(answer.contains("wrongly"))
        assertTrue(answer.contains("Robert J. Shiller"))
        assertTrue(answer.contains("Yale"))
        assertTrue(answer.contains("message #2"))
        assertFalse(answer.contains("refusal"))
    }

    @Test fun unrelatedNewTopicDoesNotReviveStaleShillerError() {
        val current = chat + listOf(
            msg("You", "How do I calculate bond convexity?"),
            msg("Astra", "Bond convexity depends on the timing of cash flows."),
            msg("You", "I think that calculation may be incorrect")
        )
        val answer = AstraConversationReview.respond(
            "Do you see your mistake?", current
        )!!
        assertTrue(answer.contains("cannot identify"))
        assertTrue(answer.contains("Bond convexity"))
        assertFalse(answer.contains("Niall Shiller"))
        assertFalse(answer.contains("Chicago"))
    }

    @Test fun mentioningIncorrectNameAsAWarningDoesNotCreateAnAdmittedError() {
        val current = listOf(
            msg("You", "Who wrote Irrational Exuberance?"),
            msg("Astra", "Robert J. Shiller wrote it, not Niall Shiller."),
            msg("You", "Review earlier mentions of Shiller")
        )
        val answer = AstraConversationReview.respond(
            "Do you see your mistake?", current
        )!!
        assertTrue(answer.contains("cannot identify"))
        assertFalse(answer.contains("I wrongly attributed"))
    }

    @Test fun doesNotInventChicagoOrRepeatedMistakesWhenOnlyAuthorshipWasWrong() {
        val current = listOf(
            msg("You", "Who wrote Irrational Exuberance?"),
            msg("Astra", "Niall Shiller wrote Irrational Exuberance."),
            msg("You", "Review earlier mentions of Shiller")
        )
        val answer = AstraConversationReview.respond(
            "Do you see your mistake?", current
        )!!
        assertTrue(answer.contains("wrongly attributed"))
        assertTrue(answer.contains("message #2"))
        assertTrue(answer.contains("Yale"))
        assertFalse(answer.contains("Chicago"))
        assertFalse(answer.contains("repeated"))
        assertFalse(answer.contains("refusal"))
    }

    @Test fun negatedChicagoAffiliationDoesNotBecomeAnotherConfessedError() {
        val current = listOf(
            msg("Astra", "Niall Shiller wrote Irrational Exuberance."),
            msg("Astra", "Robert J. Shiller is associated with Yale, not Chicago."),
            msg("You", "review earlier mentions of Shiller")
        )
        val correction = AstraConversationReview.respond(
            "Do you see your mistake?", current
        )!!
        assertTrue(correction.contains("wrongly attributed"))
        assertFalse(correction.contains("Chicago"))
    }

    @Test fun priorChatReviewOutputIsNotOriginalConversationEvidence() {
        val current = listOf(
            msg("Astra", "Bond investment returns depend on yield changes."),
            msg("You", "Review earlier mentions of bond"),
            msg("Astra", "ASTRA // CHAT REVIEW · bond\\nQuoted past remarks."),
            msg("You", "Do you see your mistake?")
        )
        val answer = AstraConversationReview.respond(
            "Do you see your mistake?", current
        )!!
        assertTrue(answer.contains("cannot identify"))
        assertTrue(answer.contains("Bond investment returns"))
        assertFalse(answer.contains("Quoted past remarks"))
    }

    @Test fun reviewMatchesWholeTopicWordsNotUnrelatedSubstrings() {
        val current = listOf(
            msg("Astra", "USDC is a stablecoin."),
            msg("You", "I need to understand bonds."),
            msg("Astra", "We discussed bondage as a concept."),
            msg("Astra", "A bond is a debt security.")
        )
        val usd = AstraConversationReview.respond(
            "Review earlier mentions of USD", current
        )!!
        assertTrue(usd.contains("No earlier mentions"))
        val bond = AstraConversationReview.respond(
            "Review earlier mentions of bond", current
        )!!
        assertTrue(bond.contains("A bond is a debt security"))
        assertFalse(bond.contains("bondage as a concept"))
    }

    @Test fun earlierMentionReviewQuotesBothRolesButLabelsModelClaims() {
        val response = AstraConversationReview.respond(
            "recall earlier mentions of Shiller", chat
        )!!
        assertTrue(response.contains("ASTRA // CHAT REVIEW"))
        assertTrue(response.contains("Astra:"))
        assertTrue(response.contains("You:"))
        assertTrue(response.contains("unverified"))
        assertTrue(response.contains("Niall Shiller"))
        assertFalse(response.contains("No matching information"))
    }

    @Test fun compoundReviewAndTwoShortFollowUpsKeepTheirOriginalReferent() {
        val combined = AstraConversationReview.respond(
            "recall earlier mentions of Shiller.... Do you see your mistake?", chat
        )!!
        assertTrue(combined.contains("Niall Shiller"))
        assertTrue(combined.contains("wrongly"))
        assertTrue(AstraConversationReview.respond("in this chat", chat)!!
            .contains("ASTRA // CHAT REVIEW"))
        assertTrue(AstraConversationReview.respond("Earlier than this", chat)!!
            .contains("Niall Shiller"))
    }

    @Test fun correctingNamesDoesNotFabricateAnAlternativeEconomist() {
        val identity = AstraConversationReview.respond("Robert Stiller?", chat)!!
        assertTrue(identity.contains("Robert J. Shiller"))
        assertTrue(identity.contains("Yale"))
        assertFalse(identity.contains("Niall Shiller"))
        val author = AstraConversationReview.respond(
            "Niall Shiller is not the author of Irrational Exuberance, yes?", chat
        )!!
        assertTrue(author.contains("Robert J. Shiller"))
        assertTrue(AstraConversationReview.respond(
            "what does Shiller say about irrational exuberance?", chat
        )!!.contains("feedback loops"))
    }

    @Test fun noInventedPersonalMemoriesOrInterceptingUnrelatedChats() {
        assertNull(AstraConversationReview.respond("What is bond duration?", chat))
        assertNull(AstraConversationReview.respond("Next language lesson", chat))
        assertNull(AstraConversationReview.respond("Add task study Shiller", chat))
        val noError = AstraConversationReview.respond(
            "do you see your mistake", listOf(
                msg("Astra", "The result is uncertain.")
            )
        )!!
        assertTrue(noError.contains("cannot identify"))
        assertFalse(noError.contains("Niall Shiller"))
    }
}
