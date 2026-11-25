"""Auditable local strategy backtest report."""
import argparse
from decimal import Decimal, localcontext
import json
from pathlib import Path
import sys

from astra_strategy_backtest import analyze
from astra_verified_finance import unique_object, invalid_constant

MAX_BYTES=524288


def read_request(path):
    with Path(path).open("rb") as f:
        raw=f.read(MAX_BYTES+1)
    if len(raw)>MAX_BYTES:
        raise ValueError("Backtest input exceeds 512 KiB.")
    return json.loads(raw.decode("utf-8"),object_pairs_hook=unique_object,parse_constant=invalid_constant)


def display(value,percent=False):
    if value is None:
        return "Not applicable"
    with localcontext() as ctx:
        ctx.prec=60
        x=Decimal(value)*(100 if percent else 1)
        if abs(x)>=Decimal("1e12") or (x and abs(x)<Decimal("1e-8")):
            return f"{x:.6E}"+("%" if percent else "")
        return f"{x:,.4f}"+("%" if percent else "")


def report(result):
    money=lambda x:result["currency"]+" "+display(x)
    pct=lambda x:display(x,True)
    lines=["# Historical strategy backtest","",
           f"Observations: {result['observations']}; initial wealth: {money(result['initial_wealth'])}.",
           f"Transaction fee per unit of gross traded value: {result['transaction_cost_bps']} basis points.",
           "",
           "| Strategy | Terminal wealth | Net return | Max drawdown loss | Total fees | Cumulative one-way turnover | Trade events |",
           "|---|---:|---:|---:|---:|---:|---:|"]
    for s in result["strategies"]:
        lines.append("| "+" | ".join([s["name"],money(s["terminal_wealth"]),pct(s["cumulative_net_return"]),
            pct(s["maximum_drawdown_loss"]),money(s["total_transaction_cost"]),
            pct(s["cumulative_one_way_turnover"]),str(s["rebalance_trade_count"])])+" |")
    if "benchmark" in result:
        lines.extend(["","## Benchmark comparison","",
                      "Benchmark cumulative return: "+pct(result["benchmark"]["cumulative_return"])+".","",
                      "| Strategy | Net active return (percentage points) |","|---|---:|"])
        for s in result["strategies"]:
            lines.append(f"| {s['name']} | {pct(s['active_cumulative_return']).replace('%','')} |")
    for s in result["strategies"]:
        lines.extend(["",f"## {s['name']} — period reconciliation","",
                      f"Strategy: {s['kind'].replace('_',' ')}.","",
                      "| Period | Opening wealth | Post-trade wealth | Fees | Gross trades | Closing wealth | Net return |",
                      "|---:|---:|---:|---:|---:|---:|---:|"])
        for row in s["series"]:
            lines.append("| "+" | ".join([str(row["period"]),money(row["opening_wealth"]),
                         money(row["post_trade_wealth"]),money(row["transaction_cost"]),
                         money(row["gross_traded_value"]),money(row["closing_wealth"]),
                         pct(row["net_return"])])+" |")
    lines.extend(["","## Method and limitations","",
                  "- Chronological aligned periodic simple total returns; the initial allocation is pre-existing and free of modeled trading fees.",
                  "- Buy-and-hold weights drift; fixed weights rebalance at predeclared boundaries; lagged momentum ranks only completed trailing observations.",
                  "- Fees are charged on total absolute traded value, including buys and sells; post-cost weights are solved self-financing.",
                  "- One-way turnover is half gross traded value divided by pre-trade wealth, summed across trade events.",
                  "- Drawdown uses end-period wealth and initial wealth; intraperiod drawdowns are unavailable.",
                  "- Benchmark returns are supplied, with no additional modeled benchmark fees.",
                  "- No look-ahead in the decision rules, but inputs, universe selection and strategy parameters can still embody selection, survivorship or data-snooping bias.",
                  "- No actual trading, market execution, tax, slippage beyond the stated fee, market-data verification or investment recommendation.",
                  "- Decimal working precision: 50 digits. JSON includes pre-period weights and the period ledger.",""])
    return "\n".join(lines)


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("request")
    p.add_argument("--format",choices=("markdown","json"),default="markdown")
    p.add_argument("--output")
    args=p.parse_args(argv)
    try:
        result=analyze(read_request(args.request))
        output=report(result) if args.format=="markdown" else json.dumps(result,indent=2,allow_nan=False)+"\n"
        if args.output:
            with Path(args.output).open("x",encoding="utf-8",newline="\n") as f:
                f.write(output)
        else:
            sys.stdout.write(output)
        return 0
    except (OSError,ValueError,TypeError,RuntimeError,RecursionError,ArithmeticError):
        print("Invalid or unavailable historical backtest.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
