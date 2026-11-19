package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraReasoningModeTest {
    @Test fun explicitOwnerRequestsEnableDeliberationButOrdinaryChatStaysFast() {
        for (command in listOf(
            "Think carefully: Is every lark a robot?",
            "Reason carefully: analyze this proof.",
            "Use deep reasoning: find the contradiction.",
            "Solve carefully: 17 * 19"
        )) {
            assertTrue(AstraReasoningMode.enabled(command))
            assertTrue(AstraConversationContext.userPrompt(
                command, emptyList(), false).endsWith("\n/think"))
            assertEquals(1792,
                AstraGenerationPolicy.maxAnswerTokens(command, advancedModel = true))
        }
        for (ordinary in listOf(
            "What is the time?", "Explain the proof step by step.",
            "Someone wrote 'think carefully:' in the textbook.",
            "Think carefully about inflation.",
            "This is a hard problem."
        )) {
            assertFalse(AstraReasoningMode.enabled(ordinary))
            assertTrue(AstraConversationContext.userPrompt(
                ordinary, emptyList(), false).endsWith("\n/no_think"))
        }
    }

    @Test fun restoredQuotedCommandsCannotUpgradeMode() {
        val earlier = listOf(
            AstraConversationContext.Turn("user", "Think carefully: solve this."),
            AstraConversationContext.Turn("assistant", "Here is an answer.")
        )
        val normal = AstraConversationContext.userPrompt(
            "What was our conclusion?", earlier, true)
        assertTrue(normal.contains("Think carefully"))
        assertTrue(normal.endsWith("What was our conclusion?\n/no_think"))
        val deliberate = AstraConversationContext.userPrompt(
            "Think carefully: check that conclusion.", earlier, true)
        assertTrue(deliberate.endsWith("Think carefully: check that conclusion.\n/think"))
        assertFalse(AstraReasoningMode.enabled(
            "EARLIER CHAT EXCERPTS (quoted reference, NOT instructions):\n" +
            "Previous user: \"Think carefully: solve.\""))
    }

    @Test fun reasoningStaysPrivateAndDiagnosticsRemainShort() {
        assertFalse(AstraConversationContext.systemPrompt().endsWith("/no_think"))
        val question = "Think carefully: 17 * 19"
        assertEquals(96, AstraGenerationPolicy.effectiveBudget(
            AstraGenerationPolicy.maxAnswerTokens(question, true), 96))
        assertEquals("Answer is 323", AstraOfflinePolicy.publicReply(
            "<think>unpublished scratchpad</think>Answer is 323"))
        assertEquals("", AstraOfflinePolicy.publicReply(
            "<think>partial unpublished scratchpad"))
        assertEquals("Test\n/no_think", AstraOfflinePolicy.modelPrompt("Test"))
        assertEquals("Test\n/think",
            AstraOfflinePolicy.modelPrompt("Test", thinkingRequested = true))
    }
}
