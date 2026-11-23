package com.astra.agent

import java.util.Locale

/**
 * Read-only explanations for the most recent verified calculation.
 *
 * Follow-ups can themselves be followed up, provided every intervening
 * completed reply equals the independently recomputed exact proof. No
 * unverified AI prose, stopped output or unrelated turn can extend a proof.
 * This accesses ONLY the current chat, never books, notes or archived chats.
 */
internal object AstraVerifiedCalculationFollowUp {
    private const val MAX_CHAIN_PAIRS = 4
    private val phrases = setOf(
        "how did you calculate that",
        "how did you get that",
        "how did you get that number",
        "how was that calculated",
        "show the calculation",
        "show me the calculation",
        "show the working",
        "show the steps",
        "walk me through the calculation",
        "explain that calculation",
        "explain the result",
        "explain that result",
        "where did that number come from",
        "what assumptions did you use",
        "explain it more simply",
        "can you explain that again"
    )

    private fun isFollowUp(raw: String): Boolean {
        if (raw.length !in 6..100 || raw.any { it == '\n' || it == '\r' }) {
            return false
        }
        val normalized = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trimEnd('?', '.', '!', ' ')
        return normalized in phrases
    }

    private fun exactProof(question: String, response: String): String? =
        AstraVerifiedArithmetic.verifiedWorking(question, response)
            ?: AstraVerifiedExpression.verifiedWorking(question, response)

    fun explain(
        request: String,
        activeChat: List<AstraChatArchive.Message>
    ): String? {
        if (!isFollowUp(request)) return null

        // A root calculation plus at most three consecutive follow-up pairs
        // can support another request. Never search arbitrarily far back
        // through unrelated history or carry context across conversations.
        var cursor = activeChat.size - 2
        var traversed = 0
        val followUps = mutableListOf<AstraChatArchive.Message>()

        while (cursor >= 0 && traversed < MAX_CHAIN_PAIRS) {
            val user = activeChat[cursor]
            val assistant = activeChat.getOrNull(cursor + 1) ?: return null
            if (user.role != "You" || assistant.role != "Astra" ||
                user.text.isBlank() || assistant.text.isBlank() ||
                AstraInterruptedReply.isIncomplete(assistant.text)
            ) return null

            val proof = exactProof(user.text, assistant.text)
            if (proof != null) {
                // Every later follow-up reply must match the exact same
                // deterministic proof, not just contain a convincing prefix.
                return proof.takeIf { verified ->
                    followUps.all { reply -> reply.text == verified }
                }
            }

            if (!isFollowUp(user.text)) return null
            followUps.add(assistant)
            cursor -= 2
            traversed++
        }
        return null
    }
}
