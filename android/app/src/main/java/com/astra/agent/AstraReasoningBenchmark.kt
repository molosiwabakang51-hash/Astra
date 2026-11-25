package com.astra.agent

import java.math.RoundingMode
import java.util.Locale

/**
 * Eight independent, fixed-input checks for actual Qwen3 1.7B inference.
 *
 * This is NOT an IQ test, a 10/10 rating, nor a substitute for real conversation
 * and user-device tests. Exact or tightly normalized final answers keep a
 * verbose, contradictory or guessed explanation from inflating a score.
 * Questions contain no user notes, personal data, web requests or credentials.
 */
internal object AstraReasoningBenchmark {
    enum class Probe(
        val title: String,
        val prompt: String,
        val expected: String,
        val answerType: AnswerType
    ) {
        WEIGHTED_RETURN(
            "Weighted investment return",
            "An investment portfolio has 25% in an asset returning 12% and 75% " +
                "in an asset returning 4%. What is the portfolio return as a " +
                "percentage? Reply with only the final number and % sign.",
            "6", AnswerType.PERCENT
        ),
        COMPOUNDING(
            "Two-year compound growth",
            "A P2,000 deposit earns 8% annual compound interest for 2 years " +
                "with no additional payments. What is the final amount in pula? " +
                "Reply with only the numerical amount to two decimals.",
            "2332.80", AnswerType.MONEY
        ),
        BOND_PRESENT_VALUE(
            "One-year zero-coupon bond price",
            "A zero-coupon bond pays P100 after one year. The required annual " +
                "yield is 5%. What price today in pula gives that yield? " +
                "Reply with only the number rounded to two decimals.",
            "95.24", AnswerType.MONEY
        ),
        PROJECT_NPV(
            "Discounted cash-flow NPV",
            "A project costs P100 today and pays P60 in one year and P60 in " +
                "two years. At an annual 10% discount rate, what is its NPV " +
                "in pula? Reply with only the number to two decimals.",
            "4.13", AnswerType.MONEY
        ),
        PROBABILITY(
            "Probability without replacement",
            "A bag contains three red balls and two blue balls. Draw two " +
                "without replacement. What is the probability both are red " +
                "as a percentage? Reply with only the number and % sign.",
            "30", AnswerType.PERCENT
        ),
        ALGEBRA(
            "Two-step linear equation",
            "Solve 2x + 7 = 23 for x. Reply with only the number.",
            "8", AnswerType.NUMBER
        ),
        LOGICAL_DEDUCTION(
            "Constrained deduction",
            "All accountants in a fictional company are analysts. No analyst " +
                "is a robot. Under ONLY these rules, can any accountant in " +
                "that company be a robot? Reply YES or NO only.",
            "no", AnswerType.YES_NO
        ),
        FOREIGN_EXCHANGE_RISK(
            "Currency-risk reasoning",
            "A Botswana investor converts pula to US dollars and buys " +
                "US-dollar assets. Does holding US dollars completely eliminate " +
                "their exchange-rate risk when measured in pula? " +
                "Reply YES or NO only.",
            "no", AnswerType.YES_NO
        )
    }

    enum class AnswerType { PERCENT, MONEY, NUMBER, YES_NO }

    data class Outcome(
        val probe: Probe,
        val passed: Boolean,
        val milliseconds: Long,
        val sample: String
    )

    /** Do not credit raw model think tokens, snippets, quotes or reference footers. */
    internal fun normalizedAnswer(raw: String): String {
        val answer = AstraOfflinePolicy.publicReply(raw)
        val footer = Regex(
            """(?im)^\s*(?:Open-course references:|Retrieved local book passages(?: \(verify originals\))?:?)\s*$"""
        )
        return answer.substring(0, footer.find(answer)?.range?.first
            ?: answer.length).trim().lowercase(Locale.ROOT)
    }

    private val numericPattern = Regex(
        """^(p|bwp)?\s*([+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\.[0-9]+)?)\s*(%)?\s*[.]?$"""
    )

    fun score(probe: Probe, output: String): Boolean {
        if (AstraInterruptedReply.isIncomplete(output)) return false
        val answer = normalizedAnswer(output)
        if (answer.isBlank() || answer.length > 90) return false
        if (probe.answerType == AnswerType.YES_NO) {
            return answer.matches(Regex("""^(?:yes|no)[.!]?$""")) &&
                answer.trimEnd('.', '!') == probe.expected
        }
        val matched = numericPattern.matchEntire(answer) ?: return false
        if (probe.answerType != AnswerType.MONEY && matched.groupValues[1].isNotEmpty()) return false
        if (probe.answerType == AnswerType.PERCENT && matched.groupValues[3] != "%") {
            return false
        }
        if (probe.answerType != AnswerType.PERCENT && matched.groupValues[3].isNotEmpty()) {
            return false
        }
        val number = matched.groupValues[2].replace(",", "")
            .toBigDecimalOrNull() ?: return false
        val expected = probe.expected.toBigDecimal()
        // Two-decimal finance questions are graded at the precision requested.
        return if (probe.answerType == AnswerType.MONEY) {
            number.setScale(2, RoundingMode.HALF_UP).compareTo(expected) == 0
        } else number.compareTo(expected) == 0
    }

    fun outcome(probe: Probe, reply: String, milliseconds: Long): Outcome =
        Outcome(
            probe,
            score(probe, reply),
            milliseconds.coerceAtLeast(0L),
            normalizedAnswer(reply).replace(Regex("""\s+"""), " ").take(120)
        )

    internal fun verifiedModel(model: String?): Boolean =
        model != null && model == AstraModelChoice.loadedName(AstraModelImport.ADVANCED_NAME)

    internal const val UNSCORED = "UNVERIFIED MODEL OR INCOMPLETE RUN — NO SCORE."

    fun render(outcomes: List<Outcome>, model: String?): String {
        require(outcomes.size <= Probe.entries.size) { "Too many reasoning checks." }
        require(outcomes.map { it.probe }.distinct().size == outcomes.size) {
            "Duplicate reasoning checks cannot inflate a score."
        }
        if (!verifiedModel(model) || outcomes.size != Probe.entries.size) return UNSCORED
        val passed = outcomes.count { it.passed }
        return buildString {
            appendLine("QWEN3 ON-DEVICE REASONING CHECKS")
            appendLine("Loaded model: " + (model ?: "unverified"))
            appendLine("$passed/${outcomes.size} narrowly scored checks passed.")
            outcomes.forEach { result ->
                appendLine((if (result.passed) "PASS" else "UNVERIFIED") +
                    " — " + result.probe.title + " (${result.milliseconds} ms)")
                if (!result.passed) {
                    appendLine("Answer: " + result.sample.ifBlank { "<no usable answer>" })
                }
            }
            append("These prompts test specific offline math, finance and logic answers. " +
                "They do not establish general reasoning ability, a 10/10 AI rating, " +
                "or accuracy on open-ended conversation.")
        }
    }
}
