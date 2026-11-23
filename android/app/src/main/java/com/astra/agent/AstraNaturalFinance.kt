package com.astra.agent

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/**
 * Deterministic offline maths for naturally phrased lump-sum finance questions.
 *
 * The small LLM is not asked to invent results. Parsing is intentionally
 * conservative: if amount, rate, period or cash-flow assumptions are unclear
 * we never silently produce a misleading number.
 */
internal object AstraNaturalFinance {
    enum class Operation { FUTURE_VALUE, PRESENT_VALUE, DOUBLING_TIME }

    data class Input(
        val operation: Operation,
        val amount: Double?,
        val currency: String,
        val annualPercent: Double,
        val years: Double?,
        val compoundsPerYear: Int,
        val frequencyWasSpecified: Boolean
    )

    private val moneyPattern = Regex(
        """(?i)(?<![\p{L}\p{N}])(?:(BWP|P|ZAR|R|USD|GBP|EUR)\s*|([$£€])\s*)""" +
            """(\d+(?:,\d{3})*(?:\.\d+)?)(?![\d,])"""
    )
    private val annualPercentPattern = Regex(
        """(?<![\d.])(-?\d+(?:\.\d+)?)\s*%(?!\s*(?:per\s+month|monthly|per\s+week|weekly|per\s+day|daily))""",
        RegexOption.IGNORE_CASE
    )
    private val annualPeriodPattern = Regex(
        """(?<![\d.])(\d+(?:\.\d+)?)\s*(?:years?|yrs?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val spelledPeriodPattern = Regex(
        """\b(one|two|three|four|five|six|seven|eight|nine|ten)\s+years?\b""",
        RegexOption.IGNORE_CASE
    )
    private val spelledPeriodYears = mapOf(
        "one" to 1.0, "two" to 2.0, "three" to 3.0,
        "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "seven" to 7.0, "eight" to 8.0, "nine" to 9.0,
        "ten" to 10.0
    )
    private val doublingIntent = Regex(
        """\b(?:how long\b.{0,70}\bdouble\b|time to double\b|years to double\b|""" +
            """when (?:will|would|does)\b.{0,70}\bdouble\b)""",
        RegexOption.IGNORE_CASE
    )
    private val presentValueIntent = Regex(
        """\b(?:present value|how much (?:do i need to|would i need to|should i|must i) (?:invest|deposit|save)|""" +
            """invest today to (?:reach|have)|need to invest to (?:reach|have))\b""",
        RegexOption.IGNORE_CASE
    )
    private val futureValueIntent = Regex(
        """\b(?:compound(?:ing)? interest|compounded|compounding|invest|investment|deposit|""" +
            """grow|grows|growth|future value|worth|accumulate|savings|save|mature|maturity)\b""",
        RegexOption.IGNORE_CASE
    )
    private val explicitFinanceQuestion = Regex(
        """^(?:if\b|what\b|how\b|calculate\b|compute\b|estimate\b|project\b|""" +
            """show\b|suppose\b|i (?:invest|save|deposit)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val monthlyCashflow = Regex(
        """\b(?:monthly contributions?|regular contributions?|additional (?:monthly )?deposits?|""" +
            """deposit (?:each|every) month|(?:deposit|save|invest|add|contribute)\s+[^ ]{1,30}\s+(?:each|every) month|""" +
            """add money (?:each|every) month|withdrawals?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val monthlyRate = Regex(
        """(?:-?\d+(?:\.\d+)?\s*%\s*(?:per\s+month|monthly|per\s+week|weekly|per\s+day|daily))""",
        RegexOption.IGNORE_CASE
    )
    private val currencyNames = mapOf(
        "BWP" to "P", "P" to "P", "ZAR" to "R", "R" to "R",
        "USD" to "$", "$" to "$", "GBP" to "£", "£" to "£",
        "EUR" to "€", "€" to "€"
    )

    fun parse(raw: String): Input? {
        val clean = raw.trim().replace(Regex("""\s+"""), " ")
        if (clean.isEmpty() || clean.length > 260) return null
        if (!explicitFinanceQuestion.containsMatchIn(clean) &&
            !clean.startsWith("compound interest", ignoreCase = true)
        ) return null

        val doubling = doublingIntent.containsMatchIn(clean)
        val presentValue = !doubling && presentValueIntent.containsMatchIn(clean)
        if (!doubling && !presentValue && !futureValueIntent.containsMatchIn(clean)) {
            return null
        }
        // Keep natural questions about monthly savings/withdrawals with
        // periodic cash flows out of the lump-sum calculator.
        if (monthlyCashflow.containsMatchIn(clean) || monthlyRate.containsMatchIn(clean)) {
            return null
        }

        val rates = annualPercentPattern.findAll(clean).toList()
        if (rates.size != 1) return null
        val rate = rates.single().groupValues[1].toDoubleOrNull() ?: return null
        if (!rate.isFinite() || rate <= -100.0 || rate > 1000.0) return null

        val amounts = moneyPattern.findAll(clean).toList()
        if (amounts.size > 1 || (!doubling && amounts.size != 1)) return null
        val amountMatch = amounts.singleOrNull()
        val amount = amountMatch?.groupValues?.get(3)?.replace(",", "")?.toDoubleOrNull()
        if (amountMatch != null && (amount == null || !amount.isFinite() ||
                    amount < 0 || amount > 1_000_000_000_000_000.0)) return null

        val periodMatches = annualPeriodPattern.findAll(clean).toList()
        val spelledPeriods = spelledPeriodPattern.findAll(clean).toList()
        // "received in one year" is a complete, unambiguous cash-flow
        // period. Never guess among conflicting numeric/verbal periods.
        if (!doubling && periodMatches.size + spelledPeriods.size != 1) return null
        if (doubling && (periodMatches.isNotEmpty() || spelledPeriods.isNotEmpty())) return null
        val years = if (spelledPeriods.isNotEmpty()) {
            spelledPeriodYears[spelledPeriods.single().groupValues[1]
                .lowercase(Locale.ROOT)]
        } else periodMatches.singleOrNull()?.groupValues?.get(1)?.toDoubleOrNull()
        if (!doubling && (years == null || !years.isFinite() || years < 0 || years > 150)) return null

        val lower = clean.lowercase(Locale.ROOT)
        val frequency = when {
            Regex("""\b(?:semi[- ]?annual(?:ly)?|twice (?:per |a )year|half[- ]yearly)\b""").containsMatchIn(lower) -> 2
            Regex("""\b(?:quarterly|every quarter)\b""").containsMatchIn(lower) -> 4
            Regex("""\b(?:monthly|every month)\b""").containsMatchIn(lower) -> 12
            Regex("""\b(?:weekly|every week)\b""").containsMatchIn(lower) -> 52
            Regex("""\b(?:daily|every day)\b""").containsMatchIn(lower) -> 365
            Regex("""\b(?:annually|annual compounding|yearly|every year|per year)\b""").containsMatchIn(lower) -> 1
            else -> 1
        }
        val wasSpecified = Regex(
            """\b(?:compounded|compounding|annually|yearly|quarterly|monthly|weekly|daily|every quarter|every month|semi[- ]?annual)\b"""
        ).containsMatchIn(lower)
        val currencyKey = amountMatch?.let {
            it.groupValues[1].ifBlank { it.groupValues[2] }.uppercase(Locale.ROOT)
        }
        val symbol = currencyNames[currencyKey] ?: "P"
        return Input(
            operation = if (doubling) Operation.DOUBLING_TIME else
                if (presentValue) Operation.PRESENT_VALUE else Operation.FUTURE_VALUE,
            amount = amount,
            currency = symbol,
            annualPercent = rate,
            years = years,
            compoundsPerYear = frequency,
            frequencyWasSpecified = wasSpecified
        )
    }

    fun respond(raw: String): String? {
        val data = parse(raw) ?: return null
        val n = data.compoundsPerYear
        val rate = data.annualPercent / 100.0
        val base = 1.0 + rate / n
        if (base <= 0.0) return "The stated rate is not valid for this compounding frequency."

        val frequencyLabel = when (n) {
            1 -> "annually"; 2 -> "semi-annually"; 4 -> "quarterly"
            12 -> "monthly"; 52 -> "weekly"; else -> "daily"
        } + if (data.frequencyWasSpecified) "" else " (assumed)"
        val header = when (data.operation) {
            Operation.DOUBLING_TIME -> "DOUBLING TIME · CALCULATED OFFLINE"
            Operation.PRESENT_VALUE -> "PRESENT VALUE · CALCULATED OFFLINE"
            Operation.FUTURE_VALUE -> "COMPOUND GROWTH · CALCULATED OFFLINE"
        }
        val context = buildString {
            appendLine(header)
            appendLine("Nominal annual rate: ${display(data.annualPercent, 2)}%")
            appendLine("Compounding: $frequencyLabel")
        }

        return when (data.operation) {
            Operation.DOUBLING_TIME -> {
                if (rate <= 0.0) return "A non-positive return cannot double a positive lump sum through compound growth."
                val result = ln(2.0) / (n * ln(base))
                if (!result.isFinite()) return "This rate produces no finite doubling-time estimate."
                buildString {
                    append(context)
                    if (data.amount != null) {
                        appendLine("Starting amount: ${money(data.currency, data.amount)}")
                        appendLine("Doubled amount: ${money(data.currency, data.amount * 2)}")
                    }
                    appendLine("Estimated time to double: ${display(result, 2)} years")
                    appendLine("Formula: t = ln(2) / [n × ln(1 + r/n)]")
                    append("Assumes a constant nominal rate and reinvested interest; ignores tax, fees and inflation.")
                }.trimEnd()
            }
            Operation.FUTURE_VALUE, Operation.PRESENT_VALUE -> {
                val amount = data.amount ?: return null
                val years = data.years ?: return null
                val factor = base.pow(n.toDouble() * years)
                if (!factor.isFinite() || factor <= 0.0) {
                    return "The amount cannot be calculated reliably for these inputs."
                }
                val answer = if (data.operation == Operation.FUTURE_VALUE)
                    amount * factor else amount / factor
                if (!answer.isFinite() || answer > 1e18) {
                    return "The amount exceeds this calculator's safe numeric range."
                }
                buildString {
                    append(context)
                    appendLine("Period: ${display(years, if (years % 1.0 == 0.0) 0 else 2)} years")
                    if (data.operation == Operation.FUTURE_VALUE) {
                        appendLine("Starting amount: ${money(data.currency, amount)}")
                        appendLine("Future value: ${money(data.currency, answer)}")
                        appendLine("Growth / interest: ${money(data.currency, answer - amount)}")
                        appendLine("Formula: FV = P × (1 + r/n)^(n × t)")
                    } else {
                        appendLine("Future target: ${money(data.currency, amount)}")
                        appendLine("Amount needed today: ${money(data.currency, answer)}")
                        appendLine("Formula: PV = FV / (1 + r/n)^(n × t)")
                    }
                    append("Lump sum only; assumes constant nominal annual rate, no extra deposits, fees, taxes or inflation.")
                }.trimEnd()
            }
        }
    }

    private fun money(currency: String, amount: Double): String =
        currency + display(amount, 2)

    private fun display(amount: Double, decimals: Int): String =
        String.format(Locale.US, "%,.${decimals}f", amount)
}
