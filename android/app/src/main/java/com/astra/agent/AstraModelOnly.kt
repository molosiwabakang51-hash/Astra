package com.astra.agent

/**
 * Explicit owner-requested model-only dialogue. Never parse model output.
 * Keep only the contiguous completed model-only exchange chain in this chat.
 * Ordinary local tools, private sources and older assisted turns are excluded.
 * This provides an evaluation route, not a claim of intelligence.
 */
internal object AstraModelOnly {
    private const val PREFIX = "model only:"

    fun question(raw: String): String? {
        val text = raw.trim()
        if (!text.startsWith(PREFIX, ignoreCase = true)) return null
        return text.substring(PREFIX.length).trim().takeIf { it.isNotEmpty() }
    }

    fun previousTurnCount(history: List<AstraChatArchive.Message>): Int {
        var end = history.size
        while (end >= 2) {
            val user = history[end - 2]
            val reply = history[end - 1]
            if (user.role != "You" || question(user.text) == null ||
                reply.role != "Astra" || reply.text.isBlank() ||
                AstraInterruptedReply.isIncomplete(reply.text)
            ) break
            end -= 2
        }
        return history.size - end
    }
}
