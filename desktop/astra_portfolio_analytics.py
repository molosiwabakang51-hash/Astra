"""Exact long-only portfolio attribution and hypothetical stress analysis.

The supplied opening/closing values must describe the same holdings, with
no trades, external cash flows or reinvested distributions during the period.
Income is cash received separately, not included in closing market value.
One reporting currency, no currency conversion. No market data or advice.
"""
from fractions import Fraction
from decimal import Decimal, localcontext
import re
from datetime import date

from astra_quantitative_verify import number

MAX_HOLDINGS = 50
MAX_SCENARIOS = 10
SYMBOL = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,39}\Z")
CURRENCY = re.compile(r"[A-Z]{3}\Z")


def quantity(value, *, nonnegative=False):
    result = number(value)
    if nonnegative and result < 0:
        raise ValueError("Negative value outside long-only portfolio domain.")
    return result


def metric(value, units):
    value = Fraction(value)
    with localcontext() as ctx:
        ctx.prec = 32
        decimal = str(Decimal(value.numerator) / Decimal(value.denominator))
    return {"numerator": str(value.numerator), "denominator": str(value.denominator),
            "decimal_approximation": decimal, "units": units}


def period(value):
    if not isinstance(value,str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}",value):
        raise ValueError("ISO calendar date required.")
    return date.fromisoformat(value)


def analyze(request):
    required = {"currency","start","end","basis","holdings"}
    if type(request) is not dict or not required <= request.keys() or set(request)-required-{"benchmark_total_return","scenarios"}:
        raise ValueError("Unexpected portfolio fields.")
    currency = request["currency"]
    if not isinstance(currency,str) or not CURRENCY.fullmatch(currency):
        raise ValueError("Explicit three-letter reporting currency required.")
    if request["basis"] != "unchanged_holdings_no_flows_income_excluded":
        raise ValueError("Confirm unchanged holdings, no flows and closing values excluding distributed income.")
    start,end = period(request["start"]),period(request["end"])
    if end <= start:
        raise ValueError("End date must follow start date.")
    holdings = request["holdings"]
    if type(holdings) is not list or not 1 <= len(holdings) <= MAX_HOLDINGS:
        raise ValueError("Expected one to fifty positions.")
    rows, seen = [], set()
    for holding in holdings:
        if type(holding) is not dict or set(holding)!={"symbol","opening_value","closing_value","cash_income"}:
            raise ValueError("Each position requires symbol, opening value, closing value and distributed cash income.")
        symbol = holding["symbol"]
        if not isinstance(symbol,str) or not SYMBOL.fullmatch(symbol) or symbol.casefold() in seen:
            raise ValueError("Invalid or duplicate holding identifier.")
        seen.add(symbol.casefold())
        opening = quantity(holding["opening_value"],nonnegative=True)
        closing = quantity(holding["closing_value"],nonnegative=True)
        income = quantity(holding["cash_income"],nonnegative=True)
        if opening <= 0:
            raise ValueError("Every position must have positive opening value.")
        rows.append((symbol,opening,closing,income))
    opening = sum(row[1] for row in rows)
    closing = sum(row[2] for row in rows)
    income = sum(row[3] for row in rows)
    capital = closing-opening
    gain = capital+income
    result = {
        "currency":currency,"start":request["start"],"end":request["end"],
        "days":(end-start).days,"basis":request["basis"],
        "opening_value":metric(opening,"currency_amount"),
        "closing_value":metric(closing,"currency_amount"),
        "cash_income":metric(income,"currency_amount"),
        "capital_gain":metric(capital,"currency_amount"),
        "total_gain":metric(gain,"currency_amount"),
        "capital_return":metric(capital/opening,"decimal_ratio"),
        "income_return":metric(income/opening,"decimal_ratio"),
        "total_return":metric(gain/opening,"decimal_ratio"),
        "positions":[],
        "scope":"Arithmetic on supplied unchanged holdings; no time-weighted return, money-weighted return, reinvestment, tax, fees or market-data validation.",
    }
    for symbol,o,c,i in rows:
        result["positions"].append({
            "symbol":symbol,"opening_value":metric(o,"currency_amount"),
            "closing_value":metric(c,"currency_amount"),
            "cash_income":metric(i,"currency_amount"),
            "total_return":metric((c-o+i)/o,"decimal_ratio"),
            "return_contribution":metric((c-o+i)/opening,"decimal_ratio"),
            "opening_weight":metric(o/opening,"decimal_ratio"),
            "closing_weight":metric(c/closing,"decimal_ratio") if closing else None,
        })
    if closing:
        weights = sorted((c/closing for _,_,c,_ in rows),reverse=True)
        hhi = sum(w*w for w in weights)
        result["concentration"] = {
            "largest_weight":metric(weights[0],"decimal_ratio"),
            "top_three_weight":metric(sum(weights[:3]),"decimal_ratio"),
            "hhi":metric(hhi*10000,"hhi_points"),
            "effective_positions":metric(1/hhi,"count"),
            "scope":"Closing market value weights across supplied positions, not sector or correlation risk.",
        }
    else:
        result["concentration"] = None
    if "benchmark_total_return" in request:
        benchmark = quantity(request["benchmark_total_return"])
        if benchmark < -1:
            raise ValueError("Benchmark total return cannot be below -100%.")
        result["benchmark"] = {
            "total_return":metric(benchmark,"decimal_ratio"),
            "active_return":metric(gain/opening-benchmark,"decimal_ratio"),
            "scope":"User-supplied same-period, same-currency benchmark total return; difference in percentage points.",
        }
    scenarios = request.get("scenarios",[])
    if type(scenarios) is not list or len(scenarios)>MAX_SCENARIOS:
        raise ValueError("At most ten scenarios allowed.")
    scenario_results, names = [], set()
    closing_by_symbol = {symbol:c for symbol,_,c,_ in rows}
    for scenario in scenarios:
        if type(scenario) is not dict or set(scenario)!={"name","shocks"}:
            raise ValueError("Each scenario requires a name and explicit shocks.")
        name,shocks = scenario["name"],scenario["shocks"]
        if not isinstance(name,str) or not SYMBOL.fullmatch(name) or name.casefold() in names:
            raise ValueError("Invalid or duplicate scenario name.")
        names.add(name.casefold())
        if type(shocks) is not dict or not shocks or not shocks.keys()<=closing_by_symbol.keys():
            raise ValueError("Scenario shocks must refer to known positions.")
        delta = Fraction(0)
        contributions = []
        for symbol,raw in shocks.items():
            shock = quantity(raw)
            if not -1<=shock<=10:
                raise ValueError("Shock outside supported range of -100% to +1000%.")
            change = closing_by_symbol[symbol]*shock
            delta += change
            contributions.append({"symbol":symbol,"shock":metric(shock,"decimal_ratio"),
                                  "value_change":metric(change,"currency_amount")})
        scenario_results.append({
            "name":name,"value_change":metric(delta,"currency_amount"),
            "stressed_value":metric(closing+delta,"currency_amount"),
            "portfolio_change":metric(delta/closing,"decimal_ratio") if closing else None,
            "positions":contributions,
            "scope":"Instantaneous hypothetical price shocks; omitted positions unchanged; no probabilities or income impact.",
        })
    result["scenarios"] = scenario_results
    return result
