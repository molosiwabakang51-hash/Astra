package com.astra.agent

import java.util.Locale

/**
 * Source-only lexical ranking for private book excerpts.
 *
 * Title mentions alone never count as passage evidence. Distinctive query
 * terms must appear in the passage itself; generic mentions such as "Russian"
 * cannot override a missing "genitive", "alphabet" or other actual subject.
 *
 * This is inexpensive on-device lexical retrieval, NOT a semantic embedding
 * model or a guarantee that the underlying book establishes the answer.
 */
internal object AstraBookRanking {
    private val words = Regex("""[\p{L}\p{N}]+""")
    private val whitespace = Regex("""\s+""")
    private val stop = setOf(
        "a", "an", "the", "of", "and", "for", "to", "in", "on", "at", "by",
        "with", "do", "does", "are", "is", "was", "were", "can", "you",
        "me", "my", "i", "how", "what", "why", "where", "about", "from",
        "explain", "please", "tell", "using", "ask", "book", "books",
        "read", "this", "that", "these", "those", "could", "would",
        "which", "give", "show", "more", "some", "information", "learn"
    )
    private val generic = setOf(
        "russian", "language", "english", "text", "lesson", "course",
        "chapter", "manual", "textbook", "dictionary", "study"
    )

    /** Stable, deduplicated terms with limited English plural normalization. */
    fun queryTerms(query: String): List<String> =
        words.findAll(query.lowercase(Locale.ROOT)).take(64)
            .map { root(it.value) }
            .filter { it.length >= 2 && it !in stop }
            .distinct()
            .toList()

    fun score(queryTerms: List<String>, passage: String, title: String): Int {
        if (queryTerms.isEmpty()) return 0
        val body = tokens(passage)
        if (body.isEmpty()) return 0
        val bodyWords = body.toSet()
        val matched = queryTerms.filter { it in bodyWords }
        if (matched.isEmpty()) return 0

        // If the user names a subject, an unrelated title-only hit is
        // insufficient evidence, regardless of book title or global language.
        val requiresSubject = queryTerms.any { it !in generic }
        if (requiresSubject && matched.none { it !in generic }) return 0

        val titleWords = tokens(title).toSet()
        val contentScore = matched.sumOf { if (it in generic) 2 else 12 }
        val coverage = (matched.size - 1).coerceAtLeast(0) * 4
        val titleBonus = matched.count { it in titleWords } * 2

        // Extra small preference for distinct terms within the same 50-word
        // span; not enough to hide a stronger actual content match.
        val matchedPositions = body.withIndex()
            .filter { it.value in matched }.map { it.index }
        val adjacent = matched.size > 1 && matchedPositions.any { left ->
            matchedPositions.any { right ->
                right > left && right - left <= 50
            }
        }
        return contentScore + coverage + titleBonus + if (adjacent) 3 else 0
    }

    /** Centre the LLM excerpt around terms relevant to the actual question. */
    fun excerpt(source: String, question: String, maxChars: Int = 320): String {
        val limit = maxChars.coerceIn(80, 500)
        val text = source.replace(whitespace, " ").trim()
        if (text.length <= limit) return text
        val keys = queryTerms(question)
        if (keys.isEmpty()) return text.take(limit).trimEnd() + "…"

        val matches = words.findAll(text).take(1_600)
            .mapNotNull { hit ->
                val word = root(hit.value)
                if (word in keys) hit.range.first to word else null
            }.toList()
        if (matches.isEmpty()) return text.take(limit).trimEnd() + "…"

        // Choose a window with the greatest distinctive term coverage,
        // rather than always returning the first 320 characters of the chunk.
        var start = 0
        var bestScore = -1
        for ((position, _) in matches) {
            val candidate = (position - limit / 4).coerceIn(
                0, (text.length - limit).coerceAtLeast(0)
            )
            val covered = matches.asSequence()
                .filter { it.first in candidate until candidate + limit }
                .map { it.second }.toSet()
            val rank = covered.sumOf { if (it in generic) 1 else 5 } +
                covered.size
            if (rank > bestScore) {
                start = candidate
                bestScore = rank
            }
        }
        // Where possible, avoid splitting a word at the excerpt boundary.
        if (start > 0) {
            val nextSpace = text.indexOf(' ', start)
            if (nextSpace >= 0 && nextSpace < start + 24) {
                start = nextSpace + 1
            }
        }
        val end = (start + limit).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") +
            text.substring(start, end).trim() +
            (if (end < text.length) "…" else "")
    }

    private fun tokens(text: String): List<String> =
        words.findAll(text.lowercase(Locale.ROOT))
            .take(1_300).map { root(it.value) }.toList()

    private fun root(value: String): String = when {
        value.length > 5 && value.endsWith("ies") -> value.dropLast(3) + "y"
        value.length > 5 && (
            value.endsWith("ches") || value.endsWith("shes") ||
            value.endsWith("sses") || value.endsWith("xes")
        ) -> value.dropLast(2)
        value.length > 4 && value.endsWith("s") &&
            !value.endsWith("ss") && !value.endsWith("us") ->
                value.dropLast(1)
        else -> value
    }
}
