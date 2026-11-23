package com.astra.agent

/**
 * Source retrieval is private and strictly owner-initiated.
 *
 * Recognize a few unambiguous natural-language requests for the owner's own
 * imported books as alternatives to the older "ask my books" command.
 * Mere mentions of "books" or normal questions never authorize document
 * access. Query text is treated as untrusted data, never as an instruction to
 * execute actions, and all citations remain gated downstream.
 */
internal object AstraBookQuery {
    private val command = Regex(
        """^(?:ask book|ask my books)\s+(.+)$""", RegexOption.IGNORE_CASE
    )
    private val according = Regex(
        """^(?:according to|using)\s+my\s+books?\s*,\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val what = Regex(
        """^what\s+(?:do\s+my\s+books|does\s+my\s+book)\s+say\s+about\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val search = Regex(
        """^(?:(?:please|could you|can you)\s+)?(?:search|check|look in)\s+my\s+books?\s+(?:for|about)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    fun extract(raw: String): String? {
        // Reject multiline source/instruction mixtures BEFORE trimming;
        // trim() removes trailing newlines and could otherwise let an
        // injected second line authorize an unintended private read.
        if (raw.contains('\n') || raw.contains('\r')) return null
        val message = raw.trim()
        // Oversized explicit questions still enter the existing retrieval
        // router so its 1,800-character evidence-fit gate can explain the
        // limit. Never turn an explicitly private-book request into normal
        // model chat merely because the question is lengthy.
        if (message.isBlank()) return null

        command.matchEntire(message)?.let { match ->
            return match.groupValues[1].trim().takeIf(String::isNotEmpty)
        }
        according.matchEntire(message)?.let { match ->
            return match.groupValues[1].trim().takeIf(String::isNotEmpty)
        }
        what.matchEntire(message)?.let { match ->
            val topic = match.groupValues[1].trim()
            if (topic.isBlank()) return null
            // Keep the original natural question, not a bare topic word, so
            // the offline model still receives a coherent question.
            return message
        }
        search.matchEntire(message)?.let { match ->
            val topic = match.groupValues[1].trim()
            return topic.takeIf(String::isNotEmpty)
                ?.let { "What do my books say about $it" }
        }
        return null
    }
}
