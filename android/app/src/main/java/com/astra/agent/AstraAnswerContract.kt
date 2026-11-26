package com.astra.agent

/**
 * Format only unambiguous complete scalar replies. Never calculate, select
 * between answers or extract a scalar from explanatory prose.
 */
internal object AstraAnswerContract {
    private val integerRequest = Regex(
        """(?i)(?:[?.!]\s+|^)(?:reply|answer|respond)\s+(?:(?:with\s+only|only\s+with|only)\s+(?:the\s+|an?\s+)?integer|with\s+(?:the\s+|an?\s+)?integer\s+only)[.!?]?$"""
    )
    private val yesNoRequest = Regex(
        """(?i)(?:[?.!]\s+|^)(?:reply|answer|respond)\s+(?:(?:only\s+|with\s+only\s+|only\s+with\s+)yes\s+or\s+no(?:\s+only)?|(?:with\s+)?yes\s+or\s+no\s+only)[.!?]?$"""
    )
    // Distinguish a conservative apostrophe inside a word from quotation
    // boundaries. An unmatched single quote can otherwise expose a fake
    // terminal instruction. Fail closed on ambiguous quotation, including
    // possessive endings, rather than normalize source material.
    private val singleQuoteBoundary = Regex("""(?<![\p{L}\p{N}])['’]|['’](?![\p{L}\p{N}])|‘""")
    private val integer = Regex("""[+-]?[0-9]{1,80}""")
    private val boolean = Regex("""(?i)yes|no""")
    private val label = Regex("""(?i)^(?:final\s+answer|answer|result)\s*:\s*(.+)$""")
    private val boxed = Regex("""\\boxed\{([^{}]+)\}""")

    fun normalize(rawQuestion: String, visibleAnswer: String): String {
        val question = rawQuestion.trim()
        if (question.length !in 12..700 ||
            question.any { it in listOf('\n', '\r', '"', '“', '”', 96.toChar(), '<', '>') } ||
            singleQuoteBoundary.containsMatchIn(question) ||
            question.contains("SAME-CHAT FOLLOW-UP", ignoreCase = true) ||
            question.contains("ORIGINAL QUESTION:", ignoreCase = true) ||
            question.contains("EARLIER CHAT EXCERPTS", ignoreCase = true)
        ) return visibleAnswer
        val isInteger = integerRequest.containsMatchIn(question)
        if (!isInteger && !yesNoRequest.containsMatchIn(question)) return visibleAnswer
        if (AstraInterruptedReply.isIncomplete(visibleAnswer)) return visibleAnswer
        var text = visibleAnswer.trim()
        if (text.length !in 1..160 || text.any { it == '\r' || it == '\n' }) return visibleAnswer
        text = label.matchEntire(text)?.groupValues?.get(1)?.trim() ?: text
        text = text.removeSuffix(".").removeSuffix("!").trim()
        repeat(2) {
            text = when {
                text.startsWith("**") && text.endsWith("**") && text.length > 4 ->
                    text.substring(2, text.length - 2).trim()
                else -> boxed.matchEntire(text)?.groupValues?.get(1)?.trim() ?: text
            }
        }
        return if (isInteger) text.takeIf(integer::matches) ?: visibleAnswer
        else if (boolean.matches(text)) text.uppercase(java.util.Locale.ROOT)
        else visibleAnswer
    }
}
