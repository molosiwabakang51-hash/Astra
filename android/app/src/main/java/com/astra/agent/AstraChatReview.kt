package com.astra.agent

import java.util.Locale

/**
 * Explicit, current-chat audit. Unlike private memory recall, this is allowed
 * to quote Astra's *own* earlier answers so the owner can challenge mistakes.
 * Quoted assistant messages are NEVER promoted to verified facts or saved as
 * user memories. Nothing leaves the device or calls the language model.
 */
internal object AstraChatReview {
    private val explicit = Regex(
        """^(?:recall|review|find|show)\s+(?:the\s+)?(?:earlier|previous)\s+""" +
            """(?:mentions|messages|replies|responses|discussion|references)\s+""" +
            """(?:of|about|on)\s+([\p{L}\p{N}'-]+)""",
        RegexOption.IGNORE_CASE
    )
    private val question = Regex(
        """^what did (?:you|we|astra) (?:say|discuss|claim|mention) about\s+([\p{L}\p{N}'-]+)""",
        RegexOption.IGNORE_CASE
    )
    private val continuation = setOf(
        "in this chat", "earlier than this", "earlier in this chat",
        "do you see your mistake", "do you see the mistake", "where was your mistake",
        "look further back", "look earlier in this chat"
    )
    private val words = Regex("""[\p{L}\p{N}]+""")

    fun respond(raw: String, currentChat: List<AstraChatArchive.Message>): String? {
        if (raw.length > 300) return null
        val normalized = raw.trim().lowercase(Locale.ROOT).trimEnd('?', '.', '!', ' ')
        val requested = explicit.find(normalized)?.groupValues?.get(1)
            ?: question.find(normalized)?.groupValues?.get(1)
        val continuing = normalized in continuation
        if (requested == null && !continuing) return null

        // An ambiguous "earlier than this" follows an explicit chat-review
        // request. Never search unrelated notes or archived conversations.
        val earlierRequests = currentChat.asReversed()
            .filter { it.role == "You" }
            .take(16)
        val lastReviewed = earlierRequests.firstNotNullOfOrNull {
            explicit.find(it.text.lowercase(Locale.ROOT))?.groupValues?.get(1)
                ?: question.find(it.text.lowercase(Locale.ROOT))?.groupValues?.get(1)
        }
        val subject = requested ?: lastReviewed ?:
            if (earlierRequests.take(6).any {
                    Regex("""\bshiller\b""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(it.text)
                }) "shiller" else null
        if (subject == null) {
            return "I can review my earlier replies in this chat, but I need a topic. " +
                "For example: 'review earlier mentions of bonds'."
        }
        val term = subject.lowercase(Locale.ROOT)
        val matches = currentChat.withIndex()
            .filter { (i, turn) ->
                i < currentChat.size &&
                    (turn.role == "Astra" || turn.role == "You") &&
                    words.findAll(turn.text.lowercase(Locale.ROOT))
                        .any { it.value == term }
            }
        if (matches.isEmpty()) return "I found no earlier references to '$subject' in this chat."

        val incorrectShiller = term == "shiller" && matches.any {
            it.value.role == "Astra" &&
                it.value.text.contains("Niall Shiller", ignoreCase = true)
        }
        val quoted = (matches.take(3) + matches.takeLast(3))
            .distinctBy { it.index }
            .sortedBy { it.index }
            .take(6)
        return buildString {
            appendLine("EARLIER MENTIONS IN THIS CHAT · $subject")
            if (incorrectShiller) {
                appendLine("Correction: I was wrong to identify 'Niall Shiller' as the economist " +
                    "behind Irrational Exuberance. Its author is Robert J. Shiller, the " +
                    "Yale economist (2000). I also gave a false University of Chicago " +
                    "affiliation and should not have defended those claims.")
                appendLine("Reference: Yale School of Management's Robert J. Shiller faculty profile.")
                appendLine()
            }
            appendLine("These are quotes, not automatically verified claims:")
            quoted.forEach { (index, turn) ->
                val label = if (turn.role == "You") "You" else "Earlier Astra reply"
                appendLine("${index + 1}. $label: ${excerpt(turn.text, term)}")
            }
            if (!incorrectShiller && continuing) {
                appendLine("A quotation alone does not prove whether a factual claim is correct.")
            }
        }.trim().take(2_800)
    }

    private fun excerpt(original: String, term: String): String {
        val flat = original.replace(Regex("""\s+"""), " ").trim()
        val at = flat.indexOf(term, ignoreCase = true).coerceAtLeast(0)
        val start = (at - 60).coerceAtLeast(0)
        val end = (start + 230).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) +
            (if (end < flat.length) "…" else "")
    }
}
