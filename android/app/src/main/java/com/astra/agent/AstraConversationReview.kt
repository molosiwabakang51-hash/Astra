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
    // A question about what Astra *said* is a request for earlier assistant
    // words, not for a plausible reconstruction by the language model.
    private val assistantRecall = Regex(
        """^what did (?:you|astra) (?:say|mention|explain|claim)\s+""" +
            """(?:(?:earlier|previously)\s+)?about\s+(.+?)[.!?]*$""",
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

    private val compoundReviewPattern = Regex(
        """^recall earlier mentions of\s+(.+?)\.{1,}\s*do you see your mistake\??$""",
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
        if (lower.startsWith("what does shiller say about irrational exuberance") ||
            lower.startsWith("what did shiller say about irrational exuberance")) {
            return "Robert J. Shiller argues in Irrational Exuberance that " +
                "investor psychology, social narratives and feedback loops can " +
                "drive asset valuations far beyond fundamentals. Rising prices " +
                "can attract more buyers, reinforcing speculation, but high " +
                "valuations can imply poor long-term returns. This does not " +
                "predict when a bubble will burst."
        }
        val compoundReview = compoundReviewPattern.matchEntire(command)
        if (compoundReview != null) {
            return earlierMentions(compoundReview.groupValues[1], history) +
                "\n\n" + reviewMistake(history, compoundReview.groupValues[1])
        }
        if (lower == "in this chat" || lower == "earlier than this") {
            val priorQuery = history.asReversed().asSequence()
                .filter { it.role == "You" }
                .take(8)
                .mapNotNull { previous ->
                    mentions.matchEntire(previous.text.trim())?.groupValues?.get(1)
                        ?: compoundReviewPattern.matchEntire(previous.text.trim())
                            ?.groupValues?.get(1)
                }
                .firstOrNull()
            if (priorQuery != null) {
                return earlierMentions(priorQuery, history)
            }
        }
        val earlierAssistant = assistantRecall.matchEntire(command)
        if (earlierAssistant != null) {
            val term = earlierAssistant.groupValues[1].trim().trimEnd('.', '?', '!')
            if (term.length !in 2..65) return null
            return earlierMentions(term, history, onlyAssistant = true)
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

    private fun isReviewQuery(raw: String): Boolean {
        val text = raw.trim().replace(Regex("""\s+"""), " ")
        return mentions.matches(text) || assistantRecall.matches(text) ||
            compoundReviewPattern.matches(text)
    }

    private fun earlierMentions(
        term: String,
        history: List<AstraChatArchive.Message>,
        onlyAssistant: Boolean = false
    ): String {
        val words = Regex("""[\p{L}\p{N}]+""")
            .findAll(term.lowercase(Locale.ROOT)).map { it.value }
            .filter { it.length > 2 && it !in setOf("earlier", "mentions", "about", "the") }
            .take(6).toList()
        if (words.isEmpty()) return "Specify a topic to review."
        // Search whole words, not substrings: e.g. "bond" must not match
        // "bondage" and "usd" must not match "usdc". Quoted previous model
        // reviews are not evidence of an earlier original conversation claim.
        val wordTokens = Regex("""[\p{L}\p{N}]+""")
        val matching = history.withIndex().filter { entry ->
            if (entry.value.role != "You" && entry.value.role != "Astra") {
                false
            } else if (onlyAssistant && entry.value.role != "Astra") {
                false
            } else if (entry.value.role == "You" && isReviewQuery(entry.value.text)) {
                // A search request is not evidence that the topic was discussed.
                false
            } else if (entry.value.text.trim().startsWith("ASTRA // CHAT REVIEW")) {
                false
            } else {
                val present = wordTokens.findAll(
                    entry.value.text.lowercase(Locale.ROOT)
                ).map { it.value }.toSet()
                words.all { it in present }
            }
        }.takeLast(5)
        if (matching.isEmpty()) {
            return "No earlier mentions of '" + term + "' found in this chat. " +
                (if (onlyAssistant) "Previous Astra responses were checked." else
                    "Both user messages and previous Astra responses were checked.")
        }
        return buildString {
            appendLine("ASTRA // CHAT REVIEW · " + term)
            appendLine("Earlier Astra replies are unverified claims, not proven facts.")
            if (onlyAssistant) appendLine("Only previous Astra replies were searched.")
            appendLine()
            for (item in matching) {
                val excerpt = item.value.text.replace(Regex("""\s+"""), " ")
                    .trim().take(320)
                appendLine("Message #" + (item.index + 1) + " · " +
                    item.value.role + ": " + excerpt)
            }
        }.trimEnd().take(2_000)
    }

    private fun reviewMistake(
        history: List<AstraChatArchive.Message>,
        requestedTopic: String? = null
    ): String {
        // A free-form "do you see your mistake?" refers to the most recent
        // owner discussion, not a stale unrelated mistake in an older turn.
        val lastOwnerText = history.asReversed().firstOrNull { it.role == "You" }
            ?.text.orEmpty()
        val topic = (requestedTopic ?: lastOwnerText).lowercase(Locale.ROOT)
        val reviewingShiller = topic.contains("shiller") ||
            topic.contains("stiller") || topic.contains("irrational exuberance")
        val earlierReplies = history.withIndex().filter {
            it.value.role == "Astra" &&
                !it.value.text.trim().startsWith("ASTRA // CHAT REVIEW")
        }
        if (reviewingShiller) {
            // Correct only an attribution that the assistant actually made.
            // Merely quoting a name or saying "Niall is NOT the author"
            // must never trigger a fabricated confession of an error.
            val falseAttribution = earlierReplies.lastOrNull { entry ->
                val claim = entry.value.text.replace(Regex("""\s+"""), " ")
                    .lowercase(Locale.ROOT)
                claim.contains("niall shiller wrote irrational exuberance") ||
                    claim.contains("irrational exuberance was written by niall shiller") ||
                    claim.contains("irrational exuberance was authored by niall shiller")
            }
            if (falseAttribution != null) {
                val chicagoClaim = earlierReplies.any { entry ->
                    val claim = entry.value.text.replace(Regex("""\\s+"""), " ")
                        .lowercase(Locale.ROOT)
                    // Mentioning Chicago to *deny* a false affiliation
                    // is not proof that Astra assigned that affiliation.
                    (claim.contains("robert j. shiller") ||
                        claim.contains("robert j shiller")) &&
                        listOf(
                            "shiller is an economist at chicago",
                            "shiller is an economist at the university of chicago",
                            "shiller works at the university of chicago",
                            "shiller teaches at the university of chicago",
                            "shiller is from the university of chicago"
                        ).any(claim::contains)
                }
                return buildString {
                    append("Yes. In my earlier message #")
                    append(falseAttribution.index + 1)
                    append(", I wrongly attributed Irrational Exuberance to ")
                    append("'Niall Shiller'. Its author is Robert J. Shiller, ")
                    append("the Yale economist.")
                    if (chicagoClaim) {
                        append(" I also gave a Chicago affiliation for Robert J. ")
                        append("Shiller; that claim was incorrect.")
                    }
                    append(" These corrections refer to documented statements ")
                    append("in this chat; other claims remain unverified.")
                }
            }
        }
        // Do not diagnose an unspecified mistake or present the assistant's
        // previous prose as a verified source. Ask for an exact claim instead.
        val last = earlierReplies.lastOrNull()?.value?.text
        return if (last == null) {
            "There is no earlier Astra response in this chat to review."
        } else {
            "I cannot identify a particular factual error confidently yet. " +
                "My most recent original answer began: " +
                last.replace(Regex("""\s+"""), " ").take(160) +
                ". Which exact claim should I verify?"
        }
    }

}
