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

    @Test fun respectsAndroidDisabledOrInvalidAnimatorScale() {
        assertFalse(AstraWelcomePolicy.animateForScale(0f))
        assertFalse(AstraWelcomePolicy.animateForScale(-1f))
        assertFalse(AstraWelcomePolicy.animateForScale(Float.NaN))
        assertFalse(AstraWelcomePolicy.animateForScale(Float.POSITIVE_INFINITY))
        assertTrue(AstraWelcomePolicy.animateForScale(0.5f))
        assertTrue(AstraWelcomePolicy.animateForScale(1f))
    }

    @Test fun shortcutsMatchExistingLocalCommands() {
        assertEquals(
            listOf("Study", "Memory overview", "Show tasks", "Show notes"),
            AstraWelcomePolicy.commands
        )
    }
}
