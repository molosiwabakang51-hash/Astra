package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraTrayPolicyTest {
    @Test fun everyExistingPrimaryDestinationRemainsAccessible() {
        assertEquals(
            listOf("Chat", "Tasks", "Notes", "Tools", "Settings", "Study"),
            AstraTrayPolicy.destinations.map { it.first }
        )
        assertEquals(AstraNavSection.entries.size, AstraTrayPolicy.destinations.size)
        assertTrue(AstraTrayPolicy.destinations.all { it.second.isNotBlank() })
    }

    @Test fun everyPreviousChatActionRemainsAvailableAndNoActionIsDuplicated() {
        val actions = AstraTrayPolicy.conversationActions
        assertEquals(AstraTrayAction.entries.toSet(), actions.toSet())
        assertEquals(actions.size, actions.toSet().size)
        assertEquals(AstraTrayAction.NEW_CONVERSATION, actions.first())
        assertTrue(actions.indexOf(AstraTrayAction.CONVERSATIONS) <
            actions.indexOf(AstraTrayAction.CLEAR_CHAT))
        assertTrue(actions.indexOf(AstraTrayAction.FIND_IN_CHAT) <
            actions.indexOf(AstraTrayAction.CLEAR_CHAT))
    }

    @Test fun onlyConfirmedChatDeletionIsDestructive() {
        for (action in AstraTrayAction.entries) {
            assertEquals(
                action == AstraTrayAction.CLEAR_CHAT,
                AstraTrayPolicy.isDestructive(action)
            )
            assertFalse(action.label.isBlank())
        }
    }
}
