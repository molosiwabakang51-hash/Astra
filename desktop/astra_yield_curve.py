"""Offline yield curves with explicit conventions and bounded interpolation.

Curve times are integer months from valuation. Inputs are discount factors,
continuously compounded zero rates, or regular coupon-grid par yields.
Discount factors are interpolated log-linearly; no extrapolation is permitted.
All rates are decimal ratios. No live quotes, calendar or credit assumptions.
"""
from bisect import bisect_left
from decimal import Decimal, localcontext
from fractions import Fraction
import re

from astra_quantitative_verify import number

MAX_NODES = 120
MAX_MONTHS = 1200
MAX_CASHFLOWS = 120
BP = Decimal("0.0001")
PRECISION = 50


def decimal(value):
    f = number(value)
    return Decimal(f.numerator) / Decimal(f.denominator)


def months(value, *, zero=False):
    if type(value) is not int or not (0 if zero else 1) <= value <= MAX_MONTHS:
        raise ValueError("Maturity must be a bounded integer month count.")
    return value


def bounded_rate(value):
    rate = decimal(value)
    if not Decimal("-0.90") <= rate <= Decimal("2"):
        raise ValueError("Rate outside supported range.")
    return rate


def text(value):
    return str(+value)


class Curve:
    def __init__(self, nodes):
        self.times = tuple(t for t, _ in nodes)
        self.logs = tuple(d for _, d in nodes)
        if not self.times or any(a >= b for a,b in zip(self.times,self.times[1:])):
            raise ValueError("Curve maturities must be strictly increasing.")
        if any(not d.is_finite() for d in self.logs):
            raise ValueError("Invalid curve factors.")

    def log_discount(self, maturity):
        months(maturity, zero=True)
        if maturity == 0:
            return Decimal(0)
        if maturity > self.times[-1]:
            raise ValueError("Maturity exceeds curve coverage; extrapolation refused.")
        i = bisect_left(self.times, maturity)
        if self.times[i] == maturity:
            return self.logs[i]
        left = self.times[i-1] if i else 0
        log_left = self.logs[i-1] if i else Decimal(0)
        return log_left + (self.logs[i]-log_left)*Decimal(maturity-left)/Decimal(self.times[i]-left)

    def discount(self, maturity):
        return self.log_discount(maturity).exp()

    def zero(self, maturity):
        months(maturity)
        return -self.log_discount(maturity)*12/Decimal(maturity)

    def forward(self, start, end):
        months(start, zero=True)
        months(end)
        if end <= start:
            raise ValueError("Forward interval must be positive.")
        return (self.log_discount(start)-self.log_discount(end))*12/Decimal(end-start)

    def bumped(self, shock, node=None):
        if node is not None and node not in self.times:
            raise ValueError("Unknown key-rate node.")
        return Curve([(t, log-shock*Decimal(t)/12 if node is None or t==node else log)
                      for t,log in zip(self.times,self.logs)])


def build(spec):
    if type(spec) is not dict or "kind" not in spec or "nodes" not in spec:
        raise ValueError("Explicit curve kind and nodes required.")
    kind = spec["kind"]
    if kind not in ("discount_factors","continuous_spot","regular_par"):
        raise ValueError("Unsupported curve kind.")
    if set(spec) != ({"kind","nodes","payments_per_year"} if kind=="regular_par" else {"kind","nodes"}):
        raise ValueError("Unexpected curve fields.")
    nodes = spec["nodes"]
    if type(nodes) is not list or not 1<=len(nodes)<=MAX_NODES:
        raise ValueError("Expected one to 120 curve nodes.")
    out = []
    prior = 0
    exact_factors = []
    if kind=="regular_par":
        freq = spec["payments_per_year"]
        if type(freq) is not int or freq not in (1,2,4,12):
            raise ValueError("Regular par frequency must divide twelve months.")
        step = 12//freq
    for row in nodes:
        if type(row) is not dict or set(row)!={"months","value"}:
            raise ValueError("Each node requires maturity months and a decimal value.")
        t = months(row["months"])
        if t<=prior:
            raise ValueError("Curve nodes must be unique and increasing.")
        prior = t
        if kind=="continuous_spot":
            log = -bounded_rate(row["value"])*Decimal(t)/12
        elif kind=="discount_factors":
            df = decimal(row["value"])
            if not Decimal("1e-12")<=df<=Decimal("1e12"):
                raise ValueError("Discount factors must be positive and within numerical bounds.")
            log = df.ln()
        else:
            if t != (len(exact_factors)+1)*step:
                raise ValueError("Par bootstrap requires every coupon-grid maturity, beginning at the first.")
            y = number(row["value"])
            if not Fraction(-9,10)<=y<=2:
                raise ValueError("Par rate outside supported range.")
            coupon = y/freq
            # Negative coupons are not supported for conventional par bonds.
            if coupon<0:
                raise ValueError("Par bootstrap requires nonnegative coupons.")
            df = (1-coupon*sum(exact_factors,Fraction(0)))/(1+coupon)
            if not Fraction(1,10**12)<=df<=10**12:
                raise ValueError("Par inputs imply an invalid or out-of-range discount factor.")
            exact_factors.append(df)
            log = (Decimal(df.numerator)/Decimal(df.denominator)).ln()
        out.append((t,log))
    return Curve(out)


def price(curve, cash):
    return sum((amount*curve.discount(t) for t,amount in cash),Decimal(0))


def analyze(request):
    if type(request) is not dict or not {"currency","curve"}<=request.keys() or set(request)-{"currency","curve","queries","forwards","cashflows","key_rate_months","parallel_shocks_bps"}:
        raise ValueError("Unexpected curve-analysis fields.")
    currency=request["currency"]
    if not isinstance(currency,str) or not re.fullmatch(r"[A-Z]{3}",currency):
        raise ValueError("Explicit three-letter reporting currency required.")
    with localcontext() as ctx:
        ctx.prec=PRECISION
        curve=build(request["curve"])
        queries=request.get("queries",list(curve.times))
        if type(queries) is not list or len(queries)>MAX_NODES or any(type(t) is not int for t in queries) or len(set(queries))!=len(queries):
            raise ValueError("Expected bounded unique maturity queries.")
        result={
            "currency":currency,"curve_kind":request["curve"]["kind"],
            "interpolation":"log_linear_discount_factors",
            "rate_convention":"continuously compounded annual decimal ratios",
            "nodes":[{"months":t,"discount_factor":text(curve.discount(t)),
                      "continuous_zero":text(curve.zero(t))} for t in curve.times],
            "queries":[{"months":months(t),"discount_factor":text(curve.discount(t)),
                        "continuous_zero":text(curve.zero(t))} for t in queries],
            "forwards":[],
            "scope":"Numerical curve model on supplied data; no market validation, extrapolation, day-count or calendar adjustment.",
        }
        forwards=request.get("forwards",[])
        if type(forwards) is not list or len(forwards)>MAX_NODES:
            raise ValueError("Invalid forward intervals.")
        for pair in forwards:
            if type(pair) is not list or len(pair)!=2:
                raise ValueError("Each forward interval requires two month counts.")
            a,b=pair
            result["forwards"].append({"start_month":a,"end_month":b,
                                       "continuous_forward":text(curve.forward(a,b))})
        if "cashflows" not in request:
            if "key_rate_months" in request or "parallel_shocks_bps" in request:
                raise ValueError("Cash flows required for sensitivity analysis.")
            return result
        raw=request["cashflows"]
        if type(raw) is not list or not 1<=len(raw)<=MAX_CASHFLOWS:
            raise ValueError("Expected one to 120 dated cash flows.")
        cash=[]
        prior=0
        for row in raw:
            if type(row) is not dict or set(row)!={"months","amount"}:
                raise ValueError("Cash flows require months and amount.")
            t,amount=months(row["months"]),decimal(row["amount"])
            if t<=prior or amount<0:
                raise ValueError("Cash flows must be increasing, unique and nonnegative.")
            curve.log_discount(t)
            prior=t
            cash.append((t,amount))
        base=price(curve,cash)
        if base<=0:
            raise ValueError("Positive modeled value required.")
        parallel_up=price(curve.bumped(BP),cash)
        parallel_down=price(curve.bumped(-BP),cash)
        result["valuation"]={
            "price":text(base),
            "cashflows":[{"months":t,"amount":text(amount),
                          "discount_factor":text(curve.discount(t)),
                          "present_value":text(amount*curve.discount(t))} for t,amount in cash],
            "parallel_dv01_central":text((parallel_down-parallel_up)/2),
            "parallel_duration_central":text((parallel_down-parallel_up)/(2*BP*base)),
            "parallel_convexity_central":text((parallel_up+parallel_down-2*base)/(BP*BP*base)),
            "key_rates":[],"parallel_scenarios":[],
        }
        valuation=result["valuation"]
        keys=request.get("key_rate_months",list(curve.times))
        if type(keys) is not list or len(keys)>MAX_NODES or any(type(t) is not int or t not in curve.times for t in keys) or len(set(keys))!=len(keys):
            raise ValueError("Key-rate maturities must be unique supplied curve nodes.")
        total=Decimal(0)
        for t in keys:
            up=price(curve.bumped(BP,t),cash)
            down=price(curve.bumped(-BP,t),cash)
            dv01=(down-up)/2
            total+=dv01
            valuation["key_rates"].append({"months":t,"dv01_central":text(dv01),
                                           "duration_central":text(dv01/(BP*base))})
        valuation["selected_key_rate_dv01_sum"]=text(total)
        valuation["all_curve_nodes_selected"]=set(keys)==set(curve.times)
        shocks=request.get("parallel_shocks_bps",[])
        if type(shocks) is not list or len(shocks)>10 or any(type(x) is not int or not -1000<=x<=1000 for x in shocks) or len(set(shocks))!=len(shocks):
            raise ValueError("At most ten unique integer parallel shocks within ±1000 bp.")
        for bp in shocks:
            stressed=price(curve.bumped(BP*bp),cash)
            valuation["parallel_scenarios"].append({"shock_bps":bp,"price":text(stressed),
                                                    "price_change":text(stressed-base),
                                                    "relative_change":text(stressed/base-1)})
        valuation["scope"]="Nonnegative deterministic cash flows; central finite differences of continuous zero-rate shocks. Key-rate sums only approximately reconcile to parallel DV01."
        return result
