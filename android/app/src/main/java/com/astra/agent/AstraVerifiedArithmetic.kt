package com.astra.agent

import java.math.BigDecimal
import java.math.RoundingMode

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
