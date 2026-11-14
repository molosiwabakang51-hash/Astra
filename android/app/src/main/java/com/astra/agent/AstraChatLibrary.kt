package com.astra.agent

import java.util.Locale

/**
 * Local-only conversation library operations. All operations return new
 * collections and leave messages unchanged. Destructive actions require UI
 * confirmation before this helper is called.
 */
internal object AstraChatLibrary {
    private val whitespace = Regex("\\s+")

    fun title(raw: String): String =
        raw.trim().replace(whitespace, " ").take(100)

    fun rename(
        chats: List<AstraChatArchive.Chat>,
        id: String,
        requestedTitle: String
    ): List<AstraChatArchive.Chat> {
        val normalized = title(requestedTitle)
        require(normalized.isNotBlank()) { "Conversation title cannot be blank." }
        return chats.map { chat ->
            if (chat.id == id) chat.copy(title = normalized) else chat
        }
    }

    fun delete(
        chats: List<AstraChatArchive.Chat>,
        id: String
    ): List<AstraChatArchive.Chat> =
        chats.filterNot { it.id == id }

    fun search(
        chats: List<AstraChatArchive.Chat>,
        query: String
    ): List<AstraChatArchive.Chat> {
        val text = query.trim().lowercase(Locale.ROOT)
        return if (text.isEmpty()) chats
        else chats.filter { it.title.lowercase(Locale.ROOT).contains(text) }
    }
}
