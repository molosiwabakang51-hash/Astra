package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AstraConversationCommandsTest {
    @Test fun newConversationNeverMapsToDestructiveClear() {
        val inputs = listOf(
            "new conversation", "new chat", "start new chat",
            "start a new chat", "start new conversation",
            "start a new conversation", "create new conversation",
            "create a new conversation", "NEW CONVERSATION!",
            "   Start   a   New   Chat?   "
        )
        for (command in inputs) {
            assertEquals(
                "must archive rather than delete: $command",
                AstraConversationCommands.Action.NEW_CONVERSATION,
                AstraConversationCommands.classify(command)
            )
        }
    }

    @Test fun switchingConversationsIsDistinctFromClearing() {
        val inputs = listOf(
            "conversations", "show conversations", "open conversations",
            "switch chat", "switch chats", "switch conversation",
            "switch conversations", "chat history", "chat list",
            "previous chats", "Open Conversations?"
        )
        for (command in inputs) {
            assertEquals(
                command,
                AstraConversationCommands.Action.OPEN_CONVERSATIONS,
                AstraConversationCommands.classify(command)
            )
        }
    }

    @Test fun deletionRemainsExplicitAndConfirmationGatedByUi() {
        val inputs = listOf(
            "clear chat", "clear history", "clear conversation",
            "delete current conversation", "CLEAR CHAT!"
        )
        for (command in inputs) {
            assertEquals(
                command,
                AstraConversationCommands.Action.CLEAR_CURRENT,
                AstraConversationCommands.classify(command)
            )
        }
    }

    @Test fun ordinaryRequestsAndModelPromptsAreNotIntercepted() {
        for (command in listOf(
            "new task", "new conversation about bitcoin",
            "why should I clear chat?", "delete my notes",
            "show tasks", "remember to switch chats tomorrow",
            "ask ai how do I start a conversation",
            "switch to dark mode", ""
        )) {
            assertNull(command, AstraConversationCommands.classify(command))
        }
    }

    @Test fun classificationIsLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(
                AstraConversationCommands.Action.OPEN_CONVERSATIONS,
                AstraConversationCommands.classify("SWITCH CHATS")
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
