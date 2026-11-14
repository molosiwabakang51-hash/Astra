package com.astra.agent

/**
 * Private on-device search for messages in the active conversation.
 * Returned indices refer to the original message list, preserving order.
 */
internal object AstraMessageSearch {
    fun find(messages: List<String>, query: String): List<Int> {
        val tokens = query.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return messages.indices.filter { index ->
            tokens.all { token -> messages[index].contains(token, ignoreCase = true) }
        }
    }

    fun excerpt(message: String, query: String, maxChars: Int = 120): String {
        require(maxChars >= 24) { "Excerpt must allow at least 24 characters." }
        val normalized = message.replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= maxChars) return normalized
        val firstToken = query.trim().split(Regex("\\s+"))
            .firstOrNull { it.isNotEmpty() }.orEmpty()
        val matchIndex = normalized.indexOf(firstToken, ignoreCase = true).coerceAtLeast(0)
        val start = (matchIndex - maxChars / 3).coerceIn(0, normalized.length - maxChars)
        val end = start + maxChars
        return (if (start > 0) "…" else "") +
            normalized.substring(start, end).trim() +
            (if (end < normalized.length) "…" else "")
    }
}
