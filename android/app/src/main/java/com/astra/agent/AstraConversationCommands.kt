package com.astra.agent

/**
 * Intent classification for conversation navigation.
 *
 * Keeping "new conversation" separate from "clear chat" is essential:
 * starting a new conversation must archive the current transcript, never
 * delete it. The processor handles these intents before local dialogue and
 * cloud AI fallback.
 */
internal object AstraConversationCommands {
    enum class Action {
        NEW_CONVERSATION,
        OPEN_CONVERSATIONS,
        CLEAR_CURRENT
    }

    fun classify(raw: String): Action? {
        val command = raw.trim().trimEnd('?', '!', '.')
            .replace(Regex("\\s+"), " ")
            .lowercase(java.util.Locale.ROOT)
        return when (command) {
            "new conversation",
            "new chat",
            "start new chat",
            "start a new chat",
            "start new conversation",
            "start a new conversation",
            "create new conversation",
            "create a new conversation" -> Action.NEW_CONVERSATION

            "conversations",
            "show conversations",
            "open conversations",
            "switch chat",
            "switch chats",
            "switch conversation",
            "switch conversations",
            "chat history",
            "chat list",
            "previous chats" -> Action.OPEN_CONVERSATIONS

            "clear chat",
            "clear history",
            "clear conversation",
            "delete current conversation" -> Action.CLEAR_CURRENT

            else -> null
        }
    }
}
