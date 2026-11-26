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

    data class Checked(val variable: String, val result: String, val steps: Int,
                       val simultaneousGroups: Int = 0)

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
        var steps = 0
        var simultaneousGroups = 0
        for (statement in statements) {
            val group = statement.trim()
            val simultaneous = group.startsWith("simultaneous ")
            val assignments = if (simultaneous)
                group.removePrefix("simultaneous ").split(',')
            else listOf(group)
            if (simultaneous && assignments.size !in 2..8) return null
            steps += assignments.size
            if (steps > 24) return null
            val pending = linkedMapOf<String, BigInteger>()
            // All RHS expressions in one simultaneous group read the SAME
            // pre-group state. No partial update, duplicate destination, or
            // implicit reference to a variable introduced within the group.
            for (part in assignments) {
                val parsed = assignment.matchEntire(part.trim()) ?: return null
                val name = parsed.groupValues[1]
                if (name in pending) return null
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
                pending[name] = value
            }
            if ((values.keys + pending.keys).size > 8) return null
            values.putAll(pending)
            if (simultaneous) simultaneousGroups++
        }
        val variable = match.groupValues[2]
        return Checked(variable, values[variable]?.toString() ?: return null, steps, simultaneousGroups)
    }

    fun report(checked: Checked, previous: String? = null): String? {
        val verdict = if (previous == null) "result: ${checked.result}." else {
            if (!Regex("""[+-]?[0-9]{1,19}""").matches(previous)) return null
            val value = previous.removePrefix("+").toBigIntegerOrNull() ?: return null
            if (value == checked.result.toBigInteger()) "correct."
            else "incorrect; corrected result: ${checked.result}."
        }
        return "INDEPENDENT STATE TRACE — " + verdict +
            (if (checked.simultaneousGroups == 0)
                "\nRecomputed ${checked.steps} sequential integer assignments for ${checked.variable}. "
            else "\nRecomputed ${checked.steps} integer assignments including ${checked.simultaneousGroups} simultaneous groups for ${checked.variable}. ") +
            "Only the explicitly submitted assignments were verified; no natural-language translation or arbitrary code was executed."
    }
}
