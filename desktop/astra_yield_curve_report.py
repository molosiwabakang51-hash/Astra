"""Deterministic yield-curve and cash-flow valuation report."""
import argparse
from decimal import Decimal, localcontext
import json
from pathlib import Path
import sys

from astra_yield_curve import analyze
from astra_verified_finance import read_request


def display(value, places=6, percent=False):
    with localcontext() as ctx:
        ctx.prec=64
        amount=Decimal(value)*(100 if percent else 1)
        return f"{amount:,.{places}f}"+("%" if percent else "")


def report(result):
    c=result["currency"]
    money=lambda value:c+" "+display(value,2)
    pct=lambda value:display(value,4,True)
    lines=[
        "# Yield-curve valuation and interest-rate risk","",
        f"Currency: {c}",
        f"Curve input: {result['curve_kind'].replace('_',' ')}.",
        "Interpolation: log-linear discount factors.",
        "Rates: continuously compounded annual rates.","",
        "## Curve nodes","",
        "| Maturity (months) | Discount factor | Continuous spot rate |",
        "|---:|---:|---:|",
    ]
    for row in result["nodes"]:
        lines.append(f"| {row['months']} | {display(row['discount_factor'],8)} | {pct(row['continuous_zero'])} |")
    lines.extend(["","## Requested maturities","","| Maturity (months) | Discount factor | Continuous spot rate |",
                  "|---:|---:|---:|"])
    for row in result["queries"]:
        lines.append(f"| {row['months']} | {display(row['discount_factor'],8)} | {pct(row['continuous_zero'])} |")
    lines.extend(["","## Forward rates",""])
    if result["forwards"]:
        lines.extend(["| From month | To month | Continuous annual forward rate |","|---:|---:|---:|"])
        for row in result["forwards"]:
            lines.append(f"| {row['start_month']} | {row['end_month']} | {pct(row['continuous_forward'])} |")
    else:
        lines.append("No forward intervals requested.")
    if "valuation" in result:
        v=result["valuation"]
        lines.extend(["","## Cash-flow valuation","",
                      f"**Modeled present value: {money(v['price'])}**","",
                      "| Maturity (months) | Cash flow | Discount factor | Present value |",
                      "|---:|---:|---:|---:|"])
        for row in v["cashflows"]:
            lines.append(f"| {row['months']} | {money(row['amount'])} | {display(row['discount_factor'],8)} | {money(row['present_value'])} |")
        lines.extend(["","## Interest-rate sensitivity","",
                      f"- Parallel DV01 (central): {c} {display(v['parallel_dv01_central'],6)}.",
                      f"- Parallel duration (central): {display(v['parallel_duration_central'])} years.",
                      f"- Parallel convexity (central): {display(v['parallel_convexity_central'])} years squared.",
                      "","| Key maturity (months) | Central DV01 | Key-rate duration |","|---:|---:|---:|"])
        for row in v["key_rates"]:
            lines.append(f"| {row['months']} | {c} {display(row['dv01_central'],6)} | {display(row['duration_central'])} |")
        lines.extend(["",f"Selected key-rate DV01 sum: {c} {display(v['selected_key_rate_dv01_sum'],6)}.",
                      "All curve nodes included: "+("yes" if v["all_curve_nodes_selected"] else "no")+".",
                      v["scope"],"","## Parallel yield shocks",""])
        if v["parallel_scenarios"]:
            lines.extend(["| Shock (bp) | Stressed price | Price change | Relative change |","|---:|---:|---:|---:|"])
            for row in v["parallel_scenarios"]:
                lines.append(f"| {row['shock_bps']:+d} | {money(row['price'])} | {money(row['price_change'])} | {pct(row['relative_change'])} |")
        else:
            lines.append("No scenarios requested.")
    lines.extend(["","## Method and limitations","",
                  "- Times are integer months divided by twelve; there are no actual settlement dates or day-count adjustments.",
                  "- Discount factors are positive; negative continuous rates and forwards are permitted.",
                  "- Regular par bootstrap requires a complete coupon grid and nonnegative par coupons. No market quote or instrument convention is inferred.",
                  "- The curve is anchored at discount factor one at time zero. No extrapolation beyond the last node.",
                  "- Key-rate DV01 uses symmetric one-basis-point bumps to one continuous zero-rate node at a time; interpolation is rebuilt.",
                  "- Parallel sensitivity uses symmetric bumps to every node. Central differences are numerical, and key-rate sums need not match exactly.",
                  "- Cash flows are deterministic and nonnegative. No credit, option, liquidity, tax, fee or currency-conversion model.",
                  "- Inputs are not independently verified, and outputs are not market quotations, investment recommendations or forecasts.",
                  "- JSON retains high-precision decimal approximations rather than exact rational curve values.",""])
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
        print("Invalid or unavailable yield-curve analysis.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
