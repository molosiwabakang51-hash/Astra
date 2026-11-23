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

    /**
     * Quick glossary answers are intentionally short and model-independent.
     * Do not substitute them for a user's explicit request for deeper work,
     * comparisons, language changes or examples: those need Qwen's context.
     * This only chooses between local responders; it never executes tools.
     */
    private val asksForCustomizedExplanation = Regex(
        """\b(?:in depth|in detail|detailed|thorough|comprehensive|exhaustive|""" +
            """step[- ]by[- ]step|worked examples?|examples?|case stud(?:y|ies)|""" +
            """practice questions?|exercises?|quiz|quizzes|homework|""" +
            """derivations?|derive|proof|prove|equations?|formulas?|""" +
            """compare|comparison|contrast|differences?|versus|vs\.?|""" +
            """calculations?|calculate|demonstrate|illustrate|""" +
            """walk me through|go deeper|expand on|elaborate|""" +
            """tables?|charts?|graphs?|diagrams?|""" +
            """in russian|in french|in setswana|in spanish|in german|""" +
            """in portuguese|in swahili|in japanese|in mandarin|""" +
            """for a beginner|for an expert|with|using|including)\b""",
        RegexOption.IGNORE_CASE
    )

    fun respond(raw: String): String? {
        val query = raw.trim().replace(Regex("""\s+"""), " ")
            .lowercase(Locale.ROOT)
            .trimEnd('.', '?', '!', ' ')
        if (query.length > 180 || privateContext.containsMatchIn(query)) return null
        // A direct book-author question needs a direct verified answer;
        // never imply the owner proposed a different or misspelled author.
        // Keep this narrow rather than building a speculative author database.
        if (Regex(
                """^who (?:wrote|authored) (?:the book )?["']?irrational exuberance["']?$"""
            ).matches(query) ||
            Regex(
                """^who is the author of (?:the book )?["']?irrational exuberance["']?$"""
            ).matches(query)
        ) {
            return "Robert J. Shiller wrote Irrational Exuberance (2000). " +
                "This is a known book-author fact; I did not check the internet."
        }
        // The owner reported that Qwen invented "Niall Shiller" when asked
        // about Irrational Exuberance. Answer only this narrow, independently
        // documented identity/book fact deterministically, even for a short
        // correction such as "Robert Stiller?" that lacks a question verb.
        val correctedName = query == "robert stiller" ||
            query == "robert shiller" || query == "niall shiller"
        if (correctedName ||
            (mentions(query, "shiller", "stiller") &&
                mentions(query, "irrational exuberance"))) return shillerExuberance
        if (!explanatoryBeginning.containsMatchIn(query)) return null
        // Explicitly customized or multi-part requests belong to the real
        // conversational model, not the introductory offline glossary.
        if (asksForCustomizedExplanation.containsMatchIn(query)) return null

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
            mentions(query, "leverage", "margin borrowing", "financial leverage") -> leverage
            mentions(query, "foreign exchange risk", "currency risk",
                "exchange rate risk", "currency conversion risk") -> foreignExchangeRisk
            mentions(query, "american bonds", "us treasury bonds",
                "u.s. bonds", "us government bonds", "united states bonds") &&
                mentions(query, "africa", "abroad", "non-us", "outside the us",
                    "another country", "international investor") -> crossBorderBonds
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

    private const val shillerExuberance =
        "ROBERT J. SHILLER — IRRATIONAL EXUBERANCE\n" +
        "The economist is Robert J. Shiller, not Niall Shiller. " +
        "Shiller is associated with Yale University and wrote " +
        "Irrational Exuberance (2000).\n\n" +
        "His central argument: asset prices can rise far beyond what " +
        "economic fundamentals justify when investor psychology, " +
        "herding and reinforcing price expectations fuel speculative bubbles. " +
        "Rising prices attract more buyers, which can drive prices even " +
        "higher until confidence changes. This is a warning about " +
        "valuation and crowd behaviour, not a precise crash prediction.\n\n" +
        "Offline reference: Robert J. Shiller, Irrational Exuberance " +
        "(Princeton University Press, 2000)."

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

    private const val leverage =
        "FINANCIAL LEVERAGE\n" +
        "Leverage uses borrowed money or derivatives to increase the exposure " +
        "relative to the investor's own capital. It magnifies BOTH gains and losses.\n\n" +
        "Example (ignoring interest and fees): you contribute 10,000 and borrow 10,000, " +
        "buying 20,000 of investments. If their value rises 10% to 22,000, " +
        "your equity is 12,000 after repaying the 10,000 loan: a 20% gain " +
        "on your starting equity. If value falls 10% to 18,000, your equity " +
        "is 8,000: a 20% loss. At a 50% fall, equity is exhausted. " +
        "Margin calls, borrowing costs and forced sales can worsen outcomes."

    private const val foreignExchangeRisk =
        "FOREIGN-EXCHANGE RISK\n" +
        "Holding assets in a foreign currency creates exchange-rate exposure " +
        "against the currency in which you spend or measure your wealth. " +
        "Converting local savings into US dollars does NOT eliminate currency risk.\n\n" +
        "For example, a USD bond can gain in dollars but lose value in home-currency " +
        "terms if the dollar weakens enough. Currency hedging may reduce some risk " +
        "but has costs and is not always available. Consider exchange fees, taxes " +
        "and the investor's liabilities before making a decision."

    private const val crossBorderBonds =
        "US BONDS FROM OUTSIDE THE UNITED STATES\n" +
        "US Treasury securities are government debt instruments, and US corporate " +
        "bonds carry issuer credit risk. An investor outside the US may be able to " +
        "buy eligible securities or regulated bond funds through a broker that " +
        "accepts residents of their specific country.\n\n" +
        "Before choosing a platform, verify that it accepts your country of residence, " +
        "offers the instrument and account type, and complies with local rules. " +
        "Check identity requirements, custody, tax treatment, minimums, fees, " +
        "liquidity, interest-rate risk, and USD exchange-rate exposure. " +
        "Converting your local currency to USD does NOT remove currency risk. " +
        "Availability and prices cannot be verified while offline; never assume " +
        "a named US bank or broker opens accounts for all African residents."

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
