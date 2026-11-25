"""Deterministic cash-flow-adjusted performance reporting."""
import argparse
from decimal import Decimal, localcontext
import json
from pathlib import Path
import sys

from astra_cashflow_performance import analyze
from astra_verified_finance import read_request


def display(value,currency):
    if value is None:
        return "Not applicable"
    with localcontext() as ctx:
        ctx.prec=48
        amount=Decimal(value["decimal_approximation"])
        if value["units"]=="decimal_ratio":
            return f"{amount*100:,.4f}%"
        return f"{currency} {amount:,.2f}"


def percent(value):
    if value is None:
        return "Not applicable"
    with localcontext() as ctx:
        ctx.prec=48
        return f"{Decimal(value)*100:,.4f}%"


def report(result):
    currency=result["currency"]
    fmt=lambda value:display(value,currency)
    mwr=result["money_weighted"]
    lines=[
        "# Cash-flow-adjusted portfolio performance",
        "",
        f"Period: {result['start']} to {result['end']} ({result['days']} days)",
        f"Reporting currency: {currency}",
        "",
        "## Performance summary",
        "",
        "| Measure | Result |",
        "|---|---:|",
    ]
    for key,label in (
        ("opening_value","Opening market value"),("closing_value","Closing market value"),
        ("net_external_flows","Net external contributions"),
        ("investment_gain","Investment gain after adjusting for external flows"),
        ("time_weighted_return","Cumulative time-weighted return"),
        ("modified_dietz","Modified Dietz period return"),
        ("modified_dietz_denominator","Modified Dietz weighted capital"),
    ):
        lines.append(f"| {label} | {fmt(result[key])} |")
    lines.append(f"| Annualized time-weighted return | {percent(result['annualized_time_weighted_return'])} |")
    lines.append(f"| Money-weighted period return | {percent(mwr.get('period_return'))} |")
    lines.append(f"| Annualized money-weighted IRR | {percent(mwr['annualized_return'])} |")
    lines.extend(["",f"Money-weighted return status: {mwr['status'].replace('_',' ')}.",
                  result["annualization_basis"]+"."])
    if mwr["status"]=="numerical_estimate":
        lines.extend([f"Numerical NPV residual: {mwr['npv_residual']} {currency}.",
                      "Annualized IRR is a numerical rate using ACT/365 fixed; it may extrapolate a short reporting period."])
    else:
        lines.append("The solver does not select an arbitrary root for a nonconventional series, an unbracketed root or an excessive numerical residual.")
    lines.extend(["","## Subperiod reconciliation","",
                  "| Start | End | Opening after flow | Closing before flow | External flow at end | Closing after flow | Subperiod return |",
                  "|---|---|---:|---:|---:|---:|---:|"])
    for row in result["segments"]:
        lines.append("| "+" | ".join([row["start"],row["end"],fmt(row["opening_after_flow"]),
                     fmt(row["closing_before_flow"]),fmt(row["external_flow_at_end"]),
                     fmt(row["value_after_flow"]),fmt(row["return"])])+" |")
    lines.extend([
        "", "## Method and limitations", "",
        "- Positive external flows are contributions; negative flows are withdrawals from the portfolio.",
        "- Each flow has an immediately preceding total portfolio valuation; the post-flow valuation equals that valuation plus the signed flow.",
        "- Retained income is already included in portfolio market value. Distributions paid outside the portfolio must be recorded as withdrawals.",
        "- TWR geometrically links subperiod returns. It assumes all external flows and their boundary valuations are complete.",
        "- Modified Dietz uses time-weighted external capital and is an approximation to cash-flow-adjusted performance, not exact TWR.",
        "- MWR solves the investor cash-flow NPV equation for a single-sign-change series in the disclosed annual rate bracket.",
        "- No external flow at the opening or final valuation boundary, no same-date multiple events, and no complete intermediate withdrawal is supported.",
        "- Annualized TWR is omitted for periods shorter than 365 days. MWR also displays a period-equivalent return when a root is found.",
        "- Displayed amounts and percentages are rounded. JSON preserves exact rational TWR and Dietz and numerical IRR estimates.",
        "- No external price, valuation, currency, fee or tax verification. No investment recommendation or compliance certification.",
        "",
    ])
    return "\n".join(lines)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request")
    parser.add_argument("--format",choices=("markdown","json"),default="markdown")
    parser.add_argument("--output",help="Create a new report without overwriting")
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
        print("Invalid or unavailable cash-flow performance report.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
