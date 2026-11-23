package com.astra.agent

import java.util.Locale

/**
 * Read-only verifier for an owner-described hypothetical bond maturity.
 * Only "You" messages from the active chat are source facts. Assistant
 * guesses and archived chats are not eligible for this deterministic answer.
 */
internal object AstraBondMaturityContext {
    private enum class Intent { CURRENT, ORIGINAL, RATE_RISK, SIMPLIFY }
    private val yearNames = listOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight",
        "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
        "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
        "twenty"
    )
    private val number = "(?:[0-9]{1,2}|" + yearNames.joinToString("|") + ")"
    private val initial = Regex(
        """(?i)\bbonds?\b.{0,110}?\b(?:matures?|maturity|term)\b\s*""" +
            """(?:in|is|was|of|to)?\s*(""" + number + """)\s+years?\b"""
    )
    private val correction = Regex(
        """(?i)^(?:actually[,:]?\s*|correction[,:]?\s*|no[,:]?\s*)?""" +
            """(?:change|make|set|update|use)\b.{0,35}?""" +
            """\b(?:maturity|term|it|that)\s+(?:to\s+|at\s+|is\s+)?(""" +
            number + """)\s+years?\b"""
    )
    private val currentQuery = Regex(
        """^(?:what is|what's)\s+(?:the maturity of (?:my|the|this) bond|""" +
            """(?:my|the|this) bond'?s maturity)(?:\s+now|\s+currently)?$"""
    )
    private val originalQuery = Regex(
        """^(?:what was|what is)\s+(?:its original maturity|""" +
            """(?:my|the|this) bond'?s original maturity|""" +
            """the original maturity of (?:my|the|this) bond)$"""
    )
    private val riskQuery = Regex(
        """^(?:how does|how will)\s+(?:this|that|the maturity)\s+""" +
            """change affect (?:interest[- ]rate risk|the bond'?s interest[- ]rate risk)$"""
    )
    private val simplifyQueries = setOf(
        "explain that more simply", "explain it more simply",
        "put that more simply", "simplify that explanation"
    )

    private data class Fact(val original: Int, val current: Int)

    private fun years(raw: String): Int? {
        val lower = raw.lowercase(Locale.ROOT)
        val parsed = lower.toIntOrNull() ?:
            yearNames.indexOf(lower).takeIf { it >= 0 }?.plus(1)
        return parsed?.takeIf { it in 1..50 }
    }

    private fun intent(raw: String): Intent? {
        if (raw.length !in 6..160 || raw.any { it == '\n' || it == '\r' }) return null
        val query = raw.trim().lowercase(Locale.ROOT).trimEnd('?', '.', '!', ' ')
        return when {
            currentQuery.matches(query) -> Intent.CURRENT
            originalQuery.matches(query) -> Intent.ORIGINAL
            riskQuery.matches(query) -> Intent.RATE_RISK
            query in simplifyQueries -> Intent.SIMPLIFY
            else -> null
        }
    }

    private fun fact(history: List<AstraChatArchive.Message>): Fact? {
        val owner = history.takeLast(100).filter { it.role == "You" }
        val roots = owner.mapIndexedNotNull { index, message ->
            initial.find(message.text)?.groupValues?.get(1)?.let(::years)
                ?.let { index to it }
        }
        val root = roots.lastOrNull() ?: return null
        var current = root.second
        var mayCorrect = true
        for (message in owner.drop(root.first + 1)) {
            val next = correction.find(message.text)
                ?.groupValues?.get(1)?.let(::years)
            if (next != null && mayCorrect) {
                current = next
            } else if (intent(message.text) == null) {
                // An unrelated owner statement ends ambiguous correction
                // linking. It never carries over into another chat.
                mayCorrect = false
            }
        }
        return Fact(root.second, current)
    }

    fun respond(
        question: String,
        activeChat: List<AstraChatArchive.Message>
    ): String? {
        val kind = intent(question) ?: return null
        val data = fact(activeChat) ?: return null
        val prior = activeChat.takeLast(2)

        return when (kind) {
            Intent.ORIGINAL ->
                "Your hypothetical bond's original maturity was " +
                    data.original + " years."
            Intent.CURRENT ->
                "Your hypothetical bond's current maturity is " +
                    data.current + " years (originally " +
                    data.original + " years)."
            Intent.RATE_RISK ->
                "BOND MATURITY CHANGE · EDUCATIONAL COMPARISON\n" +
                    "Original maturity: " + data.original + " years; " +
                    "revised maturity: " + data.current + " years.\n" +
                    when {
                        data.current < data.original ->
                            "For otherwise similar fixed-rate bonds, shortening " +
                                "maturity generally reduces modified duration and " +
                                "interest-rate sensitivity: a yield rise would " +
                                "usually cause a smaller percentage price fall " +
                                "for the " + data.current + "-year bond than for " +
                                "the " + data.original + "-year bond."
                        data.current > data.original ->
                            "For otherwise similar fixed-rate bonds, extending " +
                                "maturity generally increases modified duration " +
                                "and interest-rate sensitivity: a yield rise " +
                                "would usually cause a larger percentage price " +
                                "fall for the " + data.current + "-year bond."
                        else -> "The stated maturity did not change."
                    } +
                    "\nMaturity is not modified duration. Coupon, yield and " +
                    "cash-flow structure determine exact price sensitivity; " +
                    "this is a fictional comparison, not an actual bond change."
            Intent.SIMPLIFY -> {
                // The previous assistant response must be the just-completed
                // deterministic explanation, never an unverified Qwen claim.
                if (prior.size != 2 || prior[0].role != "You" ||
                    prior[1].role != "Astra" ||
                    intent(prior[0].text) != Intent.RATE_RISK ||
                    !prior[1].text.startsWith(
                        "BOND MATURITY CHANGE · EDUCATIONAL COMPARISON\n"
                    ) || AstraInterruptedReply.isIncomplete(prior[1].text)
                ) return null
                when {
                    data.current < data.original ->
                        "Simply: a " + data.current + "-year bond usually " +
                            "changes less in price when rates change than a " +
                            "similar " + data.original + "-year bond. " +
                            "The exact amount depends on its coupon and yield."
                    data.current > data.original ->
                        "Simply: a " + data.current + "-year bond usually " +
                            "changes more in price when rates change than " +
                            "a similar " + data.original + "-year bond."
                    else ->
                        "Simply: its stated maturity did not change, so " +
                            "there is no maturity-driven change in rate risk."
                }
            }
        }
    }
}
