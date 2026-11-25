# Historical strategy backtesting

Run from the repository root with Python 3.10+:

```powershell
python desktop/astra_backtest_report.py desktop/examples/strategy_backtest.json
```

Use --format json for the complete period ledger, including start-of-period weights and strategy parameters; use --output backtest.md to create a new report without overwriting.

Supply chronological aligned periodic simple total returns as decimal strings, one reporting currency, positive initial wealth, 1–6 assets, 2–2520 observations and 1–4 strategies. Confirm basis chronological_aligned_periodic_total_returns. Optional benchmark returns must align with every observation. All weights are long-only and sum exactly to one.

Three predeclared rules are supported: buy_and_hold, fixed_weight with rebalance_every in observation periods, and lagged_momentum with rebalance_every, lookback and top_k. Momentum ranks compounded returns over completed observations only; equal scores are resolved in input asset order. The initial weights are used during warm-up. Trades occur before the current period return is applied. Initial holdings are assumed pre-existing and do not incur modeled fees.

The proportional transaction cost is specified in basis points of total gross traded value (both purchases and sales). Post-cost wealth is solved numerically so target holdings and trading fees reconcile to available wealth. Cumulative one-way turnover is the sum of half gross traded value divided by pre-trade wealth. End-period drawdown includes the initial wealth peak, but does not capture intraperiod prices. Zero wealth is absorbing.

The report compares net cumulative return, fees, turnover, drawdown, benchmark-relative performance and a reconciled period ledger. Decision logic does not access future observations, but this does not establish unbiased data, universe selection, parameter selection, or real execution. No actual dates, market data verification, taxes, market impact, slippage beyond the stated proportional fee, external flows or investment advice. No model or third-party dependencies. GUI integration and practical validation remain pending.
