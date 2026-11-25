"""Cash-flow-adjusted portfolio performance for explicit valuation boundaries.

External flows are signed from the portfolio perspective: positive deposits,
negative withdrawals. Valuations include all retained income and are supplied
immediately before each dated flow. Distributions leaving the portfolio must
be withdrawals. No inference of missing valuations, pricing or currency FX.
"""
from datetime import date
from decimal import Decimal, localcontext
from fractions import Fraction
import re

from astra_quantitative_verify import number

MAX_EVENTS = 60
MAX_DAYS = 36525
MIN_IRR = Decimal("-0.9999")
MAX_IRR = Decimal("10")
BASIS = "pre_flow_valuations_total_portfolio_value"


def metric(value, units):
    value = Fraction(value)
    with localcontext() as ctx:
        ctx.prec = 40
        approximation = str(Decimal(value.numerator)/Decimal(value.denominator))
    return {"numerator":str(value.numerator),"denominator":str(value.denominator),
            "decimal_approximation":approximation,"units":units}


def dec(value):
    value = Fraction(value)
    return Decimal(value.numerator)/Decimal(value.denominator)


def parse_date(value):
    if not isinstance(value,str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}",value):
        raise ValueError("Expected ISO calendar date.")
    return date.fromisoformat(value)


def sign_changes(amounts):
    signs = [1 if amount>0 else -1 for amount in amounts if amount]
    return sum(a!=b for a,b in zip(signs,signs[1:]))


def money_weighted(cash, days):
    """Annualized ACT/365 IRR for a conventional single-sign-change series.

    A single sign change in chronological investor cash flows ensures at
    most one positive discount-factor root. Refuse nonconventional series
    rather than selecting an arbitrary root. Search a declared finite range.
    """
    changes = sign_changes([amount for _,amount in cash])
    if changes != 1:
        return {"status":"unavailable_nonconventional_cash_flows",
                "sign_changes":changes,"annualized_return":None}
    with localcontext() as ctx:
        ctx.prec = 64
        values = [(Decimal(day)/365,dec(amount)) for day,amount in cash if amount]
        scale = sum(abs(amount) for _,amount in values)
        # Scaling helps compare residuals across different portfolio sizes.
        def npv(rate):
            return sum((amount/(1+rate)**time for time,amount in values),Decimal(0))
        low,high = MIN_IRR,MAX_IRR
        fl,fh = npv(low),npv(high)
        if fl==0:
            root=low
        elif fh==0:
            root=high
        elif (fl>0)==(fh>0):
            return {"status":"unavailable_outside_yield_bracket",
                    "sign_changes":changes,"annualized_return":None,
                    "search_range_decimal":["-0.9999","10"]}
        else:
            for _ in range(120):
                mid=(low+high)/2
                fm=npv(mid)
                if fm==0:
                    low=high=mid
                    break
                if (fm>0)==(fl>0):
                    low,fl=mid,fm
                else:
                    high=mid
            root=(low+high)/2
        residual=npv(root)
        # Report the actual numerical residual rather than claiming exact IRR.
        if abs(residual)/scale>Decimal("1e-12"):
            return {"status":"unavailable_numerical_residual",
                    "sign_changes":changes,"annualized_return":None}
        return {"status":"numerical_estimate","sign_changes":changes,
                "annualized_return":str(root),
                "period_return":str((1+root)**(Decimal(days)/365)-1),
                "npv_residual":str(residual),
                "relative_npv_residual":str(abs(residual)/scale),
                "search_range_decimal":["-0.9999","10"],
                "day_basis":"ACT/365 fixed",
                "scope":"Numerical investor cash-flow IRR; not guaranteed realized return."}


def analyze(request):
    required={"currency","start","end","opening_value","closing_value","basis","events"}
    if type(request) is not dict or set(request)!=required:
        raise ValueError("Expected explicit performance inputs and valuation events.")
    currency=request["currency"]
    if not isinstance(currency,str) or not re.fullmatch(r"[A-Z]{3}",currency):
        raise ValueError("Explicit three-letter currency required.")
    if request["basis"]!=BASIS:
        raise ValueError("Confirm immediately pre-flow total portfolio valuations.")
    start,end=parse_date(request["start"]),parse_date(request["end"])
    days=(end-start).days
    if not 1<=days<=MAX_DAYS:
        raise ValueError("Reporting period must be 1–36,525 days.")
    opening,closing=number(request["opening_value"]),number(request["closing_value"])
    if opening<=0 or closing<0:
        raise ValueError("Positive opening and nonnegative closing values required.")
    events=request["events"]
    if type(events) is not list or len(events)>MAX_EVENTS:
        raise ValueError("At most sixty explicit external-flow events supported.")
    previous_date=start
    previous_value=opening
    factor=Fraction(1)
    net_flows=Fraction(0)
    weighted_flows=Fraction(0)
    cash=[(0,-opening)]
    segments=[]
    for event in events:
        if type(event) is not dict or set(event)!={"date","value_before_flow","external_flow"}:
            raise ValueError("Each event needs date, pre-flow value and signed external flow.")
        when=parse_date(event["date"])
        if not previous_date<when<end:
            raise ValueError("Events must have unique increasing dates strictly within the reporting period.")
        before,flow=number(event["value_before_flow"]),number(event["external_flow"])
        after=before+flow
        if before<0 or after<=0 or flow==0:
            raise ValueError("Require nonnegative pre-flow, positive post-flow value and nonzero external flow.")
        segment_factor=before/previous_value
        factor*=segment_factor
        segments.append({"start":previous_date.isoformat(),"end":when.isoformat(),
                         "opening_after_flow":metric(previous_value,"currency_amount"),
                         "closing_before_flow":metric(before,"currency_amount"),
                         "external_flow_at_end":metric(flow,"currency_amount"),
                         "value_after_flow":metric(after,"currency_amount"),
                         "return":metric(segment_factor-1,"decimal_ratio")})
        day=(when-start).days
        cash.append((day,-flow))
        net_flows+=flow
        weighted_flows+=flow*Fraction(days-day,days)
        previous_value,previous_date=after,when
    last_factor=closing/previous_value
    factor*=last_factor
    segments.append({"start":previous_date.isoformat(),"end":end.isoformat(),
                     "opening_after_flow":metric(previous_value,"currency_amount"),
                     "closing_before_flow":metric(closing,"currency_amount"),
                     "external_flow_at_end":metric(0,"currency_amount"),
                     "value_after_flow":metric(closing,"currency_amount"),
                     "return":metric(last_factor-1,"decimal_ratio")})
    cash.append((days,closing))
    gain=closing-opening-net_flows
    denominator=opening+weighted_flows
    annualized=None
    # Do not present extrapolated subyear TWR as an annual performance figure.
    if days>=365:
        with localcontext() as ctx:
            ctx.prec=40
            annualized=str(dec(factor)**(Decimal(365)/days)-1) if factor else "-1"
    return {
        "currency":currency,"start":start.isoformat(),"end":end.isoformat(),"days":days,
        "basis":BASIS,
        "opening_value":metric(opening,"currency_amount"),
        "closing_value":metric(closing,"currency_amount"),
        "net_external_flows":metric(net_flows,"currency_amount"),
        "investment_gain":metric(gain,"currency_amount"),
        "time_weighted_return":metric(factor-1,"decimal_ratio"),
        "annualized_time_weighted_return":annualized,
        "annualization_basis":"ACT/365 fixed; annualized TWR omitted for periods shorter than 365 days",
        "modified_dietz":metric(gain/denominator,"decimal_ratio") if denominator>0 else None,
        "modified_dietz_denominator":metric(denominator,"currency_amount"),
        "money_weighted":money_weighted(cash,days),
        "segments":segments,
        "scope":"Portfolio-level performance using supplied complete pre-flow valuations; no market-data validation, inferred cash flows or security-level attribution.",
    }
