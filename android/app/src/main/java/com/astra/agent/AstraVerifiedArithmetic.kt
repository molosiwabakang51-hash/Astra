package com.astra.agent


/**
 * Trustworthy, deterministic answers to *unambiguous* elementary arithmetic
 * questions written in ordinary English. This is a local calculator, not
 * evidence of Qwen3's reasoning performance.
 *
 * The entire message must match an arithmetic question. Quoted book passages,
 * multiple calculations, workflows and speculative assumptions go to their
 * existing routes rather than being executed as calculator instructions.
 */
internal object AstraVerifiedArithmetic {
    private const val NUMBER = """[+-]?[0-9]{1,9}(?:\.[0-9]{1,4})?"""
    private val expression = Regex(
        """^(?:what is|what's|calculate|compute|how much is|work out|please calculate)\s+""" +
            "($NUMBER)" +
            """\s+(plus|minus|times|multiplied by|divided by)\s+""" +
            "($NUMBER)" +
            """\s*\??(?:\s*(?:reply|answer)\s+only\s+with\s+(?:the\s+)?""" +
            """(?:number|result|answer)\s*[.!]?)?$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Explain a previous answer only when its exact value can be independently
     * recomputed from the ORIGINAL owner request. Never trust a remembered
     * assistant reply as evidence that the arithmetic was correct.
     */
    fun verifiedWorking(originalQuestion: String, observedAnswer: String): String? {
        val result = answer(originalQuestion) ?: return null
        if (result != observedAnswer.trim() ||
            result == "Division by zero is undefined."
        ) return null
        val match = expression.matchEntire(originalQuestion.trim()) ?: return null
        val left = match.groupValues[1].toBigDecimalOrNull()
            ?.stripTrailingZeros()?.toPlainString() ?: return null
        val right = match.groupValues[3].toBigDecimalOrNull()
            ?.stripTrailingZeros()?.toPlainString() ?: return null
        val symbol = when (match.groupValues[2].lowercase(java.util.Locale.ROOT)) {
            "plus" -> "+"
            "minus" -> "−"
            "times", "multiplied by" -> "×"
            "divided by" -> "÷"
            else -> return null
        }

        val steps = StringBuilder("Verified calculation:\\n")
            .append(left).append(" ").append(symbol).append(" ")
            .append(right).append(" = ").append(result)
        // For a small positive integer multiplier, explicitly display the
        // distributive-law terms so "show the working" has checkable steps.
        if (symbol == "×" && left.toIntOrNull() != null &&
            right.toIntOrNull() != null
        ) {
            val x = left.toInt()
            val y = right.toInt()
            if (x in 1..9999 && y in 11..99) {
                val tens = (y / 10) * 10
                val units = y % 10
                val first = x * tens
                val second = x * units
                steps.append("\\n").append(x)
                    .append(" × (").append(tens).append(" + ").append(units)
                    .append(") = ").append(first).append(" + ").append(second)
                    .append(" = ").append(result)
            }
        }
        return steps.append(
            "\\nRecomputed on this device using exact decimal arithmetic; " +
                "not an unverified AI explanation."
        ).toString()
    }

    fun answer(raw: String): String? {
        val question = raw.trim()
        if (question.length !in 8..180 || question.contains('\n')) return null
        val match = expression.matchEntire(question) ?: return null
        val left = match.groupValues[1].toBigDecimalOrNull() ?: return null
        val right = match.groupValues[3].toBigDecimalOrNull() ?: return null
        val result = when (match.groupValues[2].lowercase(java.util.Locale.ROOT)) {
            "plus" -> left.add(right)
            "minus" -> left.subtract(right)
            "times", "multiplied by" -> left.multiply(right)
            "divided by" -> {
                if (right.signum() == 0) return "Division by zero is undefined."
                // Do not silently round a repeating quotient and pass it off
                // as exact. Let the normal AI/tools explain approximation.
                try {
                    left.divide(right)
                } catch (_: ArithmeticException) {
                    return null
                }
            }
            else -> return null
        }
        // No formatting locale dependency; an answer-only request receives
        // only the verified value, not an invented calculation narrative.
        return result.stripTrailingZeros().toPlainString()
    }
}
