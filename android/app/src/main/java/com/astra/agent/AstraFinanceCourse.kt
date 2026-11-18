package com.astra.agent

import java.util.Locale

/**
 * A deliberately bounded, deterministic finance foundation course.
 *
 * Lessons, explanations and quizzes run in airplane mode without Qwen3 or
 * external material. The curriculum is education, not personal investment
 * advice, a live quotation, or a promise of financial outcomes.
 *
 * Only integers (visited/mastered lesson bitsets, last lesson and quiz scores)
 * are stored via Astra's existing local preferences. No new permissions,
 * remote calls, third-party data, accounts or GGUF weights.
 */
internal class AstraFinanceCourse(
    private val readInt: (String, Int) -> Int,
    private val writeInt: (String, Int) -> Unit
) {
    internal data class Lesson(
        val title: String,
        val explanation: String,
        val workedExample: String,
        val caution: String,
        val quiz: String,
        val choices: List<String>,
        val answer: Char,
        val feedback: String
    ) {
        init {
            require(choices.size == 3 && answer in 'A'..'C')
        }
    }

    companion object {
        private const val LAST = "astra_finance_last_lesson"
        private const val VISITED = "astra_finance_lessons_visited"
        private const val MASTERED = "astra_finance_quizzes_mastered"
        private const val PENDING = "astra_finance_pending_quiz"
        private const val CORRECT = "astra_finance_quiz_correct"
        private const val ATTEMPTS = "astra_finance_quiz_attempts"

        // 20 lesson flags fit in a signed Int without the sign bit.
        private const val VALID_MASK = 0x000FFFFF

        val lessons: List<Lesson> = listOf(
            Lesson(
                "Cash flow and financial goals",
                "Cash flow is money coming in and going out. A surplus can build an emergency fund or finance a goal; a deficit requires a spending or income adjustment.",
                "Monthly income P10,000 minus essential and discretionary spending P7,000 leaves P3,000 before any further savings.",
                "A budget is a plan, not proof that future income is certain.",
                "If income is P10,000 and spending P7,000, what is the surplus?",
                listOf("P3,000", "P7,000", "P17,000"), 'A',
                "Surplus = income − spending = P3,000."
            ),
            Lesson(
                "Simple interest",
                "Simple interest is charged on the original principal only. The formula is I = principal × annual rate × years.",
                "P1,000 × 10% × 3 years = P300 interest. Ending balance = P1,300 before fees and taxes.",
                "The rate and time units must agree; fees and taxes are excluded.",
                "What is the simple interest on P1,000 at 10% for 3 years?",
                listOf("P331", "P300", "P1,300"), 'B',
                "I = 1,000 × 0.10 × 3 = P300."
            ),
            Lesson(
                "Compound interest",
                "Compounding earns returns on both the principal and past returns. With annual compounding, FV = P × (1 + r)^t.",
                "P1,000 × 1.10³ = P1,331 after three years at 10% annually, assuming all interest is reinvested.",
                "Constant returns are an assumption, not a guarantee.",
                "What is P1,000 after 3 years at 10% compounded annually?",
                listOf("P1,300", "P1,210", "P1,331"), 'C',
                "The third year's return applies to P1,210: P1,331 total."
            ),
            Lesson(
                "Present value and discounting",
                "Present value expresses a future cash flow in today's money. PV = FV / (1 + r)^t, using a rate appropriate to the cash flow.",
                "P1,210 due in two years at a 10% annual discount rate has PV = 1,210 / 1.10² = P1,000.",
                "A higher discount rate generally lowers present value.",
                "At 10%, what is P1,210 received in two years worth today?",
                listOf("P1,000", "P1,210", "P1,464.10"), 'A',
                "Discount by 1.10² = 1.21 to get P1,000."
            ),
            Lesson(
                "Annuities and regular payments",
                "An ordinary annuity pays at the end of each period. Its present value is PMT × [1 − (1 + r)^(−n)] / r.",
                "Two end-of-year P1,000 payments discounted at 10% have a PV of about P1,735.54.",
                "Use a periodic rate matching payment frequency; annuity-due payments occur earlier.",
                "Are ordinary annuity payments made when?",
                listOf("At each period's start", "At each period's end", "Only at maturity"), 'B',
                "An ordinary annuity pays at the end of each period."
            ),
            Lesson(
                "Inflation and real return",
                "Inflation reduces purchasing power. Exact real return = (1 + nominal return) / (1 + inflation) − 1.",
                "An 8% nominal return with 5% inflation gives 1.08 / 1.05 − 1 ≈ 2.86% real.",
                "Do not subtract percentage changes when an exact calculation matters.",
                "At 8% nominal return and 5% inflation, the real return is closest to:",
                listOf("8%", "5%", "2.86%"), 'C',
                "(1.08 / 1.05) − 1 ≈ 2.86%."
            ),
            Lesson(
                "Risk and diversification",
                "Diversification spreads exposure among investments whose returns are not perfectly correlated. It can reduce company-specific risk, not eliminate market-wide risk.",
                "Holding companies in different industries avoids concentrating every loss in one issuer, although both can still fall in a market downturn.",
                "More holdings do not automatically mean good diversification when exposures overlap.",
                "Which risk cannot diversification eliminate?",
                listOf("Broad market risk", "One company's fraud risk", "Single-company concentration"), 'A',
                "Diversification can reduce issuer-specific risk, not systemic market risk."
            ),
            Lesson(
                "Portfolio weighted returns",
                "The portfolio return over one period is the sum of each asset weight multiplied by that asset's return.",
                "40% invested at 10% and 60% at 5% gives 0.40 × 10% + 0.60 × 5% = 7%.",
                "Weights and returns must refer to the same period and valuation basis.",
                "What is the weighted return for 40% at 10% and 60% at 5%?",
                listOf("15%", "7%", "6%"), 'B',
                "4 percentage points + 3 percentage points = 7%."
            ),
            Lesson(
                "Shares and equity valuation",
                "A share represents an ownership interest. One simplified dividend-growth model uses value = next year's dividend / (required return − expected growth).",
                "If D1 = P1.05, required return = 8% and perpetual dividend growth = 3%, the model gives P1.05 / 0.05 = P21.",
                "This model requires required return above growth, stable long-run assumptions and sustainable dividends.",
                "If required return is 8% and perpetual growth 3%, the denominator is:",
                listOf("11%", "8%", "5%"), 'C',
                "Subtract growth from the required return: 8% − 3% = 5%."
            ),
            Lesson(
                "Dividends and total shareholder return",
                "Dividend yield = annual dividend per share / share price. Total return also includes changes in share price and, where applicable, reinvested distributions.",
                "An annual dividend of P5 and a share price of P100 produce a 5% dividend yield.",
                "High yields can reflect a falling price; dividends are never guaranteed.",
                "A P5 annual dividend on a P100 share produces what yield?",
                listOf("5%", "20%", "0.5%"), 'A',
                "5 ÷ 100 × 100% = 5%."
            ),
            Lesson(
                "Bond prices, coupons and yields",
                "Bonds promise specified cash flows subject to issuer/default risk. Coupon rate uses face value; current yield uses market price; yield to maturity discounts all promised cash flows.",
                "A P1,000 face-value bond paying P50 a year has a 5% coupon rate. At a P950 price its current yield is about 5.26%, not its exact YTM.",
                "Price and yield generally move in opposite directions for a conventional fixed-rate bond.",
                "A P50 annual coupon on P1,000 face value gives what coupon rate?",
                listOf("5.26%", "5%", "10%"), 'B',
                "Coupon rate = 50 / 1,000 = 5%; current yield at P950 differs."
            ),
            Lesson(
                "Bond duration",
                "Macaulay duration measures weighted average payment timing. Modified duration approximates the percentage price change for a small yield shift: ΔP/P ≈ −Dmod × Δy.",
                "Modified duration 5 and a 1-percentage-point yield increase imply roughly a 5% price fall before convexity.",
                "Duration is a local approximation, not maturity or an exact large-shock forecast.",
                "If modified duration is 5 and yield rises 1 percentage point, estimated price change is:",
                listOf("About −5%", "About +5%", "Exactly 0%"), 'A',
                "−5 × 0.01 = −0.05, or approximately −5%."
            ),
            Lesson(
                "Bond convexity",
                "Convexity adjusts duration's linear price–yield approximation: ΔP/P ≈ −Dmod Δy + ½ C (Δy)².",
                "With Dmod = 5, convexity = 40 and Δy = +0.01, the approximation is −5% + 0.2% = −4.8%.",
                "Some callable or option-embedded bonds have different convexity behavior.",
                "For this plain positive-convexity example, the convexity adjustment is:",
                listOf("−0.2%", "+0.2%", "+20%"), 'B',
                "½ × 40 × 0.01² = 0.002 = +0.2%."
            ),
            Lesson(
                "Credit and liquidity risk",
                "Credit risk is a borrower's failure to meet obligations. Liquidity risk is the cost or difficulty of selling quickly at a reasonable price.",
                "Two bonds can have similar maturities but different yields because their credit quality, trading liquidity and other risks differ.",
                "A quoted higher yield does not establish a superior risk-adjusted investment.",
                "Which describes liquidity risk?",
                listOf("Inflation rising", "A coupon being paid", "Difficulty selling promptly at a fair price"), 'C',
                "Thin trading can cause a wide bid-ask spread or forced-sale discount."
            ),
            Lesson(
                "Project investment and NPV",
                "Net present value (NPV) adds discounted expected cash inflows and subtracts the initial cost. At a suitable hurdle rate, a positive NPV suggests value creation under the assumptions.",
                "Pay P1,000 now and receive P600 after one year and P600 after two years. At 10%, NPV = −1,000 + 600/1.10 + 600/1.10² ≈ +P41.32.",
                "NPV depends on realistic cash flows, timing and discount rates; the result is not guaranteed.",
                "For the example's assumed cash flows and 10% rate, NPV is:",
                listOf("Approximately +P41.32", "Exactly P200", "Approximately −P200"), 'A',
                "The discounted cash flows total approximately P1,041.32."
            ),
            Lesson(
                "Currency exposure and foreign bonds",
                "A foreign-currency investment has asset-return risk and exchange-rate risk. Home-currency gross return = (1 + foreign-asset return) × (1 + currency change) − 1.",
                "A USD asset gains 5%, but the dollar loses 10% against your home currency: 1.05 × 0.90 − 1 = −5.5% before fees.",
                "Simply converting into USD does not eliminate currency risk or country-specific broker restrictions.",
                "A 5% USD gain with a 10% USD fall against home currency means:",
                listOf("+15.5% home return", "+5% home return", "−5.5% home return"), 'C',
                "Currency translation multiplies returns: 1.05 × 0.90 − 1 = −5.5%."
            ),
            Lesson(
                "Leverage and margin",
                "Leverage uses borrowed capital to increase exposure. It magnifies both gains and losses relative to the investor's own funds.",
                "Invest P10,000 of your own funds and borrow P10,000. If the P20,000 position falls 10%, equity falls to P8,000: a 20% loss before costs.",
                "Borrowing costs, margin calls and forced liquidation can make losses worse.",
                "A 10% asset loss at 2× initial gross exposure causes approximately what equity loss, ignoring costs?",
                listOf("5%", "20%", "10%"), 'B',
                "A P2,000 asset loss divided by P10,000 initial equity = 20%."
            ),
            Lesson(
                "Day-trading risk controls",
                "Before entering a hypothetical trade, define entry, stop, maximum acceptable loss and exposure. Position size = cash risk budget / risk per unit.",
                "At a P10,000 account value, 1% risk is P100. With P2 price risk per share, a simple position size is 50 shares.",
                "Stops are not guaranteed execution prices; gaps, fees and slippage can exceed the plan.",
                "With a P100 risk budget and P2 planned risk per share, size is:",
                listOf("200 shares", "100 shares", "50 shares"), 'C',
                "P100 / P2 = 50 shares before fees, gaps and other constraints."
            ),
            Lesson(
                "Retirement and long-term planning",
                "A long-term savings plan combines contributions, expected growth, inflation, costs, time horizon and sequence-of-returns risk. Revisit assumptions as circumstances change.",
                "Saving P500 monthly deposits P6,000 over twelve months before investment returns and fees. Its future value requires a chosen contribution timing and assumed rate.",
                "A single average return does not describe the timing of losses close to retirement.",
                "What is 12 monthly contributions of P500 before returns?",
                listOf("P6,000", "P500", "P12,000"), 'A',
                "12 × P500 = P6,000 in contributions."
            ),
            Lesson(
                "Stress testing and drawdowns",
                "Stress tests ask how a portfolio behaves under adverse scenarios. Drawdown is a percentage fall from a previous value; recovery percentages are asymmetric.",
                "A P100,000 portfolio falling 20% becomes P80,000. Recovering to P100,000 then requires a 25% gain, not 20%.",
                "Scenario analysis explores possible losses; it does not assign reliable probabilities without supporting data.",
                "After a 20% loss, what gain restores the original value?",
                listOf("20%", "25%", "40%"), 'B',
                "P20,000 / P80,000 = 25%."
            )
        )
    }

    fun respond(raw: String): String? {
        val command = raw.trim().lowercase(Locale.ROOT)
            .trimEnd('?', '!', '.').trim()
        if (command.length > 90) return null
        return when (command) {
            "finance course", "finance syllabus", "learn finance",
            "start finance course" -> syllabus()
            "finance progress", "my finance progress" -> progress()
            "next finance lesson" -> lesson(
                (readInt(LAST, 0) + 1).coerceIn(1, lessons.size)
            )
            "finance quiz", "quiz me on finance" -> quiz(
                readInt(LAST, 1).coerceIn(1, lessons.size)
            )
            "skip finance quiz" -> {
                if (readInt(PENDING, 0) !in 1..lessons.size) {
                    "No finance quiz is active. Say 'finance quiz' to start one."
                } else {
                    writeInt(PENDING, 0)
                    "Finance quiz skipped. Choose 'finance quiz' to retry."
                }
            }
            else -> when {
                command.startsWith("answer finance") -> answer(command)
                command.startsWith("finance lesson ") ->
                    numbered(command.removePrefix("finance lesson ")) { lesson(it) }
                command.startsWith("finance quiz ") ->
                    numbered(command.removePrefix("finance quiz ")) { quiz(it) }
                else -> null
            }
        }
    }

    private fun numbered(raw: String, handler: (Int) -> String): String {
        val number = raw.toIntOrNull()
        return if (number != null && number in 1..lessons.size)
            handler(number) else "Choose a Finance lesson from 1 to ${lessons.size}. " +
            "Say 'finance course' for the syllabus."
    }

    private fun syllabus(): String = buildString {
        appendLine("FINANCE FOUNDATION · ${lessons.size} OFFLINE LESSONS")
        appendLine("No model, live prices or internet required.")
        lessons.forEachIndexed { index, item ->
            appendLine("${index + 1}. ${item.title}")
        }
        appendLine()
        append("Choose 'finance lesson 1', 'next finance lesson', " +
            "'finance quiz 1', or 'finance progress'.")
    }.trim()

    private fun lesson(index: Int): String {
        val item = lessons[index - 1]
        writeInt(LAST, index)
        writeInt(VISITED, readInt(VISITED, 0) or (1 shl (index - 1)))
        return buildString {
            appendLine("FINANCE LESSON $index/${lessons.size} · ${item.title.uppercase(Locale.ROOT)}")
            appendLine("OFFLINE FOUNDATION")
            appendLine()
            appendLine(item.explanation)
            appendLine()
            appendLine("WORKED EXAMPLE")
            appendLine(item.workedExample)
            appendLine()
            appendLine("CHECK THE ASSUMPTIONS")
            appendLine(item.caution)
            appendLine()
            append("Practice: 'finance quiz $index'. " +
                if (index < lessons.size) "Continue: 'finance lesson ${index + 1}'." else
                    "Course complete: 'finance progress'.")
        }.trim()
    }

    private fun quiz(index: Int): String {
        val item = lessons[index - 1]
        writeInt(PENDING, index)
        return buildString {
            appendLine("FINANCE QUIZ $index/${lessons.size} · ${item.title}")
            appendLine(item.quiz)
            item.choices.forEachIndexed { i, choice ->
                appendLine("${('A'.code + i).toChar()}. $choice")
            }
            append("Reply 'answer finance A', B or C; or 'skip finance quiz'.")
        }
    }

    private fun answer(command: String): String {
        val active = readInt(PENDING, 0)
        if (active !in 1..lessons.size) {
            return "No finance quiz is active. Say 'finance quiz 1' or 'finance quiz'."
        }
        val choice = command.removePrefix("answer finance").trim().uppercase(Locale.ROOT)
        if (choice !in listOf("A", "B", "C")) {
            return "Choose A, B or C using 'answer finance A'."
        }
        val item = lessons[active - 1]
        val correct = choice[0] == item.answer
        writeInt(PENDING, 0)
        writeInt(ATTEMPTS, readInt(ATTEMPTS, 0).coerceIn(0, 1_000_000) + 1)
        if (correct) {
            writeInt(CORRECT, readInt(CORRECT, 0).coerceIn(0, 1_000_000) + 1)
            writeInt(MASTERED, readInt(MASTERED, 0) or (1 shl (active - 1)))
        }
        return (if (correct) "Correct." else "Not quite. Correct answer: ${item.answer}.") +
            " ${item.feedback}\n" +
            "Try 'finance quiz $active' to review, or 'finance progress' to see results."
    }

    private fun progress(): String {
        val visited = Integer.bitCount(readInt(VISITED, 0) and VALID_MASK)
        val mastered = Integer.bitCount(readInt(MASTERED, 0) and VALID_MASK)
        val last = readInt(LAST, 0).coerceIn(0, lessons.size)
        val attempts = readInt(ATTEMPTS, 0).coerceAtLeast(0)
        val correct = readInt(CORRECT, 0).coerceIn(0, attempts)
        return "FINANCE COURSE PROGRESS · OFFLINE\n" +
            "Lessons opened: $visited/${lessons.size}. " +
            "Lesson quizzes mastered: $mastered/${lessons.size}.\n" +
            "Quiz answers: $correct/$attempts correct. " +
            "Last opened: ${if (last == 0) "none" else "$last/${lessons.size}"}.\n" +
            "Say 'finance course' to see the syllabus or 'next finance lesson' to continue."
    }
}
