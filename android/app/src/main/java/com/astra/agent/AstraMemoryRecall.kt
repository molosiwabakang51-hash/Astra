package com.astra.agent

import java.util.Locale

/**
 * Private, deterministic, on-device retrieval of what the user actually wrote.
 *
 * No embeddings, network calls, model prompts, new permissions, databases or
 * hidden inference. Only an explicit "recall" or "search memory" request
 * initiates a search. Prior assistant answers are not treated as user memories.
 */
internal object AstraMemoryRecall {
    sealed class Request {
        data object Overview : Request()
        data class Search(val term: String, val notesOnly: Boolean = false) : Request()
    }

    data class Match(val source: String, val excerpt: String, val score: Int, val order: Int)

    private val requestPattern = Regex(
        """^(recall|search (?:all )?memory(?: for)?|find (?:my )?notes about|""" +
            """what did i say about|what do you remember about|""" +
            """find (?:in )?(?:my )?conversations about)\s+(.+?)\s*[.!?]*$""",
        RegexOption.IGNORE_CASE
    )
    private val wordPattern = Regex("""[\p{L}\p{N}]+""")
    private val ignored = setOf(
        "a", "an", "and", "at", "about", "by", "for", "from", "how", "i",
        "in", "is", "it", "me", "my", "of", "on", "or", "our", "the", "to",
        "was", "were", "what", "when", "where", "why", "with", "you"
    )
    private const val MAX_QUERY = 130
    private const val MAX_RESULTS = 7

    fun parse(message: String): Request? {
        val clean = message.trim().replace(Regex("""\s+"""), " ")
        if (clean.equals("memory overview", ignoreCase = true) ||
            clean.equals("show memory overview", ignoreCase = true)) {
            return Request.Overview
        }
        val match = requestPattern.matchEntire(clean) ?: return null
        val term = match.groupValues[2].trim().trimEnd('?', '.', '!', ' ')
        if (term.length !in 2..MAX_QUERY) return null
        val notesOnly = match.groupValues[1].startsWith("find", true) &&
            match.groupValues[1].contains("notes", true)
        return Request.Search(term, notesOnly)
    }

    fun overview(
        notes: List<String>,
        tasks: List<String>,
        active: List<AstraChatArchive.Message>,
        archived: List<AstraChatArchive.Chat>
    ): String = buildString {
        appendLine("ASTRA // PRIVATE MEMORY")
        appendLine("Everything below is stored locally on your phone.")
        appendLine("Saved notes: ${notes.count { it.isNotBlank() }}")
        appendLine("Open tasks: ${tasks.count { it.isNotBlank() }}")
        appendLine("Current-chat user messages: ${active.count { it.role == "You" }}")
        appendLine("Archived conversations: ${archived.size}")
        appendLine("Archived user messages: ${archived.sumOf { chat ->
            chat.messages.count { it.role == "You" }
        }}")
        appendLine()
        appendLine("Try: recall bond duration")
        append("Or: what did I say about dividends")
    }

    fun find(
        term: String,
        notes: List<String>,
        tasks: List<String>,
        active: List<AstraChatArchive.Message>,
        archived: List<AstraChatArchive.Chat>,
        notesOnly: Boolean = false
    ): String {
        val keywords = words(term).distinct().take(10)
        if (keywords.isEmpty()) {
            return "Use a more specific keyword. Example: recall bond duration."
        }
        val best = mutableListOf<Match>()
        var matched = 0
        var seen = 0

        fun consider(source: String, raw: String, boost: Int) {
            if (raw.isBlank() || raw.length > 50_000) return
            // Long LLM output is not indexed, and only user-authored messages
            // enter the conversational index. Bound work for huge messages.
            val searchable = raw.take(4_000)
            val tokens = words(searchable).toSet()
            if (!keywords.all { it in tokens }) return
            matched++
            val phrase = keywords.joinToString(" ")
            val normalized = wordPattern.findAll(searchable.lowercase(Locale.ROOT))
                .joinToString(" ") { stem(it.value) }
            val phraseScore = if (normalized.contains(phrase)) 10 else 0
            val score = keywords.size * 5 + phraseScore + boost
            val candidate = Match(source, snippet(searchable, keywords), score, seen++)
            if (best.size < MAX_RESULTS) {
                best.add(candidate)
            } else {
                val worst = best.indices.minWithOrNull(
                    compareBy<Int> { best[it].score }.thenBy { best[it].order }
                ) ?: return
                if (candidate.score > best[worst].score ||
                    (candidate.score == best[worst].score &&
                        candidate.order > best[worst].order)) {
                    best[worst] = candidate
                }
            }
        }

        notes.forEachIndexed { index, note ->
            consider("Saved note #${index + 1}", note, boost = 8)
        }

        if (!notesOnly) {
            tasks.forEachIndexed { index, task ->
                consider("Open task #${index + 1}", task, boost = 5)
            }

            active.forEachIndexed { index, message ->
                if (message.role == "You" && parse(message.text) == null) {
                    consider("Current chat · message #${index + 1}", message.text, 4)
                }
            }

            // No assistant-authored message is included. Results identify their
            // original chat, rather than attributing model output to the user.
            archived.forEach { chat ->
                chat.messages.forEachIndexed { index, message ->
                    if (message.role == "You" && parse(message.text) == null) {
                        consider(
                            "Conversation '${chat.title.take(50)}' · message #${index + 1}",
                            message.text, 2
                        )
                    }
                }
            }
        }

        if (matched == 0) return if (notesOnly) {
            "No saved notes match '$term'. Nothing was inferred or invented."
        } else {
            "No matching information found in your local notes, tasks or conversations for '$term'. Nothing was inferred or invented."
        }

        return buildString {
            appendLine("ASTRA // PRIVATE MEMORY RECALL")
            appendLine("Query: $term")
            appendLine("Found $matched matching entries; showing ${best.size}.")
            appendLine("Sources are local user messages, notes and tasks, not AI guesses.")
            appendLine()
            best.sortedWith(
                compareByDescending<Match> { it.score }.thenByDescending { it.order }
            ).forEachIndexed { index, hit ->
                appendLine("${index + 1}. ${hit.source}")
                appendLine("   ${hit.excerpt}")
            }
        }.trimEnd().take(3_700)
    }

    private fun words(value: String): List<String> =
        wordPattern.findAll(value.lowercase(Locale.ROOT))
            .map { stem(it.value) }
            .filter { it.length >= 2 && it !in ignored }
            .toList()

    private fun stem(word: String): String =
        if (word.length >= 5 && word.endsWith("s") &&
            !word.endsWith("ss") && !word.endsWith("is") && !word.endsWith("us")) {
            word.dropLast(1)
        } else word

    private fun snippet(raw: String, keywords: List<String>): String {
        val compact = raw.replace(Regex("""\s+"""), " ").trim()
        if (compact.length <= 190) return compact
        val lower = compact.lowercase(Locale.ROOT)
        val first = keywords.asSequence()
            .map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (first - 65).coerceIn(0, compact.length - 190)
        val end = start + 190
        return (if (start > 0) "…" else "") +
            compact.substring(start, end).trim() +
            (if (end < compact.length) "…" else "")
    }
}
