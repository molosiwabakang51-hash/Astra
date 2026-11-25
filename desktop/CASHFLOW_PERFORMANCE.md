# Cash-flow-adjusted performance pilot

Run from the repository root:

```powershell
python desktop/astra_cashflow_report.py desktop/examples/cashflow_performance.json
```

Use `--format json` for rational and numerical results, or `--output performance.md` to create a new report without overwriting.

Supply a single reporting currency, opening and closing dates and market values, basis `pre_flow_valuations_total_portfolio_value`, and up to 60 chronological external-flow events strictly between the start and end dates. Every event must specify the immediately preceding total portfolio valuation and signed flow (positive contribution, negative withdrawal). Post-flow value is pre-flow value plus the flow and must be positive. Retained income is included in market value; cash distributions leaving the portfolio are withdrawals. Do not use partial, inferred or stale boundary valuations.

TWR geometrically links the subperiod returns using exact rational arithmetic. Modified Dietz uses ACT-based remaining-period weights and requires positive weighted capital. Annualized TWR uses ACT/365 fixed and is omitted for periods under 365 days. Money-weighted return solves the investor NPV equation numerically on the annualized interval -99.99% to +1000%, only for one chronological cash-flow sign change, with a residual check. A nonconventional series can have multiple roots or no root; the engine intentionally abstains rather than choosing one. The IRR period-equivalent return is also supplied.

No external flows on boundary dates, multiple same-day events, complete intermediate withdrawals, multi-currency conversion, independent data validation, security-level attribution or fee/tax modeling. This is a deterministic analytical pilot, not a GIPS compliance certification or an investment recommendation. GUI integration and practical validation remain pending.
