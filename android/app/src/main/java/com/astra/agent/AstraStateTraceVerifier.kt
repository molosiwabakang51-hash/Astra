package com.astra.agent

import java.math.BigInteger

/**
 * Independent verifier for a deliberately small, owner-supplied integer
 * assignment language. Never executes code, infers missing steps, or accepts
 * a model-generated translation without explicit owner confirmation.
 */
internal object AstraStateTraceVerifier {
    private val question = Regex(
        """^Trace integer assignments: (.+)\. What is ([a-z])\?(?: Reply with only the integer\.)?$"""
    )
    private val assignment = Regex("""^([a-z])\s*=\s*(.+)$""")
    private val expression = Regex(
        """^([a-z]|-?[0-9]{1,9})(?:\s*([+*-])\s*([a-z]|-?[0-9]{1,9}))?$"""
    )
    private val limit = BigInteger.TEN.pow(18)

    data class Checked(val variable: String, val result: String, val steps: Int)

    fun verify(raw: String): Checked? {
        if (raw.length !in 35..650 || raw != raw.trim() ||
            raw.any { it.isISOControl() }) return null
        val match = question.matchEntire(raw) ?: return null
        val statements = match.groupValues[1].split(';')
        if (statements.size !in 1..24) return null
        val values = mutableMapOf<String, BigInteger>()
        fun operand(token: String): BigInteger? =
            if (token.length == 1 && token[0] in 'a'..'z') values[token]
            else token.toBigIntegerOrNull()
        for (statement in statements) {
            val parsed = assignment.matchEntire(statement.trim()) ?: return null
            val name = parsed.groupValues[1]
            val rhs = expression.matchEntire(parsed.groupValues[2]) ?: return null
            val first = operand(rhs.groupValues[1]) ?: return null
            val op = rhs.groupValues[2]
            val value = if (op.isEmpty()) first else {
                val second = operand(rhs.groupValues[3]) ?: return null
                when (op) {
                    "+" -> first.add(second)
                    "-" -> first.subtract(second)
                    "*" -> first.multiply(second)
                    else -> return null
                }
            }
            if (value.abs() >= limit) return null
            // Assignment is sequential: evaluate both operands using the
            // pre-assignment state, then commit the new variable value.
            if (name !in values && values.size >= 8) return null
            values[name] = value
        }
        val variable = match.groupValues[2]
        return Checked(variable, values[variable]?.toString() ?: return null, statements.size)
    }

    fun report(checked: Checked, previous: String? = null): String? {
        val verdict = if (previous == null) "result: ${checked.result}." else {
            if (!Regex("""[+-]?[0-9]{1,19}""").matches(previous)) return null
            val value = previous.removePrefix("+").toBigIntegerOrNull() ?: return null
            if (value == checked.result.toBigInteger()) "correct."
            else "incorrect; corrected result: ${checked.result}."
        }
        return "INDEPENDENT STATE TRACE — " + verdict +
            "\nRecomputed ${checked.steps} sequential integer assignments for ${checked.variable}. " +
            "Only the explicitly submitted assignments were verified; no natural-language translation or arbitrary code was executed."
    }
}
