package com.astra.agent

import java.util.Locale

/**
 * A fully offline, educational day-trading foundations curriculum. Lessons and quiz answers are
 * original authored examples, not price predictions, trading advice or live market data.
 * Only small, subject-isolated integer counters/bitsets persist in Astra preferences.
 * No internet, third-party service, bundled model or file access is required.
 */
internal class AstraTradingCourse(
    private val readInt: (String, Int) -> Int,
    private val writeInt: (String, Int) -> Unit
) {
    internal data class Lesson(
        val title: String,
        val concept: String,
        val workedExample: String,
        val note: String,
        val question: String,
        val choices: List<String>,
        val answer: Char,
        val explanation: String
    ) {
        init {
            require(choices.size == 3 && answer in 'A'..'C')
            require(title.isNotBlank() && question.isNotBlank())
        }
    }

    companion object {
        private const val LAST = "astra_trading_last_lesson"
        private const val VISITED = "astra_trading_lessons_visited"
        private const val MASTERED = "astra_trading_quizzes_mastered"
        private const val PENDING = "astra_trading_pending_quiz"
        private const val ATTEMPTS = "astra_trading_quiz_attempts"
        private const val CORRECT = "astra_trading_quiz_correct"

        val lessons: List<Lesson> = listOf(
            Lesson(
                "What day trading involves",
                "Day trading opens and closes positions within a trading day. Stocks, currencies, futures and other products have different contracts, liquidity and risk.",
                "A stock order and a futures order can represent very different exposures, even if both prices move by 1%.",
                "This course explains mechanisms using hypothetical examples; it does not recommend trades.",
                "Which statement is accurate?",
                listOf("All markets share the same contract rules", "Day trading involves intraday positions but different products have different risks", "A 1% move always means the same currency loss"), 'B',
                "Position size, contract specifications and leverage determine monetary exposure."
            ),
            Lesson(
                "Bid, ask and spread",
                "The bid is the price a buyer offers; the ask is the price a seller requests. The spread is ask minus bid.",
                "With bid P99.95 and ask P100.05, the quoted spread is P0.10 per share.",
                "Spreads can widen during low liquidity or news; trading costs are not only commissions.",
                "If bid is 99.95 and ask 100.05, what is the spread?",
                listOf("0.10", "100.00", "0.05"), 'A',
                "Ask minus bid = 100.05 − 99.95 = 0.10."
            ),
            Lesson(
                "Market, limit and stop orders",
                "A market order prioritizes execution rather than price; a limit order sets a price boundary but may not fill. A stop order generally activates after a trigger and may execute away from the stop.",
                "A buy limit at P100 normally will not execute above P100, but can remain unfilled.",
                "Order handling depends on the venue and broker; a stop-loss is not a guaranteed exit price.",
                "Which order gives a price bound but may remain unfilled?",
                listOf("Market order", "Stop-market order", "Limit order"), 'C',
                "A limit order constrains price but does not guarantee execution."
            ),
            Lesson(
                "Liquidity and slippage",
                "Liquidity describes how readily an asset trades without substantial price impact. Slippage is the difference between an expected price and the actual fill.",
                "An intended P50.00 market purchase executed at P50.12 has P0.12 adverse slippage per share before fees.",
                "Displayed quotes may change before execution, especially for large orders or thin markets.",
                "What is adverse slippage on a buy expected at 50.00 filled at 50.12?",
                listOf("P0.12 per share", "P0.00", "P12.00 per share"), 'A',
                "50.12 − 50.00 = 0.12 extra cost per share."
            ),
            Lesson(
                "Reading candlestick OHLC",
                "A candlestick displays open, high, low and close for one interval. A close above the open is often shown as an upward candle.",
                "An interval opens at 10, reaches 12, falls to 9 and closes at 11; the body spans 10 to 11 and the full range spans 9 to 12.",
                "One candle does not reliably predict the next move; chart conventions vary.",
                "A candle opening at 10 and closing at 11 indicates:",
                listOf("The price never traded below 10", "The interval closed above its open", "The next candle must rise"), 'B',
                "11 is greater than the opening price of 10."
            ),
            Lesson(
                "Timeframes and volatility",
                "Short timeframes contain more price fluctuations and noise. Volatility summarizes how variable prices are, not whether a trade will succeed.",
                "A 1-minute chart has more observations within a session than an hourly chart; each can show a different-looking pattern.",
                "Do not compare volatility estimates with mismatched time windows or instruments.",
                "Which measure describes the size or variability of price movements?",
                listOf("Dividend tax", "Account username", "Volatility"), 'C',
                "Volatility measures variation, not a guaranteed direction."
            ),
            Lesson(
                "Trends and support or resistance",
                "A trend describes observed price direction. Support and resistance are zones where trading activity has sometimes slowed declines or advances.",
                "If higher highs and higher lows appear in historical prices, analysts may describe an uptrend.",
                "Historical patterns can fail; support and resistance are not physical barriers.",
                "Which is most consistent with an observed uptrend?",
                listOf("Higher highs and higher lows", "A guaranteed price increase tomorrow", "Only downward closing prices"), 'A',
                "Higher highs and higher lows are a common descriptive trend definition."
            ),
            Lesson(
                "Volume and volume interpretation",
                "Volume counts contracts, shares or units traded over an interval. Increased volume can signal activity, but does not itself establish direction.",
                "A stock traded 1,000 shares in one interval and 5,000 in the next; activity increased fivefold.",
                "Volume data coverage differs by market, venue and instrument.",
                "What does higher traded volume alone prove?",
                listOf("The asset is undervalued", "More units traded in that interval", "The next price move will be up"), 'B',
                "Volume describes activity, not certain future returns."
            ),
            Lesson(
                "Risk budgets and position size",
                "A planned risk budget limits the money exposed to a defined price move. A simplified position size equals risk budget divided by planned loss per unit.",
                "For a hypothetical P10,000 account, 1% is P100. A P2 per-share risk implies 50 shares before costs.",
                "Stops may fill worse than planned and total loss may exceed the budget in gaps or failures.",
                "With P100 risk budget and P2 planned risk per share, what is the simplified size?",
                listOf("200 shares", "25 shares", "50 shares"), 'C',
                "P100 divided by P2 equals 50 shares before fees and slippage."
            ),
            Lesson(
                "Stop-losses and price gaps",
                "A stop-loss is an instruction with a trigger, not a guarantee of a precise fill. A gap can skip the trigger price and create a larger loss.",
                "An intended stop at P95 may execute at P92 during a sharp price gap, depending on order conditions.",
                "Market closures, trading halts and leveraged products can make losses much larger than a simple stop estimate.",
                "Does a standard stop-market order guarantee execution at its stop price?",
                listOf("No, gaps and slippage can change the fill", "Yes, at every venue", "Yes, if market volume is high"), 'A',
                "A stop-market becomes a market order after triggering and may execute at a worse price."
            ),
            Lesson(
                "Reward-to-risk and R multiples",
                "An R multiple expresses profit or loss relative to a planned initial monetary risk. A 2R target represents twice that risk, not twice the capital.",
                "If the initial planned risk is P50, a hypothetical P100 profit is +2R before fees.",
                "Projected reward-to-risk does not measure the probability of success.",
                "A hypothetical P100 gain on a P50 initial risk equals:",
                listOf("+0.5R", "+2R", "+50R"), 'B',
                "100 divided by 50 = 2R."
            ),
            Lesson(
                "Leverage and margin",
                "Leverage creates market exposure larger than the money committed. It magnifies price changes relative to equity and can trigger margin calls or liquidation.",
                "At 5× exposure, a 2% adverse price move is about a 10% equity loss before costs, if the full exposure remains.",
                "Financing, maintenance margin and broker liquidation rules vary; losses can exceed initial margin.",
                "At 5× exposure, what approximate equity loss follows a 2% adverse price move before costs?",
                listOf("2%", "1%", "10%"), 'C',
                "5 × 2% = 10% of committed equity before fees and margin effects."
            ),
            Lesson(
                "Costs and trading expectancy",
                "A simple expectancy estimate multiplies each outcome by its probability, then sums outcomes. Subtract fees, spreads and slippage for net results.",
                "If 40% of trades win 3R and 60% lose 1R, gross expectancy is 0.4×3 − 0.6×1 = +0.6R per trade.",
                "Estimates are fragile with small samples and changing market conditions; positive expectancy is not a guarantee.",
                "With 40% wins at +3R and 60% losses at −1R, gross expectancy is:",
                listOf("0.6R", "2.0R", "−0.6R"), 'A',
                "0.4 × 3 − 0.6 × 1 = +0.6R, before transaction costs."
            ),
            Lesson(
                "Journals and performance review",
                "A trade journal records the setup, planned entry and exit, size, rationale, actual fills, costs and results. Review average loss, drawdown and rule adherence.",
                "A record showing a planned P50 risk and actual P90 loss highlights a gap between plan and execution worth investigating.",
                "Do not share sensitive account credentials or personal trading data in public logs.",
                "Which is most useful for analyzing trading decisions?",
                listOf("Only recording winning trades", "Recording plans, fills, costs and rule adherence", "Deleting all losing trades from the sample"), 'B',
                "Complete and accurate records permit better error analysis."
            ),
            Lesson(
                "Backtesting and overfitting",
                "Backtesting evaluates historical rules, not future success. Out-of-sample tests help detect overfitting; look-ahead bias uses information unavailable at the time.",
                "A strategy designed and optimized using 2023 data can be checked on later untouched data, provided the rules remain fixed.",
                "Include realistic trading costs, changing liquidity and survivorship bias in any evaluation.",
                "Using tomorrow's closing price to decide today's hypothetical trade is:",
                listOf("A sound real-time strategy", "Diversification", "Look-ahead bias"), 'C',
                "The future closing price is information that was not available when the decision was made."
            ),
            Lesson(
                "Market integrity and trading discipline",
                "False tips, manipulated prices, unverified promises and impulsive risk escalation can damage accounts. Confirm venue rules and regulatory obligations before trading.",
                "An anonymous message promising guaranteed daily profits should be treated as an unverified warning sign, not evidence of a reliable edge.",
                "Day trading can lose substantial money. Educational study and paper simulations do not establish live profitability.",
                "Which statement best reflects prudent evaluation?",
                listOf("Guaranteed-profit claims require skepticism and independent verification", "Every social-media tip is reliable", "Borrowing more always reduces trading risk"), 'A',
                "Guaranteed returns are not credible assurances; verify sources and understand the product and losses."
            )
        )
        private const val VALID_MASK = 0x0000FFFF
    }

    fun respond(raw: String): String? {
        val command = raw.trim().lowercase(Locale.ROOT).trimEnd('?', '!', '.').trim()
        if (command.length > 90) return null
        return when (command) {
            "trading course", "day trading course", "trading syllabus", "learn trading",
            "learn day trading", "teach me day trading", "start trading course" -> syllabus()
            "trading progress", "my trading progress" -> progress()
            "next trading lesson" -> lesson((readInt(LAST, 0) + 1).coerceIn(1, lessons.size))
            "trading quiz", "quiz me on trading" -> quiz(readInt(LAST, 1).coerceIn(1, lessons.size))
            "skip trading quiz" -> {
                if (readInt(PENDING, 0) !in 1..lessons.size) {
                    "No Trading quiz is active. Say 'trading quiz' to start one."
                } else {
                    writeInt(PENDING, 0)
                    "Trading quiz skipped. Say 'trading quiz' to retry."
                }
            }
            else -> when {
                command.startsWith("answer trading") -> answer(command)
                command.startsWith("trading lesson ") ->
                    numbered(command.removePrefix("trading lesson ")) { lesson(it) }
                command.startsWith("trading quiz ") ->
                    numbered(command.removePrefix("trading quiz ")) { quiz(it) }
                else -> null
            }
        }
    }

    private fun numbered(raw: String, handler: (Int) -> String): String {
        val number = raw.toIntOrNull()
        return if (number != null && number in 1..lessons.size)
            handler(number) else
            "Choose a trading lesson from 1 to ${lessons.size}; say 'trading course' for the syllabus."
    }

    private fun syllabus(): String = buildString {
        appendLine("DAY TRADING FOUNDATION · ${lessons.size} OFFLINE LESSONS")
        appendLine("Hypothetical examples only; no live prices or trade signals. No model or internet required.")
        lessons.forEachIndexed { index, item ->
            appendLine("${index + 1}. ${item.title}")
        }
        appendLine()
        append("Choose 'trading lesson 1', 'next trading lesson', 'trading quiz 1', or 'trading progress'.")
    }.trim()

    private fun lesson(index: Int): String {
        val item = lessons[index - 1]
        writeInt(LAST, index)
        writeInt(VISITED, readInt(VISITED, 0) or (1 shl (index - 1)))
        return buildString {
            appendLine("TRADING LESSON $index/${lessons.size} · ${item.title.uppercase(Locale.ROOT)}")
            appendLine("OFFLINE EDUCATION · NOT INVESTMENT ADVICE")
            appendLine()
            appendLine(item.concept)
            appendLine()
            appendLine("WORKED EXAMPLE")
            appendLine(item.workedExample)
            appendLine()
            appendLine("CHECK YOUR THINKING")
            appendLine(item.note)
            appendLine()
            append("Practice: 'trading quiz $index'. " +
                if (index < lessons.size) "Continue: 'trading lesson ${index + 1}'." else
                    "Course complete: 'trading progress'.")
        }.trim()
    }

    private fun quiz(index: Int): String {
        val item = lessons[index - 1]
        writeInt(PENDING, index)
        return buildString {
            appendLine("TRADING QUIZ $index/${lessons.size} · ${item.title}")
            appendLine(item.question)
            item.choices.forEachIndexed { i, choice ->
                appendLine("${('A'.code + i).toChar()}. $choice")
            }
            append("Reply 'answer trading A', B or C; or 'skip trading quiz'.")
        }.trim()
    }

    private fun answer(command: String): String {
        val active = readInt(PENDING, 0)
        if (active !in 1..lessons.size) {
            return "No Trading quiz is active. Say 'trading quiz 1' or 'trading quiz'."
        }
        val choice = command.removePrefix("answer trading").trim().uppercase(Locale.ROOT)
        if (choice !in listOf("A", "B", "C")) {
            return "Choose A, B or C using 'answer trading A'."
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
            " ${item.explanation}\n" +
            "Try 'trading quiz $active' to review, or 'trading progress' for your results."
    }

    private fun progress(): String {
        val visited = Integer.bitCount(readInt(VISITED, 0) and VALID_MASK)
        val mastered = Integer.bitCount(readInt(MASTERED, 0) and VALID_MASK)
        val last = readInt(LAST, 0).coerceIn(0, lessons.size)
        val attempts = readInt(ATTEMPTS, 0).coerceAtLeast(0)
        val correct = readInt(CORRECT, 0).coerceIn(0, attempts)
        return "DAY TRADING COURSE PROGRESS · OFFLINE\n" +
            "Lessons opened: $visited/${lessons.size}. " +
            "Lesson quizzes mastered: $mastered/${lessons.size}.\n" +
            "Quiz answers: $correct/$attempts correct. " +
            "Last opened: ${if (last == 0) "none" else "$last/${lessons.size}"}.\n" +
            "Say 'trading course' for the syllabus or 'next trading lesson' to continue."
    }
}
