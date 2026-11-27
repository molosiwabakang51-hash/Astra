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
            """(?:\s+(plus|minus|times|multiplied by|divided by)\s+|\s*([+\-−*×/÷])\s*)""" +
            "($NUMBER)" +
            """\s*\??(?:\s*(?:reply|answer)\s+only\s+with\s+(?:the\s+)?""" +
            """(?:number|result|answer)\s*[.!]?)?$""",
        RegexOption.IGNORE_CASE
    )

    // Canonicalize only the explicit operators in a fully matched owner request.
    private fun operator(match: MatchResult): String =
        when (val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            .lowercase(java.util.Locale.ROOT)) {
            "+" -> "plus"
            "-", "−" -> "minus"
            "*", "×" -> "times"
            "/", "÷" -> "divided by"
            else -> value
        }

    /**
     * Explain a previous answer only when its exact value can be independently
     * recomputed from the ORIGINAL owner request. Never trust a remembered
     * assistant reply as evidence that the arithmetic was correct.
     */
    fun verifiedWorking(originalQuestion: String, observedAnswer: String): String? {
        AstraCalculationAssist.verifiedLinearWorking(originalQuestion, observedAnswer)?.let { return it }
        val result = answer(originalQuestion) ?: return null
        if (result != observedAnswer.trim() ||
            result == "Division by zero is undefined."
        ) return null
        val match = expression.matchEntire(originalQuestion.trim()) ?: return null
        val left = match.groupValues[1].toBigDecimalOrNull()
            ?.stripTrailingZeros()?.toPlainString() ?: return null
        val right = match.groupValues[4].toBigDecimalOrNull()
            ?.stripTrailingZeros()?.toPlainString() ?: return null
        val symbol = when (operator(match)) {
            "plus" -> "+"
            "minus" -> "−"
            "times", "multiplied by" -> "×"
            "divided by" -> "÷"
            else -> return null
        }

        val steps = StringBuilder("Verified calculation:\n")
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
                steps.append("\n").append(x)
                    .append(" × (").append(tens).append(" + ").append(units)
                    .append(") = ").append(first).append(" + ").append(second)
                    .append(" = ").append(result)
            }
        }
        return steps.append(
            "\nRecomputed on this device using exact decimal arithmetic; " +
                "not an unverified AI explanation."
        ).toString()
    }

    /**
     * Explicit review only. Independently compare a bounded scalar or fraction
     * with the exact result of a complete two-operand owner question.
     * Do not certify a rounded recurring decimal or parse prose as a number.
     */
    fun audit(originalQuestion: String, observedAnswer: String): String? =
        auditOrReference(originalQuestion, observedAnswer)

    /** Independently recompute an explicit owner question without judging a prior answer. */
    fun reference(originalQuestion: String): String? = auditOrReference(originalQuestion, null)

    private fun auditOrReference(originalQuestion: String, observedAnswer: String?): String? {
        val question = originalQuestion.trim()
        if (question.length !in 8..180 || question.any { it == '\r' || it == '\n' }) return null
        val match = expression.matchEntire(question) ?: return null
        val left = match.groupValues[1].toBigDecimalOrNull() ?: return null
        val right = match.groupValues[4].toBigDecimalOrNull() ?: return null
        val actual = if (observedAnswer == null) null else {
            val raw = observedAnswer.trim()
            if (raw.length !in 1..80) return null
            val fraction = Regex("""([+-]?[0-9]{1,31})/([+-]?[0-9]{1,31})""").matchEntire(raw)
            if (fraction != null) {
                val numerator = fraction.groupValues[1].toBigDecimalOrNull() ?: return null
                val denominator = fraction.groupValues[2].toBigDecimalOrNull() ?: return null
                if (denominator.signum() == 0) return null
                numerator to denominator
            } else {
                if (!Regex("""[+-]?[0-9]{1,31}(?:\.[0-9]{1,31})?""").matches(raw)) return null
                (raw.toBigDecimalOrNull() ?: return null) to java.math.BigDecimal.ONE
            }
        }
        val op = operator(match)
        val expectedNumerator = when (op) {
            "plus" -> left.add(right)
            "minus" -> left.subtract(right)
            "times", "multiplied by" -> left.multiply(right)
            "divided by" -> left
            else -> return null
        }
        val expectedDenominator = if (op == "divided by") right else java.math.BigDecimal.ONE
        if (expectedDenominator.signum() == 0) {
            return "INDEPENDENT ARITHMETIC CHECK — " +
                (if (actual == null) "reference result: division by zero is undefined. " else "incorrect; division by zero is undefined. ") +
                "No numerical quotient exists."
        }
        // Preserve the existing exact terminating-decimal display. For a
        // recurring quotient, convert the original operands to a reduced ratio.
        val exact = answer(question) ?: run {
            if (op != "divided by") return null
            val scale = maxOf(left.scale(), right.scale())
            var n = left.movePointRight(scale).toBigIntegerExact()
            var d = right.movePointRight(scale).toBigIntegerExact()
            val gcd = n.gcd(d)
            n = n.divide(gcd)
            d = d.divide(gcd)
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            "$n/$d"
        }
        if (actual == null) return "INDEPENDENT ARITHMETIC CHECK — reference result: $exact." +
            "\nComputed from the original operands using exact arithmetic."
        val equal = actual.first.multiply(expectedDenominator)
            .compareTo(expectedNumerator.multiply(actual.second)) == 0
        return "INDEPENDENT ARITHMETIC CHECK — " +
            (if (equal) "correct." else "incorrect; corrected result: $exact.") +
            "\nComputed from the original operands using exact arithmetic and cross-multiplication. " +
            "Mathematical equality checked; answer-format compliance was not checked."
    }

    fun answer(raw: String): String? {
        val question = raw.trim()
        if (question.length !in 8..180 || question.contains('\n')) return null
        // Explicit single-variable inverse problems use exact rational algebra.
        AstraCalculationAssist.solveLinearEquation(question)?.let { return it }
        val match = expression.matchEntire(question) ?: return null
        val left = match.groupValues[1].toBigDecimalOrNull() ?: return null
        val right = match.groupValues[4].toBigDecimalOrNull() ?: return null
        val result = when (operator(match)) {
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
