"""Deterministic historical risk reports with bounded local JSON input."""
import argparse
from decimal import Decimal, localcontext
import json
from pathlib import Path
import sys

from astra_historical_risk import analyze
from astra_verified_finance import unique_object, invalid_constant

MAX_INPUT_BYTES=524288


def read_request(path):
    with Path(path).open("rb") as source:
        raw=source.read(MAX_INPUT_BYTES+1)
    if len(raw)>MAX_INPUT_BYTES:
        raise ValueError("Historical input exceeds 512 KiB.")
    return json.loads(raw.decode("utf-8"),object_pairs_hook=unique_object,
                      parse_constant=invalid_constant)


def display(value, percent=False, places=4):
    if value is None:
        return "Not applicable"
    with localcontext() as ctx:
        ctx.prec=48
        x=Decimal(value["decimal_approximation"] if isinstance(value,dict) else value)
        if percent:
            x*=100
        # Keep unusually large compounded returns legible.
        if abs(x)>=Decimal("1e12") or (x and abs(x)<Decimal("1e-8")):
            return f"{x:.6E}"+("%" if percent else "")
        return f"{x:,.{places}f}"+("%" if percent else "")


def report(result):
    pct=lambda x:display(x,True)
    stats=[("Portfolio",result["portfolio"])]
    stats.extend(zip(result["assets"],result["asset_statistics"]))
    if "benchmark" in result:
        stats.append(("Benchmark",result["benchmark"]["statistics"]))
    lines=["# Historical portfolio risk and performance","","## Basis","",
           f"Observations: {result['observations']:,}; periods per year: {result['periods_per_year']}.",
           f"Periodic risk-free return: {pct(result['period_risk_free_rate'])}.",
           "Constant-weight, frictionless rebalancing of aligned periodic total returns.","",
           "| Asset | Portfolio weight |","|---|---:|"]
    for a,w in zip(result["assets"],result["weights"]):
        lines.append(f"| {a} | {pct(w)} |")
    lines.extend(["","## Return and risk","",
                  "| Series | Cumulative return | Annual arithmetic return | Annual geometric return | Annual volatility | Annual Sharpe | Max drawdown loss |",
                  "|---|---:|---:|---:|---:|---:|---:|"])
    for name,s in stats:
        w=s["wealth"]
        lines.append("| "+" | ".join([name,pct(w["cumulative_return"]),
            pct(s["annualized_arithmetic_mean"]),pct(w["annualized_geometric_return"]),
            pct(s["annualized_volatility"]),display(s["annualized_sharpe"]),
            pct(w["maximum_drawdown_loss"])])+" |")
    lines.extend(["","## Historical tail risk (one observation period)","",
                  "| Series | Confidence | VaR loss | Expected shortfall loss | Tail equivalents | Thin sample? |",
                  "|---|---:|---:|---:|---:|---|"])
    for name,s in stats:
        t=s["historical_tail"]
        lines.append("| "+" | ".join([name,pct(t["confidence"]),pct(t["var_loss"]),
            pct(t["expected_shortfall_loss"]),display(t["tail_observation_equivalents"]),
            "Yes" if t["thin_tail_sample"] else "No"])+" |")
    lines.extend(["","Negative losses denote gains. Thin tail means fewer than five observation equivalents.","",
                  "## Annualized sample covariance","",
                  "| Asset | "+" | ".join(result["assets"])+" |",
                  "|---|"+"---:|"*len(result["assets"])])
    for a,row in zip(result["assets"],result["annualized_covariance"]):
        lines.append("| "+" | ".join([a]+[display(x,places=8) for x in row])+" |")
    lines.extend(["","## Sample correlation","",
                  "| Asset | "+" | ".join(result["assets"])+" |",
                  "|---|"+"---:|"*len(result["assets"])])
    for a,row in zip(result["assets"],result["correlation"]):
        lines.append("| "+" | ".join([a]+[display(x) for x in row])+" |")
    if "benchmark" in result:
        b=result["benchmark"]
        lines.extend(["","## Benchmark-relative risk","",
                      f"- Sample beta: {display(b['beta'])}.",
                      f"- Annualized tracking error: {pct(b['annualized_tracking_error'])}.",
                      f"- Annualized arithmetic active return: {pct(b['annualized_arithmetic_active_return'])}.",
                      f"- Annualized information ratio: {display(b['annualized_information_ratio'])}."])
    lines.extend(["","## Method and limitations","",
                  "- All observations must be aligned, equally spaced total returns in the same reporting currency. Alignment is attested by the input provider, not independently checked.",
                  "- Sample covariance uses n−1. Arithmetic means and covariance are scaled by periods per year; volatility by its square root.",
                  "- Sharpe uses a constant periodic risk-free rate and sample volatility. Ratios with zero denominators are not reported.",
                  "- Geometric annual return is omitted when fewer than one year's observations are supplied. Maximum drawdown includes initial wealth of one.",
                  "- VaR uses the inverse empirical loss CDF; expected shortfall averages the exact upper-tail probability mass, including fractional boundary observations.",
                  "- VaR and ES describe the supplied sample, not predicted losses, probabilities of future outcomes or regulatory capital.",
                  "- No transaction costs, taxes, liquidity constraints, serial-dependence correction, market-data verification or investment recommendations.",
                  "- Exact rational moments and tail estimates are retained in JSON; geometric wealth and square roots use Decimal approximations.",""])
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
        print("Invalid or unavailable historical risk report.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
