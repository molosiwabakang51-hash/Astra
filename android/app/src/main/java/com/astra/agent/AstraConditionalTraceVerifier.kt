package com.astra.agent

import java.math.BigInteger

/**
 * Exact, bounded verification of an explicitly supplied single-counter
 * recurrence. This is a fixed grammar, not a programming language or a
 * model-generated interpretation of arbitrary prose. No external effects.
 */
internal object AstraConditionalTraceVerifier {
    private val request = Regex(
        """^Trace conditional counter: x=(-?[0-9]{1,9}); repeat ([1-9][0-9]?) times: if (.+) then (.+) else (.+)\. What is x\?(?: Reply with only the integer\.)?$"""
    )
    private val comparison = Regex("""^x (<=|>=|==|!=|<|>) (-?[0-9]{1,9})$""")
    private val constant = Regex("""^x=(-?[0-9]{1,9})$""")
    private val add = Regex("""^x=x([+-])([0-9]{1,9})$""")
    private val scale = Regex("""^x=x([*/])(-?[0-9]{1,9})(?:([+-])([0-9]{1,9}))?$""")
    private val limit = BigInteger.TEN.pow(18)
    private val two = BigInteger.valueOf(2)

    data class Checked(val result: String, val iterations: Int, val trueBranches: Int)

    private fun bounded(value: BigInteger): BigInteger? = value.takeIf { it.abs() < limit }

    private fun condition(text: String): ((BigInteger) -> Boolean)? {
        if (text == "x is even") return { it.remainder(two).signum() == 0 }
        if (text == "x is odd") return { it.remainder(two).signum() != 0 }
        val match = comparison.matchEntire(text) ?: return null
        val rhs = match.groupValues[2].toBigIntegerOrNull() ?: return null
        return when (match.groupValues[1]) {
            "<" -> { x -> x < rhs }
            "<=" -> { x -> x <= rhs }
            ">" -> { x -> x > rhs }
            ">=" -> { x -> x >= rhs }
            "==" -> { x -> x == rhs }
            "!=" -> { x -> x != rhs }
            else -> null
        }
    }

    private fun update(text: String): ((BigInteger) -> BigInteger?)? {
        constant.matchEntire(text)?.let { match ->
            val rhs = match.groupValues[1].toBigIntegerOrNull() ?: return null
            return { bounded(rhs) }
        }
        add.matchEntire(text)?.let { match ->
            val rhs = match.groupValues[2].toBigIntegerOrNull() ?: return null
            val subtract = match.groupValues[1] == "-"
            return { x -> bounded(if (subtract) x.subtract(rhs) else x.add(rhs)) }
        }
        val match = scale.matchEntire(text) ?: return null
        val rhs = match.groupValues[2].toBigIntegerOrNull() ?: return null
        val divide = match.groupValues[1] == "/"
        if (divide && rhs.signum() == 0) return null
        val offset = if (match.groupValues[4].isEmpty()) BigInteger.ZERO
            else match.groupValues[4].toBigIntegerOrNull() ?: return null
        val subtract = match.groupValues[3] == "-"
        return operation@{ x ->
            val intermediate = if (divide) {
                val pair = x.divideAndRemainder(rhs)
                if (pair[1].signum() != 0) return@operation null
                pair[0]
            } else x.multiply(rhs)
            val safe = bounded(intermediate) ?: return@operation null
            bounded(if (subtract) safe.subtract(offset) else safe.add(offset))
        }
    }

    fun verify(raw: String): Checked? {
        if (raw.length !in 50..650 || raw != raw.trim() ||
            raw.any { it.isISOControl() }) return null
        val match = request.matchEntire(raw) ?: return null
        var x = match.groupValues[1].toBigIntegerOrNull() ?: return null
        val count = match.groupValues[2].toIntOrNull() ?: return null
        if (count !in 1..24) return null
        val predicate = condition(match.groupValues[3]) ?: return null
        // Validate BOTH branch expressions before running either branch.
        val whenTrue = update(match.groupValues[4]) ?: return null
        val whenFalse = update(match.groupValues[5]) ?: return null
        var trueBranches = 0
        repeat(count) {
            val selected = if (predicate(x)) {
                trueBranches++
                whenTrue
            } else whenFalse
            x = selected(x) ?: return null
        }
        return Checked(x.toString(), count, trueBranches)
    }
}
