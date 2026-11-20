package com.astra.agent


/**
 * Local-only conversation library operations. All operations return new
 * collections and leave messages unchanged. Destructive actions require UI
 * confirmation before this helper is called.
 */
internal object AstraChatLibrary {
    private val whitespace = Regex("\\s+")

    fun title(raw: String): String =
        AstraUnicodeBounds.prefix(raw.trim().replace(whitespace, " "), 100)

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

    data class SearchHit(
        val chat: AstraChatArchive.Chat,
        val snippet: String?
    )

    /**
     * Search both saved chat titles and their messages, entirely on-device.
     * Newest message matches are preferred, while the list retains archive
     * order. Only the snippet is normalized to avoid copying large histories
     * for each keystroke.
     */
    fun searchWithPreview(
        chats: List<AstraChatArchive.Chat>,
        query: String
    ): List<SearchHit> {
        val term = query.trim()
        if (term.isEmpty()) return chats.map { SearchHit(it, null) }

        return chats.mapNotNull { chat ->
            if (AstraChatSearchText.contains(chat.title, term)) {
                SearchHit(chat, null)
            } else {
                val match = chat.messages.asReversed().firstNotNullOfOrNull { message ->
                    val match = AstraChatSearchText.find(message.text, term)
                    if (match == null) null else {
                        val start = (match.start - 35).coerceAtLeast(0)
                        val end = (match.endExclusive + 65)
                            .coerceAtMost(message.text.length)
                        val excerpt = AstraUnicodeBounds.slice(message.text, start, end)
                            .replace(whitespace, " ").trim()
                        val before = if (start > 0) "…" else ""
                        val after = if (end < message.text.length) "…" else ""
                        "${message.role}: $before$excerpt$after"
                    }
                }
                if (match == null) null else SearchHit(chat, match)
            }
        }
    }

    fun search(
        chats: List<AstraChatArchive.Chat>,
        query: String
    ): List<AstraChatArchive.Chat> =
        searchWithPreview(chats, query).map { it.chat }
}
