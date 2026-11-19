package com.astra.agent

/** Original risk-centered offline teaching; no trade signals, live prices or broker actions. */
internal object AstraMasterTrading {
    val units: List<AstraMasterclass.Unit> = listOf(
        AstraMasterclass.Unit(
            "Forex market structure and currency quotes", "Intermediate",
            "Currency trading exchanges one currency for another. In a quote such as EUR/USD = 1.1000, one euro costs 1.10 US dollars. The first currency is the base and the second the quote; buying the pair means gaining base-currency exposure while selling quote currency. Spot trades, forwards, exchange-traded futures and leveraged retail CFDs can use different settlement, margin and regulatory rules. Brokers also differ in whether they act as agents, principals or market makers. Liquidity usually changes by time of day and around economic releases. Separate the actual underlying market from synthetic broker products before evaluating any strategy.",
            "A hypothetical EUR/USD quote moves from 1.1000 to 1.1010. The change is 0.0010 USD per euro, conventionally ten pips for most four-decimal pairs. A 100,000-euro long position has a gross quote-currency change of USD100 before spread, financing and fees, not an automatic 100-pula gain.",
            "Write the base and quote currencies for EUR/USD, USD/JPY and GBP/USD. For EUR/USD, calculate gross USD profit or loss for 10,000 units after a move of +0.0020 and −0.0010. Explain why converting the result into pula is a separate calculation that needs an exchange rate.",
            "What does buying EUR/USD primarily mean?",
            listOf("Buying EUR while selling USD exposure", "Buying both currencies simultaneously", "Buying only a US stock index"), 'A',
            "EUR is the base currency; a long EUR/USD position benefits from a rising number of USD per EUR."
        ),
        AstraMasterclass.Unit(
            "Index derivatives and contract specifications", "Intermediate",
            "Index products give exposure to a basket benchmark without necessarily owning its component shares. A cash index is a calculation, whereas a futures contract is a standardized exchange-traded agreement; an ETF is a security; a CFD is a bilateral derivative with its provider. Contract multiplier, tick size, currency, trading hours, expiry and settlement decide monetary exposure. Two brokers can label index CFDs similarly while assigning different contract sizes. Know the product before comparing stops, margin or historical results. Index levels may be price-return or total-return benchmarks, which also affects interpretation.",
            "A hypothetical index futures contract quotes 5,000 points with a multiplier of USD20 per point. One contract has USD100,000 notional exposure; a 15-point move produces USD300 gross profit or loss. If initial margin were USD8,000, that would not cap the loss at USD8,000.",
            "Make a contract-specification checklist for an index future and a broker index CFD: ticker, index variant, multiplier, tick size, trading session, expiry, overnight fees and corporate-action treatment. Model a 40-point adverse move at two different multipliers and compare the currency losses.",
            "Why must traders check an index derivative's multiplier?",
            listOf("It tells them the exact future direction", "It converts index points into monetary exposure", "It guarantees a minimum profit"), 'B',
            "Monetary P&L per point equals the contract multiplier times the position size."
        ),
        AstraMasterclass.Unit(
            "Bid–ask spreads, order books and execution", "Intermediate",
            "The spread is the difference between best ask and best bid, but the displayed best prices may have limited depth. A market buy consumes available sell liquidity and can fill at progressively higher prices, especially when large relative to the order book. A limit order caps the execution price for buying or sets a floor for selling but might never fill. Order queues, partial fills, trading halts and stop triggers complicate execution. A backtest that assumes every order fills at a single mid-price will often overstate returns. Effective spread and execution shortfall are more useful than posted commission alone.",
            "Suppose the best ask is 100.00 for 50 shares and the next ask is 100.10 for 100 shares. A market order to buy 100 shares could fill 50 at 100.00 and 50 at 100.10, for average price 100.05. A backtest assuming full execution at 100.00 understates cost by 0.05 per share.",
            "Simulate an order book with three price levels and quantities, then calculate volume-weighted execution for a large market buy. Repeat using a limit order that forbids the third price level and identify the unfilled quantity. Include a scenario where visible liquidity disappears before submission.",
            "Which order type primarily prioritizes immediate execution rather than a specified price?",
            listOf("A limit order", "A cancellation request", "A market order"), 'C',
            "Market orders seek execution against available liquidity; exact fill prices are not guaranteed."
        ),
        AstraMasterclass.Unit(
            "Spread, pip value and position-size arithmetic", "Intermediate",
            "Risk must be expressed in money before it becomes a fraction of account equity. Forex pip value depends on base units, quote currency and pip convention; index contracts have point or tick multipliers. A generic risk calculation is planned units = monetary risk budget divided by planned loss per unit, with an additional allowance for fees and slippage. However, price gaps can exceed the planned stop distance, so sizing based on a stop is only a model. Compare free cash, initial margin, notional exposure and stress loss explicitly; none are interchangeable.",
            "Suppose a hypothetical account has P50,000, a 0.5% planned risk budget and a derivative loses P5 per contract per adverse point. A 10-point planned stop implies P50 loss per contract. The budget is P250, so a simplified size is 5 contracts before extra costs, but a gap of 30 points would produce P750 loss.",
            "Calculate contract count at equity P40,000, risk fraction 0.75%, point value P2 and stop distance 20 points. Then stress-test a 55-point gap, subtract commissions, and decide whether rounding to an integer contract overshoots the original risk budget.",
            "What should a position-sizing worksheet express first?",
            listOf("A monetary maximum planned risk before contract count", "A certain probability of winning", "The strategy's annual return based only on one trade"), 'A',
            "A money risk budget can be divided by proposed stop-distance exposure, then stress-tested for gaps."
        ),
        AstraMasterclass.Unit(
            "Leverage, margin and forced liquidation", "Advanced",
            "Leverage controls a notional position larger than posted equity. Retail foreign-exchange CFDs, index futures and options each have specific margin rules, and brokers may liquidate positions when margin maintenance requirements are breached. Notional leverage is exposure divided by account equity, but portfolio risk depends on price sensitivity, correlation, hedges and gaps. Funding charges, currency conversion, variation margin and broker close-out rules matter. Increasing leverage does not create an information advantage or improve the statistical quality of a trading idea. A stop-loss may fail to execute at its trigger under discontinuous prices.",
            "With P20,000 equity and P200,000 of notional exposure, leverage is 10×. A 1% adverse price move translates to approximately P2,000 before costs, or 10% of equity; a 5% adverse move is about P10,000, half of starting equity. Maintenance margin could force action sooner.",
            "Build a table for leverage 1×, 2×, 5× and 10× with underlying price shocks of −1%, −3% and −10%. Calculate approximate equity impacts, then explain why a table of linear shocks understates some derivative risks and excludes financing.",
            "At 10× leverage, a 2% adverse underlying move is approximately what equity loss before costs?",
            listOf("2%", "20%", "0.2%"), 'B',
            "2% × 10 = 20% of initial equity under a simplified linear-exposure assumption."
        ),
        AstraMasterclass.Unit(
            "Candles, trend models and multi-timeframe analysis", "Intermediate",
            "A candlestick compresses opening, high, low and closing prices for a chosen interval; it cannot reveal the full within-bar transaction sequence. A pattern may describe historical trading but does not establish a causal forecast. Trend classification often uses higher highs and lows, smoothing averages or regression slopes. Comparing a five-minute chart with a daily chart can expose differences in volatility and regime, but a trade must still define the time horizon used for decisions. Multiple indicators derived from the same prices do not create independent confirming evidence.",
            "An hourly bar opens at 100, rises to 105, falls to 98 and closes at 102. The range is 7 points and the close is above the open. A bullish-colored candle does not reveal whether the high occurred before or after the low; two very different intrabar paths fit the same OHLC data.",
            "Create five hypothetical daily candles and classify higher highs, lower lows and closing prices. Compute a 3-day simple moving average. Compare your classification with 15-minute observations and identify what data would be necessary before making any statistical predictive claim.",
            "Which information is not contained in ordinary OHLC candles?",
            listOf("The open and close", "The full sequence of every intrabar transaction", "The interval high and low"), 'B',
            "OHLC summarizes extrema and endpoints; detailed intrabar ordering requires tick or finer data."
        ),
        AstraMasterclass.Unit(
            "Trend indicators, volatility and false signals", "Advanced",
            "Moving averages, oscillators and breakout rules can help formalize hypotheses but should not be treated as independent evidence when calculated from the same prices. An RSI near a conventional threshold describes recent relative gains and losses; it does not prove that reversal will occur. Average true range helps summarize recent trading ranges including gaps, while standard deviation measures dispersion under particular sampling choices. Indicators have lookback lengths, latency and regime sensitivity. If you optimize many parameters until a chart looks attractive, you are likely searching for noise unless you control for multiple testing and use untouched data.",
            "A simple moving average of closes 100, 102 and 104 is 102. Replacing the earliest close with 106 changes the average to 104; the indicator reacts after data changes. A very short average tracks price more quickly but may whipsaw more often than a long average.",
            "Write an explicit indicator rule: entry condition, required data at decision time, exit condition and permitted data revisions. Compare lookbacks 5 and 20 without choosing the winner using the same sample you later call a 'test'. Specify how you will measure false signals.",
            "Why can combining five price-derived indicators still provide little independent confirmation?",
            listOf("They may be transformations of the same underlying prices", "Every indicator predicts precisely", "Indicator lookbacks never overlap"), 'A',
            "Correlated indicators can recycle the same information rather than provide independent evidence."
        ),
        AstraMasterclass.Unit(
            "Economic calendars and event-driven volatility", "Advanced",
            "Currency and index markets respond to policy announcements, inflation releases, employment figures and revisions to earlier data. Prices react to differences between expectations and outcomes, not only the headline level. Volatility and spreads often expand around scheduled events, and execution can be poor even when the direction guess is correct. A market-moving surprise can have different effects under different inflation, growth and policy regimes. Be careful with calendars across time zones and daylight-saving changes. An offline course cannot supply verified upcoming economic-release times or live consensus forecasts; those require external, current sources.",
            "Imagine analysts expect 3.0% inflation, but a fictional report prints 3.5%. The 0.5 percentage-point surprise may affect rate expectations, yet the immediate FX direction depends on wider context. A resting stop might fill materially away from its trigger if spreads widen after the announcement.",
            "Design a paper-trading study of 20 past policy announcements with source timestamps and market snapshots before, during and after the release. Record bid–ask spread, maximum adverse excursion and whether the first reaction reversed. Identify the danger of choosing only memorable events.",
            "Which variable helps define a surprise around a macroeconomic release?",
            listOf("Only the calendar date", "The absolute level without context", "The reported outcome relative to expectations"), 'C',
            "Markets frequently price expectations in advance, so unanticipated differences are analytically important."
        ),
        AstraMasterclass.Unit(
            "Probability, payoff asymmetry and expectancy", "Advanced",
            "A trading strategy needs more than a win rate. Expected payoff depends on the probability and size of both wins and losses, adjusted for spread, commission, financing, and execution shortfall. For outcomes measured in R, gross expectancy equals p×mean win R − (1−p)×mean loss magnitude R. A low-win-rate system can have positive expectancy, and a high-win-rate system can conceal rare catastrophic losses. Confidence intervals, fat tails, sample size and dependence between observations limit inference. Never assume historic independent identically distributed trades without examining the market regime and selection process.",
            "Suppose a hypothetical strategy wins 35% of trades for +3R and loses 65% for −1R. Gross expectancy is 0.35×3 − 0.65×1 = +0.40R per trade. If costs average 0.15R, net hypothetical expectancy is +0.25R. One unmodelled gap of −20R can overturn many ordinary gains.",
            "Construct a spreadsheet with 40 hypothetical trades and record outcome in R, duration, spread and maximum adverse excursion. Compute average, median, standard deviation, longest losing streak and the effect of three exceptional losses. Compare gross and net expectancy.",
            "If wins are 40% at +2R and losses are 60% at −1R, gross expectancy is:",
            listOf("+1.4R", "+0.2R", "−0.2R"), 'B',
            "0.4×2 − 0.6×1 = +0.2R before costs and tail risks."
        ),
        AstraMasterclass.Unit(
            "Backtesting, sampling and data leakage", "Advanced",
            "A credible backtest defines its rules before evaluation and separates development data from untouched test data. Look-ahead bias uses data unavailable at decision time; survivorship bias excludes failures; data snooping arises when many alternatives are tried until one performs well. Trading costs, roll conventions, exchange hours and corporate actions must be realistic for the instrument. Walk-forward and out-of-sample testing reduce some biases but cannot guarantee profitable deployment. A strategy with many tuned parameters and few independent trades needs particular skepticism. Keep complete logs of discarded configurations rather than reporting only the winning run.",
            "A rule developed by examining 2023–2025 closes, with 12 parameters chosen to maximize profit over those same dates, has only an in-sample result. Testing fixed parameters on later untouched observations gives more informative evidence, though a changing market can still invalidate the apparent advantage.",
            "Write pseudo-code for a historical experiment whose signal at time t uses only prices observed at or before t. Specify train/validation/test boundaries, a transaction-cost model and how you will avoid peeking at test performance during development. Explain why high backtested Sharpe alone is insufficient.",
            "What is look-ahead bias?",
            listOf("Including information that was unavailable at the simulated decision time", "Using a larger historical sample", "Charging realistic transaction fees"), 'A',
            "Future observations in a current decision make the historical strategy impossible to execute as tested."
        ),
        AstraMasterclass.Unit(
            "Portfolio interaction, hedges and correlated exposures", "Advanced",
            "Two positions in different instruments may express the same economic bet. A long technology-heavy index derivative and a long risk-sensitive currency can both respond to shifts in global risk appetite. Effective aggregate exposure should consider correlations, betas, notional size, currencies and changing volatility. Hedging with an imperfect instrument leaves basis risk, and short positions can introduce asymmetric liquidity or funding demands. Correlations can jump during stress, so diversification should be evaluated under adverse scenarios rather than by ticker count. Hedge effectiveness should be measured against a stated exposure and horizon.",
            "Suppose a portfolio holds P100,000 of risk exposure to an index and another P50,000 of an asset that often moves in the same direction. Labeling the positions 'stocks' and 'currencies' does not establish that losses will offset. A market-wide risk-off event could hurt both simultaneously.",
            "Build a four-position exposure table including instrument, currency, signed notional, volatility estimate and a stress-loss scenario. Calculate the combined loss in a scenario where all risky positions move adversely and compare with the sum of standalone risk budgets. State where these simplifying assumptions fail.",
            "Why can two positions in different asset classes still be poorly diversified?",
            listOf("They always have equal coupons", "They might have a common underlying risk driver", "Different tickers guarantee independent prices"), 'B',
            "Correlated economic exposures can amplify rather than diversify losses."
        ),
        AstraMasterclass.Unit(
            "Trading journal, decision hygiene and drawdowns", "Advanced",
            "A useful trading journal separates the hypothesis and pre-trade plan from execution facts and post-trade interpretation. Record timestamp and time zone, instrument and contract specification, spread, catalyst, rule, planned stop, actual fill and error codes. Risk of ruin and drawdown analysis matter because large losses require disproportionate recovery gains. Journaling every trade, including cancelled and unfilled orders, reduces hindsight bias. Fatigue, impulsive revenge trading and oversized positions can undermine otherwise reasonable rules; a defined stop-for-the-day policy is a risk-control procedure, not a profit technique.",
            "An account falls from P100,000 to P70,000 after a 30% drawdown. Returning to P100,000 requires P30,000/P70,000 = 42.86% gain. A trader who treats this as 'only 30% to recover' understates the required rebound and may take excessive risk.",
            "Create a 30-session paper-trading journal with a pre-commitment checklist and a fixed daily loss threshold. At the end of each week, audit execution deviations separately from market outcomes. Compare peak-to-trough drawdowns and time-to-recovery.",
            "After a portfolio falls by 30%, roughly what gain restores the starting amount?",
            listOf("30%", "35%", "42.86%"), 'C',
            "To recover a 30% fall, the remaining 70% must grow by 30/70 ≈ 42.86%."
        ),
        AstraMasterclass.Unit(
            "Options, futures and nonlinear payoff stress", "Advanced",
            "Futures generally have approximately linear price sensitivity in the underlying and mark-to-market obligations; options are nonlinear because their delta changes with price, volatility and time. An option buyer pays a premium and has a defined premium loss for a plain long option, whereas an uncovered option seller may face far larger losses. Delta, gamma, theta and vega are local measures requiring model assumptions. A stop order cannot be assumed to eliminate gap risk, and complex spreads can carry different margin and assignment risks than a simple payoff chart suggests.",
            "A purchased call with strike 100 and premium 6 has payoff max(S−100,0) at expiry and profit equal to payoff minus 6 per share, ignoring fees. At underlying prices 90, 100, 106 and 120, profit is −6, −6, 0 and +14. This is different from a futures long, whose gains and losses vary approximately linearly.",
            "Draw expiry payoff and profit graphs for a plain long call and put with strikes and premiums supplied by you. Analyze a scenario where implied volatility rises before expiry while spot remains fixed, and explain why the expiry diagram alone cannot determine the interim mark-to-market price.",
            "Which statement about a purchased plain call option is generally correct?",
            listOf("Its maximum premium loss is known upfront if fully paid, excluding costs", "Its profit is guaranteed by high leverage", "Its payoff is the same as an uncovered short call"), 'A',
            "Long-call downside is the paid premium under ordinary settled-contract assumptions, while gains vary with the underlying at expiry."
        ),
        AstraMasterclass.Unit(
            "Risk frameworks, fraud warnings and market discipline", "Advanced",
            "Risk controls should be explicit, testable and consistent with the product, capital, broker protections and applicable jurisdiction. Establish exposure limits by instrument, currency, correlated risk factor and portfolio; account for slippage, gaps, margin rules, outages and counterparty risk. Marketing claims of guaranteed daily profits, secret algorithms or no-loss leverage are not reliable evidence. Historical charts and paper profits do not establish audited live performance. Use a structured pre-trade review to separate evidence from speculation, and record reasons for not trading as readily as reasons for entering. Real regulations, tax consequences and broker access require current, country-specific verification.",
            "A purported mentor shows twelve profitable screenshots and promises a 5% daily return. Without the complete history, losses, fees, independent verification or product documents, those screenshots provide almost no basis for estimated expectancy or tail risk. The rational assessment is 'unverified' rather than 'high confidence'.",
            "Write a one-page paper-trading risk policy with maximum daily loss, position size, instrument approval, event-risk response and a checklist for suspicious claims. Specify concrete steps when the terminal disconnects or when price gaps invalidate a stop. Do not propose live transactions based on these exercises.",
            "Which is appropriate when a promoter promises risk-free leveraged returns?",
            listOf("Assume the screenshots are proof", "Independently verify claims and assume significant risk", "Increase position size to exploit the certainty"), 'B',
            "Guaranteed-profit claims conflict with the uncertain and leveraged nature of trading returns."
        )
    )
}
