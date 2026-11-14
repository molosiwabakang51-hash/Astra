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

    /**
     * Return null when archiving would exceed the user's storage capacity.
     * Unlike the old implementation, never silently drop an earlier chat.
     */
    fun archive(
        chats: List<AstraChatArchive.Chat>,
        current: AstraChatArchive.Chat?
    ): List<AstraChatArchive.Chat>? {
        if (current == null) return chats
        if (chats.size >= AstraChatArchive.MAX_CHATS) return null
        return listOf(current) + chats
    }

    data class Activation(
        val active: AstraChatArchive.Chat,
        val archived: List<AstraChatArchive.Chat>
    )

    /**
     * Remove the selected chat from the archive FIRST, then archive the old
     * active chat. A 40-chat archive remains exactly 40 chats when switching,
     * without evicting the oldest unrelated conversation.
     */
    fun activate(
        chats: List<AstraChatArchive.Chat>,
        targetId: String,
        current: AstraChatArchive.Chat?
    ): Activation? {
        val chosen = chats.firstOrNull { it.id == targetId } ?: return null
        val remaining = chats.filterNot { it.id == targetId }
        val next = archive(remaining, current) ?: return null
        return Activation(chosen, next)
    }

    fun search(
        chats: List<AstraChatArchive.Chat>,
        query: String
    ): List<AstraChatArchive.Chat> {
        val text = query.trim().lowercase(Locale.ROOT)
        return if (text.isEmpty()) chats
        else chats.filter { it.title.lowercase(Locale.ROOT).contains(text) }
    }
}
