# Constrained portfolio optimization

Run from the repository root using Python 3.10+:

```powershell
python desktop/astra_optimization_report.py desktop/examples/portfolio_optimization.json
```

Use --format json for exact rational weights, returns and variances, or --output optimization.md to create a new report without overwriting.

Supply 2–6 distinct assets, annual arithmetic expected returns and a symmetric positive semidefinite annual covariance matrix as decimal strings. Supply a consistent annual risk-free rate. The engine does not estimate these from historical prices or predict future returns.

The long-only fully invested allocation grid uses 4–100 integer units, subject to a maximum of 150,000 unconstrained candidate allocations. For example, 20 units means five-percentage-point increments. Optional asset-specific min_units and max_units constrain the allocation. Coarser grids are necessary for larger asset sets. Infeasible constraints are rejected.

All feasible allocations are enumerated using exact integer comparisons of return and variance. The engine identifies the minimum-variance portfolio, maximum positive Sharpe-ratio portfolio, nondominated efficient frontier and minimum-variance allocation for each requested return target. It reports no finite Sharpe ratio when positive excess return occurs with zero variance, and does not optimize a nonpositive Sharpe ratio. Covariance validation is exact, including singular matrices. Correlation is undefined when either asset has zero variance.

The frontier is grid-optimal, not continuous-space optimal. The report displays at most 200 representative frontier points; targets are selected using the complete frontier. Annual volatility and Sharpe ratios use high-precision numerical square roots. All assumptions are user-supplied; no estimation error, turnover, transaction costs, liquidity, taxation, market-data validation or investment recommendation. No model, network or third-party package is needed. GUI integration and real-world validation remain pending.
