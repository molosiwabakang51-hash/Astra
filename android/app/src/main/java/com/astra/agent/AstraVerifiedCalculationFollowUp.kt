package com.astra.agent

import java.util.Locale

/**
 * Read-only follow-up to a previously verified arithmetic result in THIS chat.
 *
 * Only the last completed user/Astra pair may be examined; never promote
 * arbitrary model-written calculations or historical quoted text to facts.
 * No tool writes, private memory access, network activity or model loading.
 */
internal object AstraVerifiedCalculationFollowUp {
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
        "what assumptions did you use"
    )

    fun explain(
        request: String,
        activeChat: List<AstraChatArchive.Message>
    ): String? {
        if (request.length !in 6..100 || request.any { it == '\n' || it == '\r' }) {
            return null
        }
        val normalized = request.trim().lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trimEnd('?', '.', '!', ' ')
        if (normalized !in phrases) return null

        val last = activeChat.takeLast(2)
        if (last.size != 2) return null
        val previousUser = last[0]
        val previousReply = last[1]
        if (previousUser.role != "You" || previousReply.role != "Astra" ||
            AstraInterruptedReply.isIncomplete(previousReply.text)
        ) return null

        // Independently re-run the exact bounded deterministic calculator;
        // a plausible but mistaken previous AI answer is not authoritative.
        return AstraVerifiedArithmetic.verifiedWorking(
            previousUser.text, previousReply.text
        )
    }
}
