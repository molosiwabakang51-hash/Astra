package com.astra.agent

import java.util.Locale

/**
 * Search the user's existing local notes and tasks.
 *
 * This deliberately does NOT index private chat transcripts or send anything
 * to an external service. Retrieval is lexical, not generative or semantic AI.
 */
internal object AstraRecall {
    private data class MemoryHit(
        val source: String,
        val position: Int,
        val body: String,
        val score: Int
    )

    private val request = Regex(
        """(?i)^(?:recall(?:\s+my\s+notes\s+about)?|search\s+(?:my\s+)?memory\s+for|find\s+(?:my\s+)?notes\s+(?:about|on)|what\s+did\s+i\s+(?:say|write|note|tell\s+you)\s+about|what\s+have\s+i\s+(?:noted|saved)\s+about)\s+(.+)$"""
    )
    private val wordPattern = Regex("""[\p{L}\p{N}]+""")
    private val whitespace = Regex("""\s+""")
    private val ignored = setOf(
        "a", "an", "the", "i", "my", "me", "on", "of", "for", "from",
        "with", "that", "this", "to", "in", "at", "is", "are", "was"
    )

    private fun words(text: String): List<String> =
        wordPattern.findAll(text.lowercase(Locale.ROOT))
            .map { it.value }.toList()

    fun respond(raw: String, notes: List<String>, tasks: List<String>): String? {
        val input = raw.trim().trimEnd('?', '!', '.').trim()
        val lower = input.lowercase(Locale.ROOT)

        if (lower in setOf(
                "memory overview", "what do you remember",
                "show my memory", "memory status", "what have you saved"
            )
        ) {
            return "ASTRA // LOCAL MEMORY\n" +
                notes.size + " saved notes | " + tasks.size + " open tasks\n" +
                "Say 'recall bond duration' or 'what did I note about dividends'. " +
                "This searches locally saved notes and tasks, not your entire chat history."
        }

        val match = request.matchEntire(input) ?: return null
        val query = match.groupValues[1].trim()
        if (query.length > 160) return "Please shorten your memory search to 160 characters."

        val terms = words(query).filterNot { it in ignored }.distinct().take(12)
        if (terms.isEmpty()) return "Please name a topic, such as 'recall bond duration'."

        val onlyNotes = lower.startsWith("find my notes ") ||
            lower.startsWith("find notes ") ||
            lower.startsWith("recall my notes about")

        val hits = buildList {
            notes.forEachIndexed { index, note ->
                val score = score(note, query, terms)
                if (score > 0) add(MemoryHit("Note", index + 1, note, score))
            }
            if (!onlyNotes) {
                tasks.forEachIndexed { index, task ->
                    val score = score(task, query, terms)
                    if (score > 0) add(MemoryHit("Task", index + 1, task, score))
                }
            }
        }.sortedWith(
            compareByDescending<MemoryHit> { it.score }
                .thenBy { it.source }
                .thenBy { it.position }
        ).take(5)

        if (hits.isEmpty()) {
            return "No saved notes or tasks match '" + query + "'. " +
                "This local search cannot infer information that was never saved."
        }

        val summary = hits.joinToString("\n\n") { hit ->
            val clean = hit.body.replace(whitespace, " ").trim()
            val excerpt = clean.take(280) + if (clean.length > 280) "…" else ""
            hit.source + " " + hit.position + ": " + excerpt
        }

        return "ASTRA // MEMORY RECALL\n" +
            "Matches for '" + query + "' (top " + hits.size + "):\n\n" +
            summary + "\n\n" +
            "Searched on this device only. Matches are based on words and phrases " +
            "in saved notes/tasks, not inferred memories or full chat history."
    }

    private fun score(body: String, query: String, terms: List<String>): Int {
        val normalizedBody = body.lowercase(Locale.ROOT)
        val normalizedQuery = query.lowercase(Locale.ROOT)
        val bodyWords = words(body).toSet()
        var score = if (normalizedBody.contains(normalizedQuery)) 12 else 0
        for (term in terms) {
            score += when {
                term in bodyWords -> 4
                term.length >= 4 && bodyWords.any {
                    it.startsWith(term) || term.startsWith(it) && it.length >= 4
                } -> 2
                else -> 0
            }
        }
        return score
    }
}
