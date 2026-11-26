package com.astra.agent

/**
 * Experimental selection policy, not connected to live inference.
 * Agreement is not proof: correlated model errors can form a majority.
 * Keep the first response unless two complete independent retries agree
 * on one canonical scalar for an explicitly scalar-valued owner request.
 */
internal object AstraReasoningConsensus {
    data class Attempt(val visible: String, val complete: Boolean)

    private val integerRequest = Regex(
        """(?i)(?:[?.!]\s+|^)(?:reply|answer|respond)\s+(?:with\s+only|only\s+with)\s+(?:the\s+|an?\s+)?integer[.!?]?$"""
    )
    private val booleanRequest = Regex(
        """(?i)(?:[?.!]\s+|^)(?:reply|answer|respond)\s+(?:only\s+)?yes\s+or\s+no\s+only[.!?]?$"""
    )
    private val integer = Regex("""[+-]?[0-9]{1,40}""")
    private val boolean = Regex("""(?i)yes|no""")

    fun select(question: String, attempts: List<Attempt>): Int {
        // Exact three attempts; the caller must use genuinely separate
        // inference calls. Never invoke a model or a tool from this policy.
        if (attempts.size != 3 || question.length !in 12..700 ||
            question.any { it in listOf('\r', '\n', '"', '“', '”', '<', '>', 96.toChar()) } ||
            question.contains("EARLIER CHAT EXCERPTS", ignoreCase = true) ||
            question.contains("SAME-CHAT FOLLOW-UP", ignoreCase = true) ||
            question.contains("ORIGINAL QUESTION:", ignoreCase = true)) return 0
        val isInteger = integerRequest.containsMatchIn(question.trim())
        if (!isInteger && !booleanRequest.containsMatchIn(question.trim())) return 0

        fun key(attempt: Attempt): String? {
            if (!attempt.complete || AstraInterruptedReply.isIncomplete(attempt.visible) ||
                attempt.visible.length !in 1..80) return null
            val scalar = attempt.visible.trim().trimEnd('.', '!').trim()
            if (isInteger) return if (integer.matches(scalar))
                // Kotlin's nullable BigInteger parser rejects leading '+'.
                // The strict regex has already validated the sign and digits.
                scalar.removePrefix("+").toBigIntegerOrNull()?.toString() else null
            return if (boolean.matches(scalar))
                scalar.uppercase(java.util.Locale.ROOT) else null
        }

        val second = key(attempts[1]) ?: return 0
        val third = key(attempts[2]) ?: return 0
        if (second != third || second == key(attempts[0])) return 0
        // Return the original visible response of a supporting attempt;
        // do not fabricate, calculate, or normalize the delivered answer.
        return 1
    }
}
