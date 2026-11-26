package com.astra.agent

/**
 * Restricted first-turn framing validated in paired actual-model CPU transfer.
 * Android integration must preserve the exact ordinary owner request.
 * Only explicit integer-only requests in isolated, ordinary thinking turns
 * qualify. Never modify a model-only, source-backed, diagnostic or follow-up
 * prompt. This changes format framing, not answer value or reasoning budget.
 */
internal object AstraScalarFraming {
    private const val SUFFIX =
        "\nKeep any reasoning in the thinking section. After thinking, " +
            "output exactly the requested answer, without explanation, label or markup."
    private val request = Regex(
        """(?i)(?:[?.!]\s+|^)(?:reply|answer|respond)\s+(?:with\s+only|only\s+with)\s+(?:the\s+|an?\s+)?integer[.!?]?$"""
    )
    private val sourceOrConflict = Regex(
        """(?i)(?:\b(?:translate|quote|paraphrase|transcript|verbatim|source text|document excerpt|show (?:the|your) (?:work|working|steps)|explain|step.by.step|justify|without thinking|no thinking|fast mode|do not think)\b|/no_think\b|/think\b|(?:^|[.!?]\s+)(?:system|assistant|user|source|previous answer)\s*:)"""
    )

    fun prepare(
        question: String,
        thinking: Boolean,
        hasHistory: Boolean,
        isolated: Boolean,
        sourceBacked: Boolean,
        autoLanguage: Boolean
    ): String {
        if (!thinking || hasHistory || isolated || sourceBacked || !autoLanguage ||
            question.length !in 20..650 || question != question.trim() ||
            question.any { it.isISOControl() || it in listOf('"', '\'', '“', '”', '‘', '’', '<', '>', 96.toChar()) } ||
            sourceOrConflict.containsMatchIn(question) ||
            !request.containsMatchIn(question) ||
            !question.any { it in '0'..'9' }
        ) return question
        return question + SUFFIX
    }
}
