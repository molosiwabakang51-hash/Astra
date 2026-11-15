package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraConversationHistoryTest {
    private fun turn(role: String, content: String) =
        AstraConversationContext.Turn(role, content)

    @Test fun recallCanFindAnOwnerFactBeyondPrevious160TurnCutoff() {
        val oldFact = "For our fictional government bond, maturity is eight years."
        val history = listOf(turn("user", oldFact)) +
            (1..220).flatMap { number ->
                listOf(turn("user", "Unrelated lunch discussion $number."),
                    turn("assistant", "Lunch response $number."))
            }
        val selected = AstraConversationHistory.forRecovery(history)
        assertEquals(history.size, selected.size)
        assertEquals(oldFact, selected.first().content)
        val prompt = AstraConversationContext.userPrompt(
            "What was my earlier government bond maturity?", selected, true
        )
        assertTrue(prompt.contains("maturity is eight years"))
        assertTrue(prompt.endsWith("What was my earlier government bond maturity?\n/no_think"))
        assertTrue(prompt.length <= 3_010)
    }

    @Test fun recoveryStaysBoundedAndNeverReadsBeyondRetainedChatWindow() {
        val history = (0..2_400).map { turn("user", "Old message $it.") }
        val selected = AstraConversationHistory.forRecovery(history)
        assertEquals(AstraConversationHistory.MAX_RETAINED_TURNS, selected.size)
        assertEquals("Old message 401.", selected.first().content)
        assertEquals("Old message 2400.", selected.last().content)
        assertFalse(selected.any { it.content == "Old message 0." })
    }

    @Test fun emptyAndShortChatsDoNotChange() {
        assertTrue(AstraConversationHistory.forRecovery(emptyList<String>()).isEmpty())
        val recent = listOf("hello", "world")
        assertEquals(recent, AstraConversationHistory.forRecovery(recent))
    }
}
