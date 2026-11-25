"""Deterministic constrained portfolio optimization reports."""
import argparse
from decimal import Decimal, localcontext
import json
from pathlib import Path
import sys

from astra_portfolio_optimize import analyze
from astra_verified_finance import read_request


def display(value, percent=False, places=4):
    if value is None:
        return "Not applicable"
    with localcontext() as ctx:
        ctx.prec=48
        amount=Decimal(value["decimal_approximation"] if isinstance(value,dict) else value)
        return f"{amount*(100 if percent else 1):,.{places}f}"+("%" if percent else "")


def report(result):
    assets=result["assets"]
    pct=lambda v:display(v,True)
    def portfolio_row(label,p):
        return "| "+" | ".join([label]+[pct(p["weights"][asset]) for asset in assets]+[
            pct(p["expected_return"]),pct(p["volatility"]),display(p["sharpe_ratio"])])+" |"
    header="| Portfolio | "+" | ".join(assets)+" | Expected annual return | Annual volatility | Sharpe ratio |"
    divider="|---|"+"---:|"*(len(assets)+3)
    lines=[
        "# Constrained portfolio optimization","",
        "## Input assumptions","",
        f"Grid step: {pct(result['grid_step'])}; feasible allocations: {result['feasible_allocations']:,}.",
        f"Annual risk-free rate: {pct(result['risk_free_rate'])}.",
        "",
        "| Asset | Expected annual return | Minimum weight | Maximum weight |",
        "|---|---:|---:|---:|",
    ]
    for asset,mu in zip(assets,result["expected_returns"]):
        b=result["bounds_units"][asset]
        with localcontext() as ctx:
            ctx.prec=40
            lo=Decimal(b["min"])/result["grid_units"]
            hi=Decimal(b["max"])/result["grid_units"]
        lines.append(f"| {asset} | {pct(mu)} | {pct(str(lo))} | {pct(str(hi))} |")
    lines.extend(["","## Annual covariance matrix","",
                  "| Asset | "+" | ".join(assets)+" |","|---|"+"---:|"*len(assets)])
    for asset,row in zip(assets,result["covariance"]):
        lines.append("| "+" | ".join([asset]+[display(v,places=8) for v in row])+" |")
    lines.extend(["","## Correlation matrix","",
                  "| Asset | "+" | ".join(assets)+" |","|---|"+"---:|"*len(assets)])
    for asset,row in zip(assets,result["correlation"]):
        lines.append("| "+" | ".join([asset]+[display(v) for v in row])+" |")
    lines.extend(["","## Grid-optimal portfolios","",header,divider,
                  portfolio_row("Minimum variance",result["minimum_variance"])])
    if result["maximum_sharpe"] is not None:
        label=("Zero variance / positive excess return" if result["sharpe_status"]=="positive_excess_zero_variance_no_finite_sharpe"
               else "Maximum positive Sharpe")
        lines.append(portfolio_row(label,result["maximum_sharpe"]))
    lines.extend(["", "Sharpe status: "+result["sharpe_status"].replace("_"," ")+".",
                  "","## Efficient frontier","",
                  f"Nondominated allocations: {result['frontier_count']:,}."])
    if result["frontier_sampled"]:
        lines.append("Display is sampled to at most 200 points; target selection uses the full frontier.")
    lines.extend(["",header,divider])
    for i,point in enumerate(result["efficient_frontier"],1):
        lines.append(portfolio_row(str(i),point))
    if result["targets"]:
        lines.extend(["","## Minimum-risk return targets","",header,divider])
        for row in result["targets"]:
            label="At least "+pct(row["target"])
            if row["portfolio"] is None:
                lines.append("| "+label+" | "+" | ".join(["Infeasible"]+["—"]*(len(assets)+2))+" |")
            else:
                lines.append(portfolio_row(label,row["portfolio"]))
    lines.extend(["","## Method and limitations","",
                  "- Expected return is the weighted arithmetic mean of supplied annual return assumptions.",
                  "- Variance is w-transpose times covariance times w; volatility is its nonnegative square root.",
                  "- Sharpe ratio is expected annual excess return divided by annual volatility.",
                  "- Maximum Sharpe is reported only for positive excess-return portfolios; positive excess at zero variance has no finite Sharpe ratio.",
                  "- The engine exhaustively enumerates a finite long-only fully invested grid with explicit weight bounds.",
                  "- Exact rational comparisons identify grid optima, not continuous-space optima; square roots and displayed figures are approximations.",
                  "- Correlation is undefined for an asset with zero variance.",
                  "- Inputs are user estimates and have not been independently verified. No transaction costs, tax, liquidity, turnover or estimation-error adjustment.",
                  "- This report is an analytical scenario, not a forecast or investment recommendation.",""])
    return "\n".join(lines)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request")
    parser.add_argument("--format",choices=("markdown","json"),default="markdown")
    parser.add_argument("--output",help="Create a new file; never overwrite")
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
        print("Invalid or unavailable portfolio optimization report.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
