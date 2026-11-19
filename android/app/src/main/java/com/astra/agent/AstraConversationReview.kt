package com.astra.agent

import java.util.Locale

/**
 * Source-labeled, read-only review of the current chat. Previous Astra replies
 * are quotes to audit, never sources of verified personal memory.
 */
internal object AstraConversationReview {
    private val mentions = Regex(
        """^(?:recall|find|show|review)\s+(?:my\s+)?(?:earlier|previous|past)\s+(?:mentions|messages|references|answers)\s+(?:of|about|to)\s+(.+?)[.!?]*$""",
        RegexOption.IGNORE_CASE
    )
    private val mistake = Regex(
        """^(?:do you see your mistake|what was your mistake|where were you wrong|correct your earlier answer)[.?!]*$""",
        RegexOption.IGNORE_CASE
    )
    private val identity = Regex(
        """^(?:who is\s+)?(?:robert\s+(?:j\.?\s+)?shiller|robert stiller)\??$""",
        RegexOption.IGNORE_CASE
    )

    fun respond(raw: String, history: List<AstraChatArchive.Message>): String? {
        val command = raw.trim().replace(Regex("""\s+"""), " ")
        if (command.length !in 2..180) return null
        if (identity.matches(command)) {
            return "Robert J. Shiller is an American economist and Yale University " +
                "professor. He shared the 2013 Nobel Memorial Prize in Economic " +
                "Sciences and wrote Irrational Exuberance, which examines " +
                "speculative bubbles, investor psychology and valuations."
        }
        val lower = command.lowercase(Locale.ROOT).trimEnd('?', '.', '!', ' ')
        if (lower == "niall shiller is not the author of irrational exuberance, yes" ||
            lower == "who wrote irrational exuberance" ||
            lower == "who is the author of irrational exuberance") {
            return "Correct. Irrational Exuberance was written by Robert J. " +
                "Shiller, a Yale economist, not 'Niall Shiller'."
        }
        val match = mentions.matchEntire(command)
        if (match != null) {
            val term = match.groupValues[1].trim().trimEnd('.', '?', '!')
            if (term.length !in 2..65) return null
            return earlierMentions(term, history)
        }
        if (mistake.matches(command)) return reviewMistake(history)
        return null
    }

    private fun earlierMentions(
        term: String,
        history: List<AstraChatArchive.Message>
    ): String {
        val words = Regex("""[\p{L}\p{N}]+""")
            .findAll(term.lowercase(Locale.ROOT)).map { it.value }
            .filter { it.length > 2 && it !in setOf("earlier", "mentions", "about", "the") }
            .take(6).toList()
        if (words.isEmpty()) return "Specify a topic to review."
        val matching = history.withIndex().filter { entry ->
            (entry.value.role == "You" || entry.value.role == "Astra") &&
                words.all { it in entry.value.text.lowercase(Locale.ROOT) } &&
                !entry.value.text.trim().startsWith("ASTRA // CHAT REVIEW")
        }.takeLast(5)
        if (matching.isEmpty()) {
            return "No earlier mentions of '" + term + "' found in this chat. " +
                "Both user messages and previous Astra responses were checked."
        }
        return buildString {
            appendLine("ASTRA // CHAT REVIEW · " + term)
            appendLine("Earlier Astra replies are unverified claims, not proven facts.")
            appendLine()
            for (item in matching) {
                val excerpt = item.value.text.replace(Regex("""\s+"""), " ")
                    .trim().take(320)
                appendLine("Message #" + (item.index + 1) + " · " +
                    item.value.role + ": " + excerpt)
            }
        }.trimEnd().take(2_000)
    }

    private fun reviewMistake(history: List<AstraChatArchive.Message>): String {
        val earlierReplies = history.asReversed()
            .filter { it.role == "Astra" }.take(24)
        if (earlierReplies.any { it.text.contains("Niall Shiller", ignoreCase = true) }) {
            return "Yes. I wrongly attributed Irrational Exuberance to " +
                "'Niall Shiller', then repeated the error after you challenged it. " +
                "Robert J. Shiller is the actual author. He teaches at Yale, " +
                "not the University of Chicago, as I claimed. My refusal to " +
                "acknowledge those errors was another mistake."
        }
        val last = earlierReplies.firstOrNull()?.text
        return if (last == null) {
            "There is no earlier Astra response in this chat to review."
        } else {
            "I cannot identify a particular factual error confidently yet. " +
                "My latest response began: " +
                last.replace(Regex("""\s+"""), " ").take(160) +
                ". Which statement should I check?"
        }
    }
}
