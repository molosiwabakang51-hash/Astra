package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraWelcomePolicyTest {
    @Test fun welcomeIsShownForNewChatAndInitialAssistantGreeting() {
        assertTrue(AstraWelcomePolicy.show(emptyList()))
        assertTrue(AstraWelcomePolicy.show(listOf("Astra")))
        assertTrue(AstraWelcomePolicy.show(listOf("assistant")))
    }

    @Test fun welcomeDoesNotOccupySpaceAfterOwnerSpeaks() {
        assertFalse(AstraWelcomePolicy.show(listOf("Astra", "You")))
        assertFalse(AstraWelcomePolicy.show(listOf("assistant", "user", "assistant")))
    }

    @Test fun shortcutsMatchExistingLocalCommands() {
        assertEquals(
            listOf("Teach me finance", "Memory overview", "Show tasks", "Show notes"),
            AstraWelcomePolicy.commands
        )
    }
}
