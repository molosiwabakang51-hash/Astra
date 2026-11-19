package com.astra.agent

/** Original offline explanatory material; not official CFA curriculum or advice. */
internal object AstraMasterFinance {
    val units: List<AstraMasterclass.Unit> = listOf(
        AstraMasterclass.Unit(
            "Financial statements and cash flow", "Intermediate",
            "Financial statements answer different questions. The statement of financial position measures assets, liabilities and residual equity at one date; the income statement records revenue and expenses over a period; the cash-flow statement separates operating, investing and financing flows. Accrual accounting recognizes revenue when earned, not necessarily when cash arrives. To evaluate solvency and earnings quality, reconcile profit to operating cash flow and examine receivables, inventories, payables, provisions and capital expenditure. Avoid treating depreciation, a noncash accounting charge, as an immediate payment. Ratio changes need comparison with business model and accounting policy.",
            "A hypothetical retailer reports P1,000 revenue, P700 cash-based expenses and P100 depreciation: profit before tax P200. Receivables increase P150 while other working-capital accounts are unchanged, so an illustrative indirect cash reconciliation begins at P200 + P100 − P150 = P150, not P200. Interest, tax and other adjustments could change that amount.",
            "Prepare a miniature income statement and cash reconciliation for a shop with P600 credit sales, P400 cash costs and P80 depreciation. Assume customers pay only P350 during the period. Explain which values are profit and which are collections, and identify at least one missing input before claiming total cash flow.",
            "Why can reported profit exceed operating cash collected?",
            listOf("Profit and cash are always identical", "Revenue may be recognized before customer payment", "All noncash items are financing payments"), 'B',
            "Credit sales create receivables; changes in working capital help explain differences between earnings and cash receipts."
        ),
        AstraMasterclass.Unit(
            "Discounted cash flows and effective rates", "Intermediate",
            "Present value discounts each expected future cash flow using a rate matched to its risk, currency and timing: PV equals the sum of CF_t divided by (1 + r)^t. For a level annuity, PV = C × [1 − (1+r)^(-n)]/r when payments are at period end and the periodic rate is nonzero. Compounding frequency matters: a quoted nominal annual rate with monthly compounding is not the same as its effective annual rate, (1 + nominal/12)^12 − 1. A discount-rate assumption embodies risk and opportunity cost; it is not a factual guarantee of returns. Compare nominal cash flows to nominal rates and real cash flows to real rates.",
            "Consider P1,000 due in one year and P1,100 due in two years. Discounted at 10% annually, PV = 1,000/1.10 + 1,100/1.10² = approximately P1,818.18. Discounting both amounts for two years would incorrectly understate the first payment.",
            "Value an illustrative three-year stream of P200, P300 and P400 at an annual 8% rate. Show the denominator for each payment, estimate the sum, then repeat with 12% and explain why the valuation falls. Finally describe how you would treat a cash flow quoted in US dollars when your budget is in pula.",
            "Which treatment makes a discounted-cash-flow calculation internally consistent?",
            listOf("Nominal cash flows with matching nominal discount rates", "Real cash flows with arbitrary nominal rates", "Discount all cash flows for the longest maturity"), 'A',
            "Currency, inflation basis and period convention should align between cash flows and discount rates."
        ),
        AstraMasterclass.Unit(
            "Bond valuation, yields and total return", "Advanced",
            "A conventional fixed-rate bond promises coupons and principal but may trade above or below par. Its dirty price is the present value of remaining payments, including accrued interest; a quoted clean price normally excludes accrued interest. Yield to maturity is the discount rate that equates promised cash flows to the market price under stated conventions, not a guaranteed investor return. Realized return depends on the purchase price, default risk, reinvestment of coupons and sale or holding horizon. A coupon of 5% does not imply a 5% current yield or yield to maturity when the bond is not at par. Different markets use distinct settlement and day-count rules.",
            "A P1,000 face-value two-year bond with 5% annual coupons pays P50 after year one and P1,050 after year two. At a 6% annual yield, price = 50/1.06 + 1,050/1.06² ≈ P963.33. Trading below par is consistent with its coupon rate being below the market discount rate.",
            "Create a timeline for the same two-year bond at yields of 4%, 5%, 6% and 8%. Reprice each using present-value factors. Explain premium, par and discount prices, and identify what additional settlement data you need before calculating the exact clean traded price.",
            "What usually happens to the market price of a fixed-rate bond when its required yield rises?",
            listOf("It rises by the same percentage", "It falls, other factors equal", "It stays at face value"), 'B',
            "A larger discount rate reduces the present value of fixed contractual coupons and principal."
        ),
        AstraMasterclass.Unit(
            "Duration, convexity and rate shocks", "Advanced",
            "Macaulay duration is the weighted average time of a bond's discounted cash flows. Modified duration estimates the percentage price response to a small yield change: ΔP/P ≈ −Dmod × Δy. Convexity is the curvature adjustment, commonly +0.5 × convexity × (Δy)^2, improving estimates for larger parallel yield shifts. These are local approximations, not exact promises. Effective duration is often preferred when option-driven cash flows change with rates. Parallel shifts miss curve twists, spread changes and credit events. Portfolio duration is a weighted sensitivity measure only when the constituents and conventions are consistent.",
            "For a bond with modified duration 4.5 and convexity 30, a hypothetical 1 percentage-point yield increase (Δy=0.01) gives estimated price change −4.5×0.01 + 0.5×30×0.0001 = −0.0435, or −4.35%. The linear duration-only estimate is −4.50%; neither is exact.",
            "Calculate duration-only and duration-plus-convexity responses for yield movements of +50, +100 and −100 basis points using Dmod 5 and convexity 40. Compare asymmetry, then explain why a callable bond could behave differently. Document whether the yield change is expressed in decimal form.",
            "What does positive convexity generally add to the duration-only approximation?",
            listOf("A positive second-order curvature term", "A promise that price cannot fall", "A reduction of the coupon payment"), 'A',
            "The convexity term is nonnegative for positive convexity because the squared yield change is nonnegative."
        ),
        AstraMasterclass.Unit(
            "Credit, liquidity and yield spreads", "Advanced",
            "Government and corporate bonds carry different sources of risk. A nominal yield can be decomposed conceptually into an underlying reference rate plus credit, liquidity and other premia, although observed spreads do not perfectly isolate these components. Credit ratings are opinions about relative risk, not guarantees, and spreads can widen before defaults occur. Recovery depends on debt seniority, collateral and restructuring outcomes. A bond with a high stated yield can be unattractive if default-adjusted cash flows are weak or the market is illiquid. Distinguish currency and inflation risk from credit risk when comparing local and foreign debt.",
            "Suppose a two-year corporate yield is 9% while a comparable sovereign reference yields 6%, so the observed simple yield spread is about 300 basis points. That difference could reflect credit concerns, market liquidity and technical factors; it does not mean there is exactly a 3% probability of default.",
            "Compare a liquid government bond at 5% yield and a thinly traded corporate bond at 9%. Build a table of issuer, maturity, currency, seniority, spread and plausible stress scenarios. Explain why the extra 400 basis points is not free income.",
            "How should a quoted corporate yield spread be interpreted?",
            listOf("As a guaranteed net profit", "As the exact default probability", "As compensation for multiple risks, not a pure probability"), 'C',
            "Observed spreads reflect several risks and market effects; translating them into default probabilities requires a model and assumptions."
        ),
        AstraMasterclass.Unit(
            "Equity analysis and valuation", "Advanced",
            "Equity represents a residual claim after obligations to creditors. Analysts estimate value using discounted cash flows, dividend models, residual income or relative multiples, while checking earnings quality and balance-sheet resilience. A price/earnings ratio can be misleading for cyclical, loss-making or highly leveraged firms. Dividend yield depends on the most recent relevant dividend and market price; it says little about whether future dividends can be maintained. A dividend-discount model with constant perpetual growth uses P = D1/(r−g), valid only under assumptions including r greater than g. Inputs and scenario ranges matter more than apparent precision.",
            "If a share is expected to pay P5 next year, required return r=10% and sustainable perpetual growth g=3%, the simple Gordon valuation is P5/(0.10−0.03) = P71.43. Changing r or g by one percentage point can materially alter the value. A company unable to fund dividends should not be valued mechanically with this model.",
            "Value a hypothetical stable utility at D1=P4 with discount rates of 8%, 10% and 12%, holding growth at 2%. Compare estimates with a market price of P55. Then list business events that would invalidate the constant-growth assumption.",
            "Why must r exceed g in the simple constant-growth dividend model?",
            listOf("To keep the perpetuity denominator positive and finite", "To make the dividend tax rate zero", "To ensure earnings equal free cash flow"), 'A',
            "With r not greater than g, the constant-growth perpetuity formula is not a finite valid valuation under its stated assumptions."
        ),
        AstraMasterclass.Unit(
            "Capital structure and cost of capital", "Advanced",
            "Cost of debt depends on the marginal borrowing rate adjusted for relevant tax effects, not simply the coupon on outstanding debt. Cost of equity is an expected compensation for equity risk, sometimes approximated with CAPM: r_e = r_f + β × equity-risk premium. Weighted average cost of capital combines market-value weights for debt and equity and can be a useful discount rate for projects with similar risk to the firm. It is not automatically correct for every project, country or currency. Leverage changes financial risk, and financing choices can affect tax shields, distress costs, covenants and control.",
            "An illustrative firm finances 60% by equity at 12% required return and 40% by debt costing 7%, with a 22% marginal tax rate. WACC ≈ 0.60×12% + 0.40×7%×(1−0.22) = 9.384%. This estimate assumes a usable tax deduction and relevant market-value weights.",
            "Compute WACC for an otherwise similar firm with 70% equity, 30% debt, 11% equity return, 8% debt cost and 25% tax. Explain whether the result should be used to discount a speculative start-up venture in a different country.",
            "Which principle is essential when applying WACC?",
            listOf("Every project always uses the current corporate WACC", "Match the discount rate to project risk and cash-flow basis", "Use face-value debt weights regardless of market values"), 'B',
            "Discount rates must reflect the relevant risk, market-value capital structure and consistent currency/inflation assumptions."
        ),
        AstraMasterclass.Unit(
            "Portfolio risk, correlation and diversification", "Advanced",
            "A portfolio's variance depends on each asset's volatility and pairwise covariance, not merely the average volatility. For two assets, σp² = w1²σ1² + w2²σ2² + 2w1w2σ1σ2ρ. Correlation can change during crises, so a historical estimate does not guarantee future protection. Concentration can dominate outcomes even when headline holdings count is high. Separate systematic market risk from idiosyncratic risk and understand that diversification can reduce the latter but cannot eliminate broad market losses. Rebalancing needs rules, transaction costs and tax consideration.",
            "Two equal-weighted assets each have annual volatility of 20%. If correlation is zero, portfolio volatility is √(0.25×0.04+0.25×0.04) ≈ 14.14%; with correlation +1, volatility is 20%. The equal expected returns do not imply equal portfolio risk.",
            "Calculate a two-asset portfolio's variance using weights 60/40, volatilities 15%/25%, and correlations −0.5, 0 and +0.8. Describe what happens if the second asset becomes highly correlated during a drawdown. Avoid assuming normally distributed returns.",
            "Which input is needed to calculate two-asset portfolio volatility?",
            listOf("The covariance or correlation between returns", "Only the number of shares held", "Only the higher expected return"), 'A',
            "Portfolio risk includes the covariance term, which captures how asset returns move together."
        ),
        AstraMasterclass.Unit(
            "Foreign exchange and cross-border bonds", "Advanced",
            "Investing in a foreign-currency bond creates at least two layers of return: local-currency bond performance and currency translation back into the investor's home currency. For a home investor, the approximate combined return is (1 + asset return in foreign currency) × (1 + change in foreign currency value measured in home currency) − 1. A dollar bond does not eliminate exchange-rate risk for a pula investor. Access routes, broker eligibility, withholding tax, settlement fees and currency conversions need verification for the investor's jurisdiction and account type. Avoid assuming that a named US broker accepts every African country or offers every US Treasury product.",
            "An illustrative USD bond gains 4% in US-dollar value while USD appreciates 8% against the pula: pula return ≈ 1.04×1.08−1 = 12.32% before taxes and fees. If USD instead depreciates 8%, the same asset produces about −4.32% in pula. A 4% US-dollar bond gain is not a guaranteed 4% home return.",
            "Build four scenarios combining a bond return of +4% or −3% with a currency move of +10% or −10% in pula terms. Calculate combined returns multiplicatively. Then list which cross-border brokerage access, residency, tax and product-eligibility facts must be checked before an actual investment.",
            "What is true for a pula investor owning an unhedged US-dollar Treasury bond?",
            listOf("Dollar denomination removes all risk", "The bond return and USD/BWP movement jointly determine pula returns", "The nominal coupon fixes the total home-currency return"), 'B',
            "Cross-currency investing adds an exchange-rate component to capital and income returns."
        ),
        AstraMasterclass.Unit(
            "Derivatives, hedging and basis risk", "Advanced",
            "Futures, forwards, options and swaps transfer financial exposures using contracts rather than simply buying assets outright. Payoff diagrams clarify asymmetric and linear risks. A futures position requires margin and may be marked to market daily; margin is collateral, not the maximum possible loss. A long call has limited premium loss for the buyer, while writing uncovered options can have much larger risk. Hedging reduces selected exposure but can introduce basis, liquidity, counterparty and roll risks. Contract multiplier and expiry determine notional exposure, and a correct hedge ratio depends on the asset and hedge sensitivity.",
            "A hypothetical futures contract covers 50 index units per point. At index level 4,000, notional value is P200,000. A 100-point adverse move creates a P5,000 loss before fees, regardless of an illustrative P12,000 initial margin. Thus 'margin posted' is not 'capital at risk'.",
            "Draw the payoff of a purchased call with strike 100 and premium 4 at underlying expiry prices 80, 100, 104, 120. Compare intrinsic payoff with profit after premium. Then size an index futures hedge for a portfolio using the contract multiplier and a stated beta assumption.",
            "Which describes initial margin on a futures contract?",
            listOf("The largest possible loss", "A fixed interest payment", "Collateral rather than a guaranteed loss cap"), 'C',
            "Futures exposure is driven by contract specification and price moves; losses may exceed initial margin."
        ),
        AstraMasterclass.Unit(
            "Macroeconomics, inflation and the yield curve", "Advanced",
            "Interest rates respond to expectations for policy, inflation, growth, credit and term premia. A yield curve plots yields of comparable debt at different maturities; its slope and changes reflect several drivers, not a deterministic recession forecast. Compare nominal yield with expected inflation to reason about approximate real yield; for precise relationships use (1+nominal)/(1+inflation)−1. Different central banks have different operating regimes and announcement calendars. A lower policy rate may stimulate activity but can coincide with weaker growth or currency pressure. Analysts should separate observed data from narratives and consider lags.",
            "At a 7% nominal annual return and 4% inflation, exact illustrative real return is 1.07/1.04−1 ≈ 2.88%, not a guaranteed 3%. If a two-year government yield rises by 100 basis points but the ten-year rises by only 20, the curve changes shape rather than moving in a parallel shift.",
            "Sketch a yield curve with 2-, 5- and 10-year maturities before and after a steepening event. List three possible causes of a falling short-term yield. Explain why you cannot infer future equity returns from curve shape alone.",
            "Which formula gives an exact single-period real return?",
            listOf("Nominal return minus the coupon rate", "(1 + nominal return)/(1 + inflation) − 1", "Nominal return multiplied by inflation"), 'B',
            "Inflation reduces purchasing power multiplicatively, not by a guaranteed fixed subtraction."
        ),
        AstraMasterclass.Unit(
            "Ethics, evidence and investor protection", "Advanced",
            "Investment advice carries duties around truthful communication, evidence, conflicts of interest and suitability. Separate research claims, assumptions, scenarios and personal recommendations. A compelling backtest can be misleading if it uses survivorship bias, look-ahead information, selective reporting or unrealistic execution assumptions. Privacy is also part of investor protection: do not disclose account passwords or private balances to unauthorised systems. The purpose of ethics is to protect decisions under uncertainty, not to make a report look compliant. Verify laws and licensing in the investor's actual jurisdiction rather than generalizing across countries.",
            "Imagine a promoter claims a trading system earned 4% every month for five years but provides only winning trades and no independently audited statements. The absence of losing trades, cost disclosure and third-party verification makes the claim unreliable, even if the included calculations are arithmetically correct.",
            "Write a one-page risk note on a hypothetical derivative strategy. Label verified facts, model inputs and unverified claims separately. Identify at least four conflicts or governance controls, and specify which evidence would be necessary before investing actual capital.",
            "Which practice most improves the integrity of an investment report?",
            listOf("Clearly separating verifiable evidence from assumptions and conflicts", "Removing all negative results from tables", "Treating marketing claims as audited performance"), 'A',
            "Transparent evidence, uncertainty and conflicts allow a reader to assess reliability rather than rely on presentation."
        ),
        AstraMasterclass.Unit(
            "Asset allocation, rebalancing and objectives", "Advanced",
            "An investment policy connects objectives, time horizon, liquidity needs, constraints and risk capacity to asset allocation. Strategic allocation sets a longer-term target; tactical tilts are short-term departures and should be explicitly justified. A portfolio can have high expected return but still be unsuitable if near-term liabilities cannot tolerate drawdowns. Rebalancing restores weights but can trigger costs and tax; threshold rules can reduce unnecessary trading. Performance attribution asks whether results came from allocation, security selection, currency or other factors, and requires a coherent benchmark. An index comparison is not automatically appropriate for an income-focused mandate.",
            "A hypothetical portfolio targets 60% equities and 40% bonds, starting at P100,000. After a period, equities are worth P75,000 and bonds P40,000; total P115,000 and equity weight 65.22%. Restoring 60/40 would target P69,000 equities and P46,000 bonds, selling P6,000 equities before costs.",
            "Draft an investment policy for a hypothetical retiree needing P30,000 within 12 months and investing a separate long-term reserve. Propose a strategic mix, concentration cap, rebalancing trigger and appropriate benchmark. Explain how expected return differs from risk capacity and liquidity.",
            "What is a direct purpose of portfolio rebalancing?",
            listOf("To guarantee outperformance", "To remove all market risk", "To restore exposures toward chosen target weights"), 'C',
            "Rebalancing keeps allocation closer to policy limits; it does not guarantee future returns."
        ),
        AstraMasterclass.Unit(
            "Retirement and pension funding", "Advanced",
            "Pension promises depend on projected contributions, investment returns, longevity, fees and inflation. Defined-contribution plans place more market and longevity risk on individuals; defined-benefit plans promise a specified benefit formula but carry sponsor and funding risks. A real spending target must be distinguished from a nominal balance, and an annuity discount rate must match the payment timing. Longevity scenarios, sequence-of-returns risk and withdrawal policy matter because losses just before retirement are especially costly. Governance includes investment oversight, custody, member communications and policy compliance. Rules vary across pension jurisdictions.",
            "A worker contributes P500 at each month end for 12 months, before investment returns: total contributions are P6,000. If an annual inflation rate is 5% over the same period, nominal savings growth alone cannot tell how much future purchasing power those contributions will fund.",
            "Prepare a stylised retirement worksheet with initial savings, annual savings, nominal returns, inflation and a 25-year withdrawal period. Stress test a 20% market fall one year before retirement and compare real versus nominal spending. State which assumptions lack reliable forecasts.",
            "Which risk is especially relevant when withdrawing from a retirement portfolio?",
            listOf("Sequence-of-returns risk", "Only the number of bank branches nearby", "The coupon date of one unrelated bond"), 'A',
            "Early losses combined with withdrawals can permanently reduce a portfolio's recovery capacity."
        )
    )
}
