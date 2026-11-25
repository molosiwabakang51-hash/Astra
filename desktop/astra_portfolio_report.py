"""Local portfolio analysis and auditable Markdown reports."""
import argparse
from decimal import Decimal, localcontext
import json
from pathlib import Path
import sys

from astra_portfolio_analytics import analyze
from astra_verified_finance import read_request


def format_metric(value, currency=None):
    if value is None:
        return "Not applicable"
    with localcontext() as ctx:
        ctx.prec = 48
        amount = Decimal(value["decimal_approximation"])
        if value["units"] == "decimal_ratio":
            return f"{amount*100:,.2f}%"
        if value["units"] == "currency_amount":
            return f"{currency} {amount:,.2f}"
        return f"{amount:,.2f}"


def report(result):
    c = result["currency"]
    fmt = lambda value: format_metric(value,c)
    lines = [
        "# Portfolio performance and risk analysis",
        "",
        f"Reporting period: {result['start']} to {result['end']} ({result['days']} days)",
        f"Reporting currency: {c}",
        "",
        "## Executive summary",
        "",
        "| Measure | Result |",
        "|---|---:|",
    ]
    for key,label in (
        ("opening_value","Opening market value"),("closing_value","Closing market value"),
        ("cash_income","Distributed cash income"),("capital_gain","Capital gain"),
        ("total_gain","Total gain"),("capital_return","Capital return"),
        ("income_return","Income return"),("total_return","Total return"),
    ):
        lines.append(f"| {label} | {fmt(result[key])} |")
    if "benchmark" in result:
        benchmark = result["benchmark"]
        active = fmt(benchmark["active_return"]).replace("%"," pp")
        lines.extend([f"| Benchmark total return | {fmt(benchmark['total_return'])} |",
                      f"| Active return | {active} |"])
    lines.extend(["","## Position-level attribution","",
                  "| Holding | Opening value | Closing value | Cash income | Total return | Contribution (pp) | Closing weight |",
                  "|---|---:|---:|---:|---:|---:|---:|"])
    for row in result["positions"]:
        contribution = fmt(row["return_contribution"]).replace("%","")
        lines.append("| " + " | ".join([row["symbol"],fmt(row["opening_value"]),
                     fmt(row["closing_value"]),fmt(row["cash_income"]),fmt(row["total_return"]),
                     contribution,fmt(row["closing_weight"])])+" |")
    lines.extend(["","## Concentration",""])
    concentration = result["concentration"]
    if concentration is None:
        lines.append("Closing market value is zero; concentration is undefined.")
    else:
        lines.extend(["| Measure | Result |","|---|---:|"])
        for key,label in (("largest_weight","Largest holding"),("top_three_weight","Top three holdings"),
                          ("hhi","Herfindahl–Hirschman index (0–10,000)"),
                          ("effective_positions","Effective number of equally weighted positions")):
            lines.append(f"| {label} | {fmt(concentration[key])} |")
        lines.extend(["",concentration["scope"]])
    lines.extend(["","## Hypothetical stress tests",""])
    if result["scenarios"]:
        lines.extend(["| Scenario | Value change | Stressed value | Portfolio change |",
                      "|---|---:|---:|---:|"])
        for scenario in result["scenarios"]:
            lines.append("| "+" | ".join([scenario["name"],fmt(scenario["value_change"]),
                         fmt(scenario["stressed_value"]),fmt(scenario["portfolio_change"])])+" |")
        for scenario in result["scenarios"]:
            lines.extend(["",f"**{scenario['name']} — assumptions**",""])
            for holding in scenario["positions"]:
                lines.append(f"- {holding['symbol']}: {fmt(holding['shock'])} price shock; {fmt(holding['value_change'])} value impact.")
        lines.extend(["",result["scenarios"][0]["scope"]])
    else:
        lines.append("No stress scenarios supplied.")
    lines.extend(["","## Basis and limitations","",
        "- Opening and closing market values refer to unchanged holdings, without trades or external flows.",
        "- Cash income is separately distributed and excluded from closing market value.",
        "- Total gain = closing market value − opening market value + distributed cash income.",
        "- Total return = total gain ÷ opening market value.",
        "- Contribution = position-level total gain ÷ portfolio opening market value.",
        "- Concentration uses closing market value, with HHI equal to the sum of squared weights × 10,000.",
        "- Displayed figures are rounded; the JSON result retains exact rational values.",
        "- All prices, income and benchmark data are supplied by the user and have not been independently validated.",
        "- No currency conversion, annualisation, reinvestment, fees, taxes or cash-flow-adjusted performance.",
        "- Hypothetical scenarios are not forecasts. This report does not recommend investments.",
        ""])
    if "benchmark" in result:
        lines.extend([result["benchmark"]["scope"],""])
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request",help="Portfolio JSON input")
    parser.add_argument("--format",choices=("json","markdown"),default="markdown")
    parser.add_argument("--output",help="Create a new report file; never overwrite.")
    args=parser.parse_args(argv)
    try:
        result=analyze(read_request(args.request))
        output=report(result) if args.format=="markdown" else json.dumps(result,indent=2,allow_nan=False)+"\n"
        if args.output:
            # Exclusive creation, no accidental destruction of existing files.
            with Path(args.output).open("x",encoding="utf-8",newline="\n") as file:
                file.write(output)
        else:
            sys.stdout.write(output)
        return 0
    except (OSError,ValueError,RuntimeError,TypeError,RecursionError):
        print("Invalid or unavailable portfolio report; check input and output paths.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
