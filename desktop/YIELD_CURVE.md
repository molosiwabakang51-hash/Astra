# Yield-curve analytics

Run from the repository root with Python 3.10+:

```powershell
python desktop/astra_yield_curve_report.py desktop/examples/yield_curve.json
```

Use --format json for numerical detail or --output curve-report.md to create a new report without overwriting.

## Input conventions

Specify one reporting currency and a curve with unique, strictly increasing integer-month maturities (1–1200 months, up to 120 nodes). Three input formats are supported:

- continuous_spot: continuously compounded annual zero rates as decimal strings.
- discount_factors: positive discount factors as decimal strings.
- regular_par: nonnegative nominal annual par coupon rates, with payments_per_year of 1, 2, 4 or 12 and every coupon-grid maturity from the first payment onward. A par instrument pays its regular coupon and redeems at face value. The bootstrap rejects nonpositive or out-of-range factors.

All interpolation is log-linear in discount factors, anchored at one at month zero. There is no extrapolation beyond the final node. Negative spot or forward rates can occur and are not themselves an error. This is a deterministic curve construction, not calibration to observed instrument prices or proof of market consistency.

Queries return discount factors and continuously compounded spot rates. Forward intervals return annual continuous forward rates. Optional nonnegative cash flows can be discounted against the curve; all cash flows must have increasing unique maturities covered by the curve.

Key-rate and parallel risk use symmetric one-basis-point bumps to continuous zero rates. Repricing uses the same interpolation. Central DV01 is half the difference between the down-bumped and up-bumped prices. Central duration and convexity are numerical finite differences. Key-rate DV01 sums approximately reconcile to parallel DV01 when all nodes are selected; the output discloses partial node selection. Up to ten explicit parallel shocks are supported.

No actual settlement dates, accrued interest, irregular coupon schedules, day-count conventions, issuer credit, options, liquidity, FX, fees, taxes, or live market data. Decimal calculations use a 50-digit working precision; this is not exact rational arithmetic for logarithms or exponentials. Results are descriptive analytical outputs, not investment recommendations or forecasts. GUI integration and practical validation remain outstanding.
