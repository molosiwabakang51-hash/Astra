# Historical portfolio risk

Run from the repository root using Python 3.10+:

```powershell
python desktop/astra_historical_risk_report.py desktop/examples/historical_risk.json
```

Use --format json for exact rational moments and empirical tail estimates, or --output risk-report.md to create a new file without overwriting.

Supply one to six distinct assets, 2–2520 aligned equally spaced periodic simple total-return observations (decimal strings), one nonnegative weight per asset summing exactly to one, a constant periodic risk-free return, confidence between 50% and 99%, and periods_per_year of 12, 52 or 252. Confirm basis aligned_periodic_total_returns_constant_weights. Optionally supply a benchmark return for every observation. The file limit is 512 KiB.

The portfolio is rebalanced to the specified weights at each observation boundary, without transaction costs or external flows. Sample covariance uses n−1. Annual arithmetic mean and covariance multiply periodic values by frequency; annual volatility uses the square root of frequency. These are conventional scaling assumptions, not a serial-dependence adjustment. Geometric annualization is omitted for samples shorter than a year. Drawdown uses a wealth index starting at one.

Historical VaR is the inverse empirical loss CDF. Expected shortfall integrates the upper empirical quantiles over exactly the requested tail probability, including fractional boundary observations. Both use one-period losses defined as minus return, so negative losses denote gains. Fewer than five tail observation equivalents triggers a thin-sample flag; even larger samples do not establish predictive accuracy.

Benchmark metrics include sample beta, annualized tracking error, arithmetic active return and information ratio. Zero-denominator ratios are omitted. Annual covariance and arithmetic means can inform optimization assumptions, but their sampling uncertainty, frequency and rounding need review; do not automatically treat historical estimates as forecasts.

The engine uses exact rational moments and tail comparisons, numerical square roots and Decimal wealth compounding. No verified market data, date alignment detection, fees, taxation, liquidity modeling, regulatory risk certification or investment recommendation. Monte Carlo, graphical integration and target-computer performance testing remain outstanding.
