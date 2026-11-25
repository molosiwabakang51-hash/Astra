package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraInterruptedReplyTest {
    @Test fun detectsAllGeneratedInterruptionMarkers() {
        for (reply in listOf(
            "ASTRA // THINKING OFFLINE...",
            "ASTRA // THINKING...",
            "The response was interrupted when Astra closed.",
            "Offline response interrupted.",
            "Offline response stopped.",
            AstraInterruptedReply.EMPTY_MODEL_REPLY,
            "Offline model unavailable. Android may not have enough available RAM. Retry from Chat.",
            "Partial finance lecture.\n\n[Response stopped]",
            "Partial Russian lesson.\n\n[Response interrupted]",
            "Partial answer.\n\n[Response stopped]\n\nReferences retained."
        )) {
            assertTrue(reply, AstraInterruptedReply.isIncomplete(reply))
        }
    }

    @Test fun completeAnswersAreStillEligibleForNativeContext() {
        for (reply in listOf(
            "Bonds pay coupons and mature on a stated date.",
            "Привет. Что вы хотите узнать?",
            "A response to a normal question about interrupted processes.",
            "For documentation: [Response stopped] is a status marker.",
            "The term interruption appears in finance research.",
            "Code example: ASTRA // THINKING is a placeholder.",
            ""
        )) {
            assertFalse(reply, AstraInterruptedReply.isIncomplete(reply))
        }
    }

    @Test fun unfinishedRepliesAreNotMisreadAsEntireConversation() {
        val history = listOf(
            AstraChatArchive.Message("You", "Teach me finance"),
            AstraChatArchive.Message("Astra", "Here is a partial lesson.\n\n[Response stopped]"),
            AstraChatArchive.Message("You", "What is duration?"),
            AstraChatArchive.Message("Astra", "Duration is interest-rate sensitivity.")
        )
        val eligible = history.filterNot {
            it.role == "Astra" && AstraInterruptedReply.isIncomplete(it.text)
        }
        assertTrue(eligible.any {
            it.role == "You" && it.text == "Teach me finance"
        })
        assertFalse(eligible.any { it.text.contains("partial lesson") })
        assertTrue(eligible.any { it.text.contains("interest-rate sensitivity") })
    }
}
