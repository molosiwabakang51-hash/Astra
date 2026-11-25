"""Fixed-rate bullet-bond analytics on a coupon date.

All cash flows are regular, in one currency, with redemption at maturity.
Nominal annual yields compound at the coupon frequency. No accrued interest,
day-count conventions, settlement adjustments, options, indexation or default.
Exact rational arithmetic is used at supplied yields; YTM is a numerical root.
"""
from fractions import Fraction
from decimal import Decimal, localcontext
import re

from astra_quantitative_verify import number

FREQUENCIES = (1, 2, 4, 12)
MAX_PERIODS = 120
MIN_YIELD = Fraction(-9, 10)
MAX_YIELD = Fraction(2)
MAX_SHOCKS = 10
ONE_BP = Fraction(1, 10000)


def metric(value, units):
    value = Fraction(value)
    with localcontext() as ctx:
        ctx.prec = 32
        approximation = str(Decimal(value.numerator) / Decimal(value.denominator))
    return {"numerator": str(value.numerator), "denominator": str(value.denominator),
            "decimal_approximation": approximation, "units": units}


def validate_yield(value):
    if not MIN_YIELD <= value <= MAX_YIELD:
        raise ValueError("Yield outside supported annual range of -90% to 200%.")
    return value


def cash_flows(face, coupon, frequency, periods):
    payment = face * coupon / frequency
    return [payment] * (periods - 1) + [payment + face]


def price(cash, frequency, annual_yield):
    factor = 1 + validate_yield(annual_yield) / frequency
    return sum((flow / factor**k for k, flow in enumerate(cash, 1)), Fraction(0))


def solve_yield(cash, frequency, target):
    """Monotone bisection on a bounded nominal annual yield interval.

    Exact prices at the endpoints establish the bracket. The root is then
    approximated to 14 decimal places; the report includes pricing residual.
    """
    low_price = price(cash, frequency, MIN_YIELD)
    high_price = price(cash, frequency, MAX_YIELD)
    if not high_price <= target <= low_price:
        raise ValueError("Market price outside supported yield bracket.")
    if target == low_price:
        return MIN_YIELD
    if target == high_price:
        return MAX_YIELD
    with localcontext() as ctx:
        ctx.prec = 64
        to_decimal = lambda x: Decimal(x.numerator) / Decimal(x.denominator)
        flows = [to_decimal(value) for value in cash]
        target_decimal = to_decimal(target)
        low, high = to_decimal(MIN_YIELD), to_decimal(MAX_YIELD)
        for _ in range(64):
            mid = (low + high) / 2
            factor = 1 + mid / frequency
            discounted = sum((flow / factor**k for k, flow in enumerate(flows, 1)), Decimal(0))
            if discounted > target_decimal:
                low = mid
            else:
                high = mid
        estimate = ((low + high) / 2).quantize(Decimal("0.00000000000001"))
    return validate_yield(Fraction(estimate))


def analyze(request):
    required = {"currency", "face_value", "annual_coupon_rate", "payments_per_year",
                "remaining_periods", "basis"}
    if type(request) is not dict or not required <= request.keys() or set(request)-required-{
            "annual_yield", "market_price", "shocks_bps"}:
        raise ValueError("Unexpected bond input fields.")
    if ("annual_yield" in request) == ("market_price" in request):
        raise ValueError("Supply exactly one annual yield or market price.")
    currency = request["currency"]
    if not isinstance(currency,str) or not re.fullmatch(r"[A-Z]{3}",currency):
        raise ValueError("Explicit three-letter currency required.")
    if request["basis"] != "regular_fixed_rate_bullet_on_coupon_date":
        raise ValueError("Confirm regular fixed-rate bullet bond and coupon-date settlement.")
    face, coupon = number(request["face_value"]), number(request["annual_coupon_rate"])
    if face <= 0 or not 0 <= coupon <= 2:
        raise ValueError("Positive face value and annual coupon between zero and 200% required.")
    frequency, periods = request["payments_per_year"], request["remaining_periods"]
    if type(frequency) is not int or frequency not in FREQUENCIES:
        raise ValueError("Coupon frequency must be 1, 2, 4 or 12 per year.")
    if type(periods) is not int or not 1 <= periods <= MAX_PERIODS:
        raise ValueError("Remaining regular periods must be between 1 and 120.")
    cash = cash_flows(face,coupon,frequency,periods)
    target = None
    if "market_price" in request:
        target = number(request["market_price"])
        if target <= 0:
            raise ValueError("Market price must be positive.")
        annual_yield = solve_yield(cash,frequency,target)
    else:
        annual_yield = validate_yield(number(request["annual_yield"]))
    factor = 1 + annual_yield/frequency
    discounted = [flow/factor**k for k,flow in enumerate(cash,1)]
    valuation = sum(discounted)
    mac = sum(Fraction(k,frequency)*pv for k,pv in enumerate(discounted,1))/valuation
    modified = mac/factor
    convexity = sum(Fraction(k*(k+1),frequency**2)*pv
                    for k,pv in enumerate(discounted,1))/(valuation*factor**2)
    result = {
        "currency": currency, "basis": request["basis"],
        "face_value": metric(face,"currency_amount"),
        "annual_coupon_rate": metric(coupon,"decimal_ratio"),
        "coupon_payment": metric(face*coupon/frequency,"currency_amount"),
        "payments_per_year": frequency, "remaining_periods": periods,
        "years_to_maturity": metric(Fraction(periods,frequency),"years"),
        "annual_yield": metric(annual_yield,"decimal_ratio"),
        "yield_method": "supplied_exact" if target is None else "bounded_bisection_approximation",
        "price": metric(valuation,"currency_amount"),
        "price_per_100_face": metric(valuation*100/face,"price_per_100"),
        "current_yield": metric(face*coupon/valuation,"decimal_ratio"),
        "macaulay_duration": metric(mac,"years"),
        "modified_duration": metric(modified,"years"),
        "convexity": metric(convexity,"years_squared"),
        "linear_dv01": metric(valuation*modified*ONE_BP,"currency_amount"),
        "scope":"Regular coupon-date fixed-rate bullet cash flows; derivatives are with respect to nominal annual yield, not periodic yield.",
    }
    if target is not None:
        result["market_price"] = metric(target,"currency_amount")
        result["pricing_residual"] = metric(valuation-target,"currency_amount")
        result["yield_resolution"] = "0.00000000000001 annual decimal yield (numerical approximation)"
    if annual_yield+ONE_BP <= MAX_YIELD:
        result["exact_up_one_bp_loss"] = metric(valuation-price(cash,frequency,annual_yield+ONE_BP),"currency_amount")
    else:
        result["exact_up_one_bp_loss"] = None
    shocks = request.get("shocks_bps",[])
    if type(shocks) is not list or len(shocks)>MAX_SHOCKS:
        raise ValueError("At most ten integer basis-point shocks permitted.")
    scenarios,seen=[],set()
    for bp in shocks:
        if type(bp) is not int or not -1000<=bp<=1000 or bp in seen:
            raise ValueError("Unique integer shocks between -1000 and +1000 basis points required.")
        seen.add(bp)
        change = Fraction(bp,10000)
        shifted = validate_yield(annual_yield+change)
        stressed = price(cash,frequency,shifted)
        actual = (stressed-valuation)/valuation
        estimated = -modified*change + convexity*change**2/2
        scenarios.append({
            "shock_bps":bp,
            "stressed_annual_yield":metric(shifted,"decimal_ratio"),
            "stressed_price":metric(stressed,"currency_amount"),
            "exact_price_change":metric(actual,"decimal_ratio"),
            "duration_convexity_estimate":metric(estimated,"decimal_ratio"),
            "approximation_error":metric(estimated-actual,"decimal_ratio"),
        })
    result["scenarios"] = scenarios
    return result
