"""Historical risk for aligned, equally spaced simple total-return observations.

A constant-weight, frictionless rebalanced portfolio is simulated. Exact
rational arithmetic is used for moments and empirical tail losses; geometric
wealth, square roots and annualization use bounded Decimal precision.
Historical observations are not forecasts or independently verified inputs.
"""
from decimal import Decimal, localcontext
from fractions import Fraction
import re

from astra_quantitative_verify import number

MAX_ASSETS = 6
MAX_ROWS = 2520
FREQUENCIES = (12, 52, 252)
BASIS = "aligned_periodic_total_returns_constant_weights"
SYMBOL = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,39}\Z")


def metric(x):
    x = Fraction(x)
    with localcontext() as ctx:
        ctx.prec = 40
        value = Decimal(x.numerator) / Decimal(x.denominator)
    return {"numerator": str(x.numerator), "denominator": str(x.denominator),
            "decimal_approximation": str(value)}


def dec(x):
    x = Fraction(x)
    return Decimal(x.numerator)/Decimal(x.denominator)


def root(x):
    with localcontext() as ctx:
        ctx.prec = 40
        return dec(x).sqrt()


def mean(xs):
    return sum(xs, Fraction(0))/len(xs)


def covariance(xs, ys):
    n = len(xs)
    return (sum((x*y for x,y in zip(xs,ys)),Fraction(0))
            - sum(xs)*sum(ys)/n)/(n-1)


def rate(value):
    x = number(value)
    if not -1 <= x <= 10:
        raise ValueError("Simple return must be between -100% and +1000%.")
    return x


def tail_losses(returns, confidence):
    """Empirical loss quantile and integral of upper-tail quantiles.

    Loss = negative return, without flooring at zero. VaR uses the
    inverse empirical CDF; ES averages exactly (1-confidence) probability
    mass, including a fractional boundary observation when necessary.
    """
    losses = sorted((-x for x in returns))
    n = len(losses)
    mass = (1-confidence)*n
    rank = -(-(confidence*n).numerator//(confidence*n).denominator)
    whole = mass.numerator//mass.denominator
    remainder = mass-whole
    descending = list(reversed(losses))
    total = sum(descending[:whole],Fraction(0))
    if remainder:
        total += remainder*descending[whole]
    return {"confidence":metric(confidence),"var_loss":metric(losses[rank-1]),
            "expected_shortfall_loss":metric(total/mass),
            "tail_observation_equivalents":metric(mass),
            "thin_tail_sample":mass<5,
            "scope":"One observation period; empirical historical loss, not a forecast. Negative losses represent gains."}


def wealth_stats(returns, periods):
    with localcontext() as ctx:
        ctx.prec = 50
        wealth=peak=Decimal(1)
        worst=Decimal(0)
        peak_index=drawdown_peak=drawdown_trough=0
        for i,r in enumerate(returns,1):
            wealth *= 1+dec(r)
            if wealth>peak:
                peak=wealth
                peak_index=i
            dd=1-wealth/peak
            if dd>worst:
                worst=dd
                drawdown_peak,drawdown_trough=peak_index,i
        annual=None
        if len(returns)>=periods:
            annual = str(wealth**(Decimal(periods)/len(returns))-1) if wealth else "-1"
        return {"cumulative_return":str(wealth-1),
                "annualized_geometric_return":annual,
                "maximum_drawdown_loss":str(worst),
                "drawdown_peak_observation":drawdown_peak,
                "drawdown_trough_observation":drawdown_trough}


def summarize(xs, periods, confidence, rf):
    m=mean(xs)
    var=covariance(xs,xs)
    annual_var=var*periods
    vol=root(annual_var)
    sharpe=None
    if vol:
        with localcontext() as ctx:
            ctx.prec=40
            sharpe=str(dec((m-rf)*periods)/vol)
    return {"period_arithmetic_mean":metric(m),
            "annualized_arithmetic_mean":metric(m*periods),
            "period_sample_variance":metric(var),
            "annualized_sample_variance":metric(annual_var),
            "annualized_volatility":str(vol),"annualized_sharpe":sharpe,
            "wealth":wealth_stats(xs,periods),
            "historical_tail":tail_losses(xs,confidence)}


def analyze(request):
    required={"assets","returns","weights","periods_per_year","period_risk_free_rate","confidence","basis"}
    if type(request) is not dict or not required<=request.keys() or set(request)-required-{"benchmark_returns"}:
        raise ValueError("Unexpected or missing historical risk fields.")
    if request["basis"]!=BASIS:
        raise ValueError("Confirm aligned equally spaced total returns and constant rebalanced weights.")
    assets=request["assets"]
    if type(assets) is not list or not 1<=len(assets)<=MAX_ASSETS or any(
            not isinstance(a,str) or not SYMBOL.fullmatch(a) for a in assets):
        raise ValueError("Expected one to six valid asset identifiers.")
    n=len(assets)
    if len({a.casefold() for a in assets})!=n:
        raise ValueError("Duplicate asset identifiers.")
    periods=request["periods_per_year"]
    if type(periods) is not int or periods not in FREQUENCIES:
        raise ValueError("Choose monthly (12), weekly (52), or trading-day (252) observations.")
    rf=rate(request["period_risk_free_rate"])
    if rf<=-1:
        raise ValueError("Periodic risk-free return must exceed -100%.")
    confidence=number(request["confidence"])
    if not Fraction(1,2)<=confidence<=Fraction(99,100):
        raise ValueError("Confidence must be between 50% and 99%.")
    raw=request["weights"]
    if type(raw) is not list or len(raw)!=n:
        raise ValueError("One weight per asset required.")
    weights=[number(x) for x in raw]
    if any(w<0 for w in weights) or sum(weights)!=1:
        raise ValueError("Long-only weights must sum exactly to one.")
    raw=request["returns"]
    if type(raw) is not list or not 2<=len(raw)<=MAX_ROWS:
        raise ValueError("Expected two to 2520 aligned observation rows.")
    rows=[]
    for row in raw:
        if type(row) is not list or len(row)!=n:
            raise ValueError("Every observation must contain all asset returns.")
        rows.append([rate(x) for x in row])
    columns=list(zip(*rows))
    portfolio=[sum((w*x for w,x in zip(weights,row)),Fraction(0)) for row in rows]
    cov=[[covariance(x,y) for y in columns] for x in columns]
    correlation=[]
    with localcontext() as ctx:
        ctx.prec=40
        for i in range(n):
            correlation.append([str(dec(cov[i][j])/root(cov[i][i]*cov[j][j]))
                                if cov[i][i]>0 and cov[j][j]>0 else None for j in range(n)])
    result={"basis":BASIS,"assets":assets,"observations":len(rows),
            "periods_per_year":periods,"period_risk_free_rate":metric(rf),
            "weights":[metric(w) for w in weights],
            "asset_statistics":[summarize(xs,periods,confidence,rf) for xs in columns],
            "annualized_covariance":[[metric(c*periods) for c in row] for row in cov],
            "correlation":correlation,
            "portfolio":summarize(portfolio,periods,confidence,rf),
            "scope":"Historical supplied total returns, constant frictionless rebalancing. Arithmetic annualization and square-root-of-time volatility assume a consistent frequency; serial dependence is not adjusted."}
    if "benchmark_returns" in request:
        raw=request["benchmark_returns"]
        if type(raw) is not list or len(raw)!=len(rows):
            raise ValueError("Benchmark observations must align with every portfolio row.")
        bench=[rate(x) for x in raw]
        active=[p-b for p,b in zip(portfolio,bench)]
        bv=covariance(bench,bench)
        te=root(covariance(active,active)*periods)
        beta=metric(covariance(portfolio,bench)/bv) if bv else None
        info=None
        with localcontext() as ctx:
            ctx.prec=40
            if te:
                info=str(dec(mean(active)*periods)/te)
        result["benchmark"]={"statistics":summarize(bench,periods,confidence,rf),
                             "beta":beta,"annualized_tracking_error":str(te),
                             "annualized_arithmetic_active_return":metric(mean(active)*periods),
                             "annualized_information_ratio":info,
                             "scope":"Sample beta relative to supplied benchmark; tracking error is sample standard deviation of periodic active returns, annualized."}
    return result
