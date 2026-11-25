"""Generate a deterministic fixed-income valuation and sensitivity report."""
import argparse
from decimal import Decimal, localcontext
import json
from pathlib import Path
import sys

from astra_fixed_income import analyze
from astra_verified_finance import read_request


def display(value, currency, places=4):
    if value is None:
        return "Not applicable"
    with localcontext() as ctx:
        ctx.prec = 48
        amount = Decimal(value["decimal_approximation"])
        units = value["units"]
        if units == "decimal_ratio":
            return f"{amount*100:,.{places}f}%"
        if units == "currency_amount":
            return f"{currency} {amount:,.2f}"
        return f"{amount:,.{places}f}"


def report(result):
    currency = result["currency"]
    fmt = lambda value: display(value, currency)
    lines = [
        "# Fixed-income valuation and interest-rate risk",
        "",
        f"Reporting currency: {currency}",
        "",
        "## Bond terms and valuation",
        "",
        "| Measure | Result |",
        "|---|---:|",
    ]
    for key,label in (
        ("face_value","Face value"),("annual_coupon_rate","Annual coupon rate"),
        ("coupon_payment","Coupon per payment"),("years_to_maturity","Remaining years"),
        ("annual_yield","Nominal annual yield to maturity"),("price","Model price"),
        ("price_per_100_face","Price per 100 face"),("current_yield","Current yield"),
    ):
        lines.append(f"| {label} | {fmt(result[key])} |")
    lines.extend([
        f"| Coupon payments per year | {result['payments_per_year']} |",
        f"| Remaining coupon periods | {result['remaining_periods']} |",
    ])
    if "market_price" in result:
        lines.append(f"| Supplied market price | {fmt(result['market_price'])} |")
        lines.extend(["",f"YTM is a numerical bisection estimate. Yield resolution: {result['yield_resolution']}.",
                      f"Model price minus supplied price (unrounded decimal approximation): {result['pricing_residual']['decimal_approximation']} {currency}."])
    else:
        lines.extend(["","Price and sensitivity calculations use the supplied yield."])
    lines.extend([
        "", "## Interest-rate sensitivity", "",
        "| Measure | Result |", "|---|---:|",
    ])
    for key,label in (
        ("macaulay_duration","Macaulay duration (years)"),
        ("modified_duration","Modified duration (years)"),
        ("convexity","Convexity (years squared)"),
        ("linear_dv01","Linear DV01 (positive value loss for +1 bp)"),
        ("exact_up_one_bp_loss","Exact repriced loss for +1 bp"),
    ):
        lines.append(f"| {label} | {fmt(result[key])} |")
    lines.extend(["","## Parallel yield shocks",""])
    if result["scenarios"]:
        lines.extend([
            "| Shock (bp) | Stressed yield | Repriced value | Actual model change | Duration/convexity estimate | Error (pp) |",
            "|---:|---:|---:|---:|---:|---:|",
        ])
        for scenario in result["scenarios"]:
            lines.append("| "+" | ".join([
                f"{scenario['shock_bps']:+d}",fmt(scenario["stressed_annual_yield"]),
                fmt(scenario["stressed_price"]),fmt(scenario["exact_price_change"]),
                fmt(scenario["duration_convexity_estimate"]),
                fmt(scenario["approximation_error"]).replace("%",""),
            ])+" |")
    else:
        lines.append("No shocks supplied.")
    lines.extend([
        "", "## Method and limitations", "",
        "- Price is the sum of coupon and redemption cash flows discounted at yield divided by payment frequency.",
        "- Macaulay duration is the cash-flow present-value-weighted time to payment.",
        "- Modified duration and convexity use derivatives with respect to nominal annual yield.",
        "- Duration/convexity approximation: -modified duration × yield change + convexity × yield change² / 2.",
        "- Stress error is the approximation minus the exact modeled price change, in percentage points.",
        "- Coupon-date valuation immediately after a coupon payment, with regular fixed cash flows and face-value redemption.",
        "- No accrued interest, settlement dates, day-count adjustments, irregular coupons, embedded options, default, inflation linking, FX, fees or taxes.",
        "- Current yield is annual coupon divided by modeled price; it is not total return.",
        "- YTM assumes scheduled payments and does not guarantee realized return.",
        "- Prices and sensitivities depend entirely on supplied assumptions. No market-data validation or investment recommendation.",
        "- Display values are rounded. JSON retains exact rational results at the supplied or numerically approximated yield.",
        "",
    ])
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request",help="Bond JSON input")
    parser.add_argument("--format",choices=("markdown","json"),default="markdown")
    parser.add_argument("--output",help="Create a new file without overwriting")
    args = parser.parse_args(argv)
    try:
        result = analyze(read_request(args.request))
        output = report(result) if args.format=="markdown" else json.dumps(result,indent=2,allow_nan=False)+"\n"
        if args.output:
            with Path(args.output).open("x",encoding="utf-8",newline="\n") as target:
                target.write(output)
        else:
            sys.stdout.write(output)
        return 0
    except (OSError,ValueError,RuntimeError,TypeError,RecursionError,ArithmeticError):
        print("Invalid or unavailable fixed-income report.",file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
