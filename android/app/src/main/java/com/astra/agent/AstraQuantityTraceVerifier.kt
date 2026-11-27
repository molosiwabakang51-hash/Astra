package com.astra.agent

import java.math.BigDecimal

/** Exact owner-confirmed chronological quantity trace; no inferred chat referent. */
internal object AstraQuantityTraceVerifier {
    private val question = Regex(
        """^Trace quantity: (.+)\. What is the current amount\?(?: Reply with only the number\.)?$""",
        RegexOption.IGNORE_CASE
    )
    private const val NUMBER = """[+−-]?(?:[0-9]{1,12}(?:\.[0-9]{1,8})?|\.[0-9]{1,8})"""
    private const val MAGNITUDE = """(?:[0-9]{1,12}(?:\.[0-9]{1,8})?|\.[0-9]{1,8})"""
    private const val UNIT = """(?:coins?|dollars?|pounds?|euros?|pula|rand|years?|months?|weeks?|days?|hours?|minutes?|seconds?|percent|units?)"""
    private val initial = Regex("""^initial amount is ($NUMBER) ($UNIT)$""")
    private val replacement = Regex("""^(?:actually, )?(?:make it|set it to) ($NUMBER) ($UNIT)$""")
    private val change = Regex("""^(?:actually, )?(increase|decrease|raise|reduce) it by ($MAGNITUDE) ($UNIT|percentage points?|basis points?)$""")
    private val proportional = Regex("""^(?:actually, )?(increase|decrease|raise|reduce) it by ($MAGNITUDE) percent relative to its current amount$""")
    private val addSubtract = Regex("""^(?:actually, )?(?:add ($MAGNITUDE) ($UNIT) to it|subtract ($MAGNITUDE) ($UNIT) from it)$""")
    private val withdrawal = Regex("""^(?:actually, )?(?:the current amount is unknown|i do not know the current amount)$""")
    // A valid accumulated result can exceed a single input's width or
    // precision (basis-point conversion introduces two decimal places).
    private val resultNumber = Regex("""[+−-]?(?:[0-9]{1,16}(?:\.[0-9]{1,10})?|\.[0-9]{1,10})""")
    private val bound = BigDecimal.TEN.pow(16)
    private fun decimal(text: String): BigDecimal? = text.replace('−', '-').toBigDecimalOrNull()
    private fun unit(text: String): String = when (text) {
        "coins", "dollars", "pounds", "euros", "years", "months", "weeks",
        "days", "hours", "minutes", "seconds", "units" -> text.dropLast(1)
        else -> text
    }
    data class Checked(val result: String, val unit: String, val steps: Int)

    fun verify(raw: String): Checked? {
        if (raw.length !in 35..650 || raw != raw.trim() || raw.any { it.isISOControl() }) return null
        val match = question.matchEntire(raw) ?: return null
        val statements = match.groupValues[1].lowercase(java.util.Locale.ROOT).split(';').map(String::trim)
        if (statements.size !in 1..24) return null
        val start = initial.matchEntire(statements.first()) ?: return null
        val quantityUnit = unit(start.groupValues[2])
        var value: BigDecimal? = decimal(start.groupValues[1]) ?: return null
        for (step in statements.drop(1)) {
            val replace = replacement.matchEntire(step)
            val relative = change.matchEntire(step)
            val proportion = proportional.matchEntire(step)
            val add = addSubtract.matchEntire(step)
            when {
                withdrawal.matches(step) -> value = null
                replace != null -> {
                    if (unit(replace.groupValues[2]) != quantityUnit) return null
                    value = decimal(replace.groupValues[1]) ?: return null
                }
                proportion != null -> {
                    val old = value ?: return null
                    // Negative baselines make ordinary increase/decrease language ambiguous.
                    if (old.signum() < 0) return null
                    val rate = decimal(proportion.groupValues[2]) ?: return null
                    val positive = proportion.groupValues[1] in setOf("increase", "raise")
                    if (!positive && rate > BigDecimal("100")) return null
                    val fraction = rate.movePointLeft(2)
                    val factor = if (positive) BigDecimal.ONE.add(fraction) else BigDecimal.ONE.subtract(fraction)
                    value = old.multiply(factor).stripTrailingZeros()
                    // Never round silently or produce a result the scalar auditor cannot parse.
                    if (value.scale() > 10) return null
                }
                relative != null -> {
                    val old = value ?: return null
                    val amount = decimal(relative.groupValues[2]) ?: return null
                    val rawUnit = relative.groupValues[3]
                    val delta = when {
                        rawUnit == "percent" -> return null
                        rawUnit.startsWith("percentage point") && quantityUnit == "percent" -> amount
                        rawUnit.startsWith("basis point") && quantityUnit == "percent" -> amount.movePointLeft(2)
                        unit(rawUnit) == quantityUnit -> amount
                        else -> return null
                    }
                    value = if (relative.groupValues[1] in setOf("increase","raise")) old.add(delta) else old.subtract(delta)
                }
                add != null -> {
                    val old = value ?: return null
                    val positive = add.groupValues[1].isNotEmpty()
                    val amount = decimal(add.groupValues[if (positive) 1 else 3]) ?: return null
                    val deltaUnit = unit(add.groupValues[if (positive) 2 else 4])
                    if (deltaUnit != quantityUnit || deltaUnit == "percent") return null
                    value = if (positive) old.add(amount) else old.subtract(amount)
                }
                else -> return null
            }
            if (value != null && value.abs() >= bound) return null
        }
        return Checked(value?.stripTrailingZeros()?.toPlainString() ?: "UNKNOWN", quantityUnit, statements.size)
    }

    fun report(checked: Checked, previous: String? = null): String? {
        val verdict = if (previous == null) "result: " + checked.result + "." else {
            val same = if (previous.equals("UNKNOWN", ignoreCase = true)) checked.result == "UNKNOWN"
            else {
                if (!resultNumber.matches(previous)) return null
                val actual = decimal(previous) ?: return null
                val expected = decimal(checked.result)
                expected != null && actual.compareTo(expected) == 0
            }
            if (same) "correct." else "incorrect; corrected result: " + checked.result + "."
        }
        return "INDEPENDENT QUANTITY CHECK — " + verdict +
            "\nRecomputed " + checked.steps + " explicit chronological statements; unit: " + checked.unit + ". " +
            "UNKNOWN withdraws the preceding amount; a later absolute replacement may establish it again. " +
            "Only the submitted quantity trace was verified, not the meaning of an arbitrary conversation."
    }
}
