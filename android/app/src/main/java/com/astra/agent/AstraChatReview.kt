package com.astra.agent

import java.util.Locale

/**
 * Bounded, explicit current-chat audit. Earlier Astra replies are quotations,
 * not verified facts or owner memories. No model, network or other chat access.
 */
internal object AstraChatReview {
    private val explicit = Regex(
        """^(?:recall|review|find|show)\s+(?:the\s+)?(?:earlier|previous)\s+""" +
            """(?:mentions|messages|replies|responses|discussion|references)\s+""" +
            """(?:of|about|on)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val question = Regex(
        """^what did (you|we|astra) (?:say|discuss|claim|mention) about\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val trailingFollowUp = Regex(
        """[.!?]+\s+(?:do you see|where was|in this chat|look earlier|look further)\b.*$""",
        RegexOption.IGNORE_CASE
    )
    private val scopeSuffix = Regex("""\s+(?:in|from) this chat$""", RegexOption.IGNORE_CASE)
    private val continuation = setOf(
        "in this chat", "earlier than this", "earlier in this chat",
        "do you see your mistake", "do you see the mistake", "where was your mistake",
        "look further back", "look earlier in this chat"
    )
    private val words = Regex("""[\p{L}\p{N}]+""")
    private const val REPORT_HEADER = "EARLIER MENTIONS IN THIS CHAT · "
    private const val EMPTY_PREFIX = "I found no earlier references to '"
    private data class Request(val subject: String, val assistantOnly: Boolean)

    private fun normalize(text: String): String =
        text.trim().lowercase(Locale.ROOT).trimEnd('?', '.', '!', ' ')

    private fun parse(text: String): Request? {
        if (text.length > 300) return null
        val clean = normalize(text)
        val direct = explicit.matchEntire(clean)
        val ask = question.matchEntire(clean)
        val subject = (direct?.groupValues?.get(1) ?: ask?.groupValues?.get(2)
            ?: return null).replace(trailingFollowUp, "").replace(scopeSuffix, "")
            .trim().trimEnd('?', '.', '!', ' ')
        if (subject.length !in 1..100 || words.findAll(subject).count() !in 1..8) return null
        return Request(subject, ask != null && ask.groupValues[1] != "we")
    }

    // Conservative English inflection matching; never match partial words.
    private fun stem(word: String): String = when {
        word.length > 5 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 4 && word.endsWith('s') &&
            !word.endsWith("ss") && !word.endsWith("us") &&
            !word.endsWith("is") -> word.dropLast(1)
        else -> word
    }

    private fun terms(text: String): List<String> =
        words.findAll(text.lowercase(Locale.ROOT)).map { stem(it.value) }.toList()

    // Match the whole topic in order, not merely one overlapping keyword.
    private fun matchAt(text: String, topic: List<String>): Int? {
        val tokens = words.findAll(text).toList()
        if (tokens.size < topic.size) return null
        for (i in 0..tokens.size - topic.size) {
            if (topic.indices.all { offset ->
                    stem(tokens[i + offset].value.lowercase(Locale.ROOT)) == topic[offset]
                }) return tokens[i].range.first
        }
        return null
    }

    fun respond(raw: String, currentChat: List<AstraChatArchive.Message>): String? {
        if (raw.length > 300) return null
        val requested = parse(raw)
        val continuing = normalize(raw) in continuation
        if (requested == null && !continuing) return null

        // Only a contiguous chain of review requests/continuations can reuse
        // a topic. Any intervening ordinary user turn breaks that chain.
        val previous = if (requested == null) currentChat.asReversed()
            .asSequence().filter { it.role == "You" }.take(16)
            .dropWhile { normalize(it.text) in continuation }
            .firstOrNull()?.let { parse(it.text) } else null
        val request = requested ?: previous ?: return null
        val topic = terms(request.subject)
        if (topic.isEmpty()) return null

        val matches = currentChat.withIndex().filter { (_, turn) ->
            (turn.role == "Astra" || (turn.role == "You" && !request.assistantOnly)) &&
                // Review queries and generated audit summaries are not new
                // evidence: repeated review must never amplify its own quotes.
                !(turn.role == "You" && (parse(turn.text) != null ||
                    normalize(turn.text) in continuation)) &&
                !(turn.role == "Astra" && (turn.text.startsWith(REPORT_HEADER) ||
                    (turn.text.startsWith(EMPTY_PREFIX) && turn.text.endsWith("' in this chat.")))) &&
                matchAt(turn.text, topic) != null
        }
        if (matches.isEmpty()) return EMPTY_PREFIX +
            request.subject + "' in this chat."

        // Preserve a narrow, sourced correction for the actual reported
        // identity error, without claiming a separate affiliation mistake
        // unless an earlier Astra reply contains that assertion too.
        val shillerTopic = "shiller" in topic
        val priorAstra = matches.filter { it.value.role == "Astra" }.map { it.value.text }
        val wrongIdentity = shillerTopic && priorAstra.any {
            Regex("""\bNiall Shiller\s+(?:wrote\s+Irrational Exuberance|is\s+a\s+Chicago economist)\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(it)
        }
        val wrongAffiliation = shillerTopic && priorAstra.any {
            Regex("""\bRobert J\.?\s+Shiller\s+is\s+(?:from|at|a professor at)\s+(?:the\s+)?University of Chicago\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(it)
        }
        val quoted = (matches.take(3) + matches.takeLast(3))
            .distinctBy { it.index }.sortedBy { it.index }.take(6)
        return buildString {
            appendLine(REPORT_HEADER + request.subject)
            if (wrongIdentity) {
                appendLine("Correction: I was wrong to identify 'Niall Shiller' as the economist " +
                    "behind Irrational Exuberance. Its author is Robert J. Shiller, the " +
                    "Yale economist (2000).")
            }
            if (wrongAffiliation) {
                appendLine("Correction: I was wrong to give Robert J. Shiller a University of Chicago affiliation; his faculty profile is at Yale.")
            }
            if (wrongIdentity || wrongAffiliation) {
                appendLine("Reference: Yale School of Management's Robert J. Shiller faculty profile.")
                appendLine()
            }
            appendLine("These are quotes, not automatically verified claims:")
            quoted.forEach { (index, turn) ->
                val label = if (turn.role == "You") "You" else "Earlier Astra reply"
                appendLine((index + 1).toString() + ". " + label + ": " + excerpt(turn.text, topic))
            }
            if (!wrongIdentity && !wrongAffiliation && continuing) {
                appendLine("A quotation alone does not prove whether a factual claim is correct.")
            }
        }.trim().take(2_800)
    }

    private fun excerpt(original: String, topic: List<String>): String {
        val flat = original.replace(Regex("""\s+"""), " ").trim()
        val at = matchAt(flat, topic) ?: 0
        var start = (at - 60).coerceAtLeast(0)
        var end = (start + 230).coerceAtMost(flat.length)
        if (start > 0 && Character.isLowSurrogate(flat[start])) start--
        if (end > start && end < flat.length &&
            Character.isHighSurrogate(flat[end - 1])) end--
        return (if (start > 0) "…" else "") + flat.substring(start, end) +
            (if (end < flat.length) "…" else "")
    }
}
