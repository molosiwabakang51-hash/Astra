"""Report bounded historical block-bootstrap simulations."""
import argparse
from decimal import Decimal, localcontext
import json
from pathlib import Path
import sys

from astra_bootstrap_simulation import analyze
from astra_verified_finance import unique_object, invalid_constant

MAX_BYTES=524288


def read_request(path):
    with Path(path).open("rb") as source:
        raw=source.read(MAX_BYTES+1)
    if len(raw)>MAX_BYTES:
        raise ValueError("Simulation request exceeds 512 KiB.")
    return json.loads(raw.decode("utf-8"),object_pairs_hook=unique_object,
                      parse_constant=invalid_constant)


def display(value,percent=False,places=2):
    with localcontext() as ctx:
        ctx.prec=60
        amount=Decimal(value)*(100 if percent else 1)
        if abs(amount)>=Decimal("1e12") or (amount and abs(amount)<Decimal("1e-8")):
            return f"{amount:.6E}"+("%" if percent else "")
        return f"{amount:,.{places}f}"+("%" if percent else "")


def report(result):
    currency=result["currency"]
    money=lambda value:currency+" "+display(value)
    pct=lambda value:display(value,True)
    lines=["# Historical block-bootstrap portfolio simulation","",
           "## Simulation assumptions","",
           f"- Initial wealth: {money(result['initial_wealth'])}.",
           f"- Target wealth: {money(result['target_wealth'])}.",
           f"- Observations: {result['observations']}; paths: {result['paths']:,}.",
           f"- Horizon: {result['horizon_periods']} observation periods; block length: {result['block_length']}.",
           f"- Seed: {result['seed']}; tail confidence: {pct(result['confidence'])}.",
           "",
           "| Allocation | "+" | ".join(result["assets"])+" |",
           "|---|"+"---:|"*len(result["assets"])]
    for row in result["allocations"]:
        lines.append("| "+" | ".join([row["name"]]+[pct(w) for w in row["weights"]])+" |")
    lines.extend(["","## Terminal wealth distribution","",
                  "| Allocation | Mean | 5th percentile | Median | 95th percentile |",
                  "|---|---:|---:|---:|---:|"])
    for row in result["allocations"]:
        q=row["terminal_quantiles"]
        lines.append("| "+" | ".join([row["name"],money(row["mean_terminal_wealth"]),
                                      money(q["5"]),money(q["50"]),money(q["95"])])+" |")
    lines.extend(["","## Conditional downside risk","",
                  "| Allocation | Below initial wealth | Below target | VaR loss | Expected shortfall loss | 95th percentile max drawdown |",
                  "|---|---:|---:|---:|---:|---:|"])
    for row in result["allocations"]:
        lines.append("| "+" | ".join([row["name"],pct(row["loss_path_fraction"]),
                    pct(row["below_target_path_fraction"]),pct(row["terminal_var_loss"]),
                    pct(row["terminal_expected_shortfall_loss"]),pct(row["p95_max_drawdown"])])+" |")
    lines.extend(["",f"Tail path equivalents: {display(result['tail_path_equivalents'])}."])
    if result["thin_simulated_tail"]:
        lines.append("Warning: fewer than five simulated path equivalents in the specified tail.")
    if result["paired_comparisons"]:
        lines.extend(["","## Paired allocation comparisons","",
                      "| Allocation | Baseline | Outperform paths | Tie paths | Underperform paths | Mean terminal difference |",
                      "|---|---|---:|---:|---:|---:|"])
        for row in result["paired_comparisons"]:
            lines.append("| "+" | ".join([row["allocation"],row["baseline"],
                str(row["outperform_path_count"]),str(row["tie_path_count"]),
                str(row["underperform_path_count"]),money(row["mean_terminal_wealth_difference"])])+" |")
    lines.extend(["","## Method and limitations","",
        "- Uniformly sample overlapping noncircular historical blocks with replacement; truncate the final block at the horizon.",
        "- Preserve aligned cross-asset observations and within-block ordering. Dependence across block boundaries is not preserved.",
        "- All allocations use identical sampled paths and frictionless constant-weight rebalancing.",
        "- Terminal loss is one minus terminal wealth divided by initial wealth; negative losses represent gains.",
        "- Quantiles use inverse empirical CDF; expected shortfall uses fractional upper-tail mass.",
        "- Simulated frequencies are conditional on the chosen sample, block length and assumptions. They are not calibrated probabilities of future investment outcomes.",
        "- No estimation-uncertainty adjustment, external flows, taxes, fees, liquidity constraints or verified market data.",
        "- Fifty-digit Decimal working precision; seeded reproducibility within the same Python runtime. No investment recommendation.",""])
    return "\n".join(lines)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request")
    parser.add_argument("--format",choices=("markdown","json"),default="markdown")
    parser.add_argument("--output")
    args=parser.parse_args(argv)
    try:
        result=analyze(read_request(args.request))
        output=report(result) if args.format=="markdown" else json.dumps(result,indent=2,allow_nan=False)+"\n"
        if args.output:
            with Path(args.output).open("x",encoding="utf-8",newline="\n") as target:
                target.write(output)
        else:
            sys.stdout.write(output)
        return 0
    except (OSError,ValueError,RuntimeError,TypeError,RecursionError,ArithmeticError):
        print("Invalid or unavailable portfolio simulation.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
