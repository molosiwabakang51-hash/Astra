package com.astra.agent

import java.util.Locale

/**
 * Small, accurate offline finance explanations for frequent educational
 * questions. Never turns free-form questions into financial transactions.
 * Specialized calculators and on-device Qwen3 remain available separately.
 *
 * This layer is intentionally deterministic: definitions and worked examples
 * do not require an installed language model or a network connection.
 */
internal object AstraFinanceEducation {
    private val explanatoryBeginning = Regex(
        """^(?:(?:please|could you|can you|would you)\s+)*""" +
            """(?:explain|define|describe|teach me (?:about|how)|""" +
            """tell me about|what (?:is|are|does|do)|what's|how (?:does|do|is)|""" +
            """help me understand|i want to understand)\b""",
        RegexOption.IGNORE_CASE
    )
    private val privateContext = Regex(
        """\b(?:my|our|mine|ours|this portfolio|my investment|my returns)\b""",
        RegexOption.IGNORE_CASE
    )

    fun respond(raw: String): String? {
        val query = raw.trim().replace(Regex("""\s+"""), " ")
            .lowercase(Locale.ROOT)
            .trimEnd('.', '?', '!', ' ')
        if (query.length > 180 || !explanatoryBeginning.containsMatchIn(query)) return null
        if (privateContext.containsMatchIn(query)) return null

        return when {
            mentions(query, "compound interest", "compounding interest",
                "interest compounding", "how interest compounds") ->
                compoundInterest

            mentions(query, "simple interest") -> simpleInterest
            mentions(query, "modified duration", "macaulay duration",
                "bond duration", "duration of a bond", "duration in bonds") ->
                bondDuration

            mentions(query, "convexity", "bond convexity") -> convexity
            mentions(query, "yield to maturity", "ytm") -> yieldToMaturity
            mentions(query, "dividend yield") -> dividendYield
            mentions(query, "diversification", "diversify a portfolio") -> diversification
            mentions(query, "annuity", "annuities") -> annuity
            mentions(query, "present value", "discounting future cash flows") -> presentValue
            mentions(query, "inflation", "purchasing power") -> inflation
            mentions(query, "time value of money", "tvm") -> timeValueOfMoney
            mentions(query, "risk and return", "risk-return trade-off") -> riskReturn
            else -> null
        }
    }

    private fun mentions(text: String, vararg expressions: String): Boolean =
        expressions.any { expression ->
            Regex("""(?<![a-z])""" + Regex.escape(expression) + """(?![a-z])""")
                .containsMatchIn(text)
        }

    private const val compoundInterest =
        "COMPOUND INTEREST\n" +
        "You earn interest on your original amount AND on interest already earned.\n\n" +
        "Example: P1,000 invested at 10% per year, compounded annually:\n" +
        "Year 1: P1,100\nYear 2: P1,210\nYear 3: P1,331\n" +
        "Total interest = P331, compared with P300 using simple interest.\n\n" +
        "Formula: Future value = P × (1 + r/n)^(n×t), where P is the starting " +
        "amount, r the annual rate as a decimal, n the compounding periods " +
        "per year, and t the number of years. A higher rate, more time or " +
        "more frequent compounding increases the ending value, all else equal."

    private const val simpleInterest =
        "SIMPLE INTEREST\n" +
        "Interest is calculated on the original principal only, not on earlier interest.\n\n" +
        "Formula: I = P × r × t.\n" +
        "Example: P1,000 at 10% per year for three years gives " +
        "P1,000 × 0.10 × 3 = P300 interest; final value P1,300. " +
        "With annually compounded interest, the final value would be P1,331."

    private const val bondDuration =
        "BOND DURATION\n" +
        "Macaulay duration is the weighted-average time until a bond's discounted " +
        "cash flows are received. Modified duration estimates its sensitivity " +
        "to changes in yield.\n\n" +
        "Approximation: percentage price change ≈ −modified duration × yield change " +
        "(express the yield change as a decimal).\n" +
        "Example: if modified duration is 5, a 1-percentage-point rise in yield " +
        "implies roughly a 5% price fall, before convexity. " +
        "Duration is not the same as maturity."

    private const val convexity =
        "BOND CONVEXITY\n" +
        "Convexity measures how a bond's price–yield sensitivity changes as yields move. " +
        "It improves the linear duration estimate, especially for larger changes.\n\n" +
        "Approximation: ΔP/P ≈ −Dmod × Δy + ½ × Convexity × (Δy)². " +
        "Use decimal yield changes (1 percentage point = 0.01).\n" +
        "Most plain fixed-rate, non-callable bonds have positive convexity; " +
        "some option-embedded bonds can behave differently."

    private const val yieldToMaturity =
        "YIELD TO MATURITY (YTM)\n" +
        "YTM is the discount rate that makes a bond's promised future coupons " +
        "and principal equal its current market price. It is not the coupon rate.\n\n" +
        "A bond bought below face value generally has a YTM above its coupon rate. " +
        "Realized return may differ because of default, sale before maturity " +
        "and the rate at which coupons are reinvested."

    private const val dividendYield =
        "DIVIDEND YIELD\n" +
        "Dividend yield = annual cash dividends per share ÷ current share price × 100%.\n" +
        "Example: a share priced at P100 paying P5 in yearly dividends has a 5% yield.\n" +
        "Dividend yield is only an income measure; total shareholder return " +
        "also includes changes in the share price. Dividends are not guaranteed."

    private const val diversification =
        "PORTFOLIO DIVERSIFICATION\n" +
        "Diversification spreads exposure across assets, sectors or regions " +
        "whose returns do not move perfectly together. " +
        "It can reduce company-specific (unsystematic) risk, but it cannot " +
        "eliminate market-wide risk.\n\n" +
        "Portfolio risk depends on asset weights, individual volatility " +
        "and the covariance/correlation between returns."

    private const val annuity =
        "ANNUITY\n" +
        "An annuity is a series of equal payments at regular intervals. " +
        "An ordinary annuity pays at the end of each period; an annuity due " +
        "pays at the beginning.\n\n" +
        "Ordinary annuity present value = Payment × [1 − (1 + r)^(−n)] / r, " +
        "where r is the periodic discount rate and n is the number of payments. " +
        "For r = 0, present value = Payment × n."

    private const val presentValue =
        "PRESENT VALUE\n" +
        "Present value converts future money into today's equivalent using " +
        "a discount rate.\n\n" +
        "PV = FV / (1 + r)^t.\n" +
        "Example: P1,100 received in one year is worth P1,000 today " +
        "when the annual discount rate is 10%. " +
        "Higher discount rates reduce present values, all else equal."

    private const val inflation =
        "INFLATION AND PURCHASING POWER\n" +
        "Inflation is a sustained rise in the general price level. It reduces " +
        "how much a fixed amount of money can buy.\n\n" +
        "Exact real return = (1 + nominal return)/(1 + inflation rate) − 1. " +
        "At 8% nominal return and 5% inflation, real return is about 2.86%, " +
        "not 8%. Nominal and inflation rates are decimal values in the formula."

    private const val timeValueOfMoney =
        "TIME VALUE OF MONEY\n" +
        "Money available now is generally worth more than the same nominal amount " +
        "received later because it can be invested and may lose purchasing power.\n\n" +
        "Future value = Present value × (1 + r)^t. " +
        "Present value = Future value / (1 + r)^t. " +
        "Apply a consistent discount rate and time period."

    private const val riskReturn =
        "RISK–RETURN TRADE-OFF\n" +
        "Investments with higher expected returns generally involve greater " +
        "uncertainty or possible losses. Higher risk does not guarantee " +
        "higher realized returns.\n\n" +
        "Compare expected return with volatility, liquidity, concentration, " +
        "time horizon and capacity to absorb a loss. Diversification may " +
        "improve a portfolio's risk-adjusted return."
}
