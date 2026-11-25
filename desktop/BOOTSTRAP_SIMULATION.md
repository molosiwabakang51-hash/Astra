# Historical block-bootstrap portfolio simulation

Run from the repository root with Python 3.10+:

```powershell
python desktop/astra_simulation_report.py desktop/examples/bootstrap_simulation.json
```

Use --format json for numerical results or --output simulation.md to create a new report without overwriting.

Supply aligned, equally spaced periodic total returns as decimal strings, a reporting currency, positive initial wealth and nonnegative target wealth. Confirm basis aligned_periodic_total_returns_constant_weights. Up to six assets, 2520 observations and four long-only allocations are supported. Every allocation must sum exactly to one.

Choose 1–10,000 paths, 1–252 horizon observation periods, a block length no greater than the sample or horizon, an explicit integer seed and 50–99% confidence. The workload is capped at one million allocation-period updates. The frequency and horizon units must match the supplied data; there is no automatic date or frequency inference.

Blocks are sampled uniformly from overlapping, noncircular historical blocks, with replacement. Block length one gives independent empirical row sampling; longer blocks preserve within-block ordering, not dependence across block boundaries. All assets and candidate allocations share the sampled rows. The portfolio is frictionlessly rebalanced to constant weights. Zero wealth is absorbing.

Reports include terminal wealth mean and quantiles, frequency of terminal loss and target shortfall, terminal VaR and ES, maximum drawdown quantiles, and paired comparisons against the first allocation. Loss is one minus the terminal-to-initial wealth ratio. Empirical VaR uses the inverse CDF; expected shortfall averages exact upper-tail probability mass including fractional boundary observations. A thin simulated tail is flagged. The underlying historical sample may itself be very limited even when many paths are drawn.

Fifty-digit Decimal calculations and a seeded Python pseudorandom generator provide reproducibility under the same runtime and inputs. No independent data validation, future-probability calibration, model uncertainty adjustment, taxes, costs, liquidity constraints, trading or external flows. Simulations are conditional scenarios, not forecasts or investment recommendations. GUI integration and target-hardware performance verification remain pending.
