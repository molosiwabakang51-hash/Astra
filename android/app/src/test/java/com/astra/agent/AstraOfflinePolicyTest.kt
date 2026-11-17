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

    @Test fun oversizedOfflinePromptKeepsCurrentQuestionAtTheEnd() {
        val prefix = "Read these long hypothetical lecture notes. "
        val middle = "Unrelated explanatory example. ".repeat(200)
        val ending = "NOW ANSWER: What is modified duration, briefly?"
        val bounded = AstraOfflinePolicy.modelPrompt(prefix + middle + ending)
        assertTrue(bounded.startsWith(prefix))
        assertTrue(bounded.contains("Middle of long input omitted"))
        assertTrue(bounded.endsWith(ending + "\n/no_think"))
        assertTrue(bounded.length <= 3010)
    }

    @Test fun ordinaryQuestionAndShortSourceAreNotSilentlyRewritten() {
        for (length in listOf(1, 24, 1_800, 3_000)) {
            val message = "A".repeat(length)
            assertEquals(message, AstraOfflinePolicy.boundedInput(message))
            assertEquals(message + "\n/no_think",
                AstraOfflinePolicy.modelPrompt(message))
        }
    }

    @Test fun oversizeInputHasClearBoundaryAndNoDuplicateMiddle() {
        val text = "START-OF-NOTES " + "M".repeat(7_000) + " END-OF-QUESTION"
        val bounded = AstraOfflinePolicy.boundedInput(text)
        assertTrue(bounded.startsWith("START-OF-NOTES"))
        assertTrue(bounded.endsWith("END-OF-QUESTION"))
        assertEquals(1, bounded.split("Middle of long input omitted").size - 1)
        assertTrue(bounded.length <= 3_000)
        assertFalse(bounded.contains("M".repeat(4_000)))
    }

    @Test fun truncationDoesNotSplitUnicodeSurrogatePairs() {
        val markerLength =
            "\n[Middle of long input omitted to fit the offline model context.]\n".length
        val headLength = 3_000 - markerLength - 1_800
        val headBoundary = "a".repeat(headLength - 1) + "😀" + "b".repeat(5_000)
        val tailBoundary = "a".repeat(5_000 - 1_800 - 1) + "😀" + "b".repeat(1_800)
        for (input in listOf(headBoundary, tailBoundary)) {
            val result = AstraOfflinePolicy.boundedInput(input)
            assertTrue(result.length <= 3_000)
            for (index in result.indices) {
                if (result[index].isHighSurrogate()) {
                    assertTrue(index + 1 < result.length && result[index + 1].isLowSurrogate())
                }
                if (result[index].isLowSurrogate()) {
                    assertTrue(index > 0 && result[index - 1].isHighSurrogate())
                }
            }
        }
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
