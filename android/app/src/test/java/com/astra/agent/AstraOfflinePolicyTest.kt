package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraOfflinePolicyTest {
    private val unsupported =
        "I cannot answer that reliably with Astra's current offline tools. Try asking another way."

    @Test fun onlyUnsupportedNonSensitiveRequestsGoToOfflineModel() {
        assertTrue(AstraOfflinePolicy.shouldUseModel(unsupported, false, false))
        assertFalse(AstraOfflinePolicy.shouldUseModel(unsupported, true, false))
        assertFalse(AstraOfflinePolicy.shouldUseModel(unsupported, false, true))
        assertFalse(AstraOfflinePolicy.shouldUseModel("Note saved.", false, false))
        assertFalse(AstraOfflinePolicy.shouldUseModel("Task added.", false, false))
        assertFalse(AstraOfflinePolicy.shouldUseModel("", false, false))
    }

    @Test fun modelPromptsUseQwenNonThinkingSwitchWithoutChangingOriginalInput() {
        val input = "What is modified duration?"
        assertEquals("What is modified duration?\n/no_think", AstraOfflinePolicy.modelPrompt(input))
        assertEquals("What is modified duration?", input)
        assertTrue(AstraOfflinePolicy.modelPrompt("a".repeat(10_000)).length <= 3010)
    }

    @Test fun interruptedThinkingMessageIsRecoveredAfterRestart() {
        assertEquals(
            "The response was interrupted when Astra closed.",
            AstraOfflinePolicy.recoverInterruptedMessage(
                "Astra", "ASTRA // THINKING OFFLINE..."
            )
        )
        assertEquals(
            "The response was interrupted when Astra closed.",
            AstraOfflinePolicy.recoverInterruptedMessage(
                "Astra", "ASTRA // THINKING..."
            )
        )
    }

    @Test fun completeAndUserMessagesRemainUnmodified() {
        assertEquals(
            "Completed response.",
            AstraOfflinePolicy.recoverInterruptedMessage("Astra", "Completed response.")
        )
        assertEquals(
            "ASTRA // THINKING OFFLINE...",
            AstraOfflinePolicy.recoverInterruptedMessage(
                "You", "ASTRA // THINKING OFFLINE..."
            )
        )
    }

    @Test fun reasoningNeverLeaksAfterVisibleAnswerOrBetweenParagraphs() {
        assertEquals("First. Second.", AstraOfflinePolicy.publicReply(
            "First. <think>private middle</think> Second."
        ))
        assertEquals("Useful answer", AstraOfflinePolicy.publicReply(
            "Useful answer <think>unfinished private reasoning"
        ))
        assertEquals("Answer", AstraOfflinePolicy.publicReply(
            "<THINK>first<think>nested</think>last</THINK>Answer"
        ))
        assertEquals("Final", AstraOfflinePolicy.publicReply(
            "private model scratchpad</think>Final"
        ))
    }

    @Test fun partialReasoningTagNeverAppearsDuringStreaming() {
        assertEquals("Answer", AstraOfflinePolicy.publicReply("Answer <thi"))
        assertEquals("Answer", AstraOfflinePolicy.publicReply("Answer </thin"))
        assertEquals("Answer", AstraOfflinePolicy.publicReply(
            "Answer <think>private"
        ))
        assertEquals("Math is 2 < 3.", AstraOfflinePolicy.publicReply(
            "Math is 2 < 3."
        ))
        assertEquals("Text", AstraOfflinePolicy.publicReply("Text<"))
    }

    @Test fun noReasoningOrIncompleteThoughtsAreDisplayed() {
        assertEquals("Short answer.", AstraOfflinePolicy.publicReply(
            "<think>private scratchpad</think>\n\nShort answer."
        ))
        assertEquals("", AstraOfflinePolicy.publicReply("<think>unfinished internal note"))
        assertEquals("Normal response", AstraOfflinePolicy.publicReply(" Normal response "))
        assertEquals("Answer.", AstraOfflinePolicy.publicReply(
            "<think>first private segment</think>  <think>second private segment</think> Answer."
        ))
        assertEquals("", AstraOfflinePolicy.publicReply(
            "<think>finished</think><think>unfinished private segment"
        ))
        assertEquals("Answer.", AstraOfflinePolicy.publicReply(
            "<think>private</think>Answer."
        ))
    }
}
