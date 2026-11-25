# Fixed-income analytics pilot

Run from the repository root with Python 3.10+:

```powershell
python desktop/astra_bond_report.py desktop/examples/bond_analysis.json
```

Use `--format json` for exact rational output or `--output bond-report.md` to create a new report without overwriting an existing file.

## Input contract

The engine values conventional, non-callable, fixed-rate bullet bonds immediately after a regular coupon date. Supply one currency, positive face value, annual coupon rate as a decimal string, payments per year (1, 2, 4 or 12), and 1–120 remaining payment periods. Set basis to `regular_fixed_rate_bullet_on_coupon_date`.

Supply either `annual_yield` (nominal annual yield compounded at the coupon frequency) or `market_price` (the total price for the specified face value), not both. Yield range is -90% to 200%. The market-price route uses bounded numerical bisection with 14 decimal places of annual yield resolution and discloses the residual. It is not an exact closed-form YTM calculation.

Outputs include price, price per 100 face, current yield, Macaulay duration, modified duration, convexity, linear DV01, and exact modeled loss after a one-basis-point upward yield change when in range. Up to ten unique integer yield shocks of -1,000 to +1,000 basis points are supported, provided the shocked yield stays in range. The report compares exact repricing to the second-order duration-convexity estimate and displays their difference.

All cash flows are assumed to be received as scheduled. No accrued interest, actual settlement dates, day-count conventions, irregular coupons, callable or puttable bonds, inflation-linked securities, credit/default modeling, FX, live prices, tax or fees. Sensitivity is with respect to nominal annual yield. The report is descriptive and is not a market quotation, forecast or recommendation. GUI integration and target-hardware validation remain pending.
