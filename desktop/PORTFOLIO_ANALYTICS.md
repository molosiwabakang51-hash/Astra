# Portfolio analytics pilot

Requires Python 3.10+ and no external packages, model or internet access.

Run the included synthetic example from the repository root:

```powershell
python desktop/astra_portfolio_report.py desktop/examples/portfolio_analysis.json
```

Export a new Markdown report:

```powershell
python desktop/astra_portfolio_report.py desktop/examples/portfolio_analysis.json --output portfolio-report.md
```

For exact rational results and structured data, use `--format json`. Existing output files are never overwritten.

## Input contract

Supply a single reporting currency, ISO start/end dates, and explicit basis `unchanged_holdings_no_flows_income_excluded`. The analysis covers up to 50 long-only positions with positive opening values, nonnegative closing values and nonnegative separately distributed cash income. Enter monetary values as decimal strings. Do not use this method for portfolios with purchases, sales, contributions, withdrawals or reinvested distributions. Convert any foreign-currency values consistently before input; the engine does not convert FX.

An optional benchmark total return uses a decimal ratio (e.g. "0.12" for 12%), covering the same period and currency. Up to ten scenarios may specify instantaneous proportional closing-value price shocks, e.g. "-0.20" for a 20% decline. Omitted holdings are unchanged.

The report reconciles capital and income returns, position contributions, current concentration (HHI and effective number of positions), benchmark-relative return in percentage points, and hypothetical stress losses. JSON retains exact rational values; report figures are rounded.

No live pricing, independent input verification, annualisation, risk-adjusted returns, cash-flow-adjusted performance, trading, fees or taxes. Concentration is not a complete risk assessment, and stress scenarios are not forecasts. Reports are descriptive analytical aids, not investment recommendations or a certified commercial product. GUI integration and real-user validation remain pending.
