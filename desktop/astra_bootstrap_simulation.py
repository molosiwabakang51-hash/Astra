"""Bounded paired historical block-bootstrap portfolio simulations.

The same sampled observation rows drive every allocation in a trial. This
preserves contemporaneous cross-asset dependence and within-block ordering,
but does not estimate future distributions or guarantee stationarity.
"""
from decimal import Decimal, localcontext
from fractions import Fraction
from random import Random
import re

from astra_quantitative_verify import number

BASIS = "aligned_periodic_total_returns_constant_weights"
MAX_ROWS = 2520
MAX_ASSETS = 6
MAX_ALLOCATIONS = 4
MAX_PATHS = 10000
MAX_PERIODS = 252
MAX_WORK = 1000000
NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,39}\Z")


def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError("Integer outside simulation bounds.")
    return value


def decimal(value):
    f = Fraction(value)
    return Decimal(f.numerator)/Decimal(f.denominator)


def empirical_quantile(values, q):
    """Inverse empirical CDF at q in (0,1], with no interpolation."""
    rank = (q*len(values)).__ceil__()
    return values[rank-1]


def tail_mean(values, confidence):
    """Mean of upper loss tail with fractional boundary probability mass."""
    mass = (1-confidence)*len(values)
    whole = mass.__floor__()
    remainder = mass-whole
    descending = sorted(values, reverse=True)
    total = sum(descending[:whole], Decimal(0))
    if remainder:
        total += decimal(remainder)*descending[whole]
    return total/decimal(mass)


def path_indices(rng, observations, horizon, block):
    remaining = horizon
    while remaining:
        start = rng.randrange(observations-block+1)
        take = min(remaining,block)
        yield from range(start,start+take)
        remaining -= take


def summary(wealth, drawdowns, initial, target, confidence):
    ordered = sorted(wealth)
    n = len(ordered)
    losses = [1-value/initial for value in ordered]
    sorted_losses = sorted(losses)
    return {
        "mean_terminal_wealth": str(sum(ordered,Decimal(0))/n),
        "terminal_quantiles": {str(q):str(empirical_quantile(ordered,Fraction(q,100)))
                               for q in (5,25,50,75,95)},
        "minimum_terminal_wealth":str(ordered[0]),
        "maximum_terminal_wealth":str(ordered[-1]),
        "loss_path_count":sum(value<initial for value in ordered),
        "loss_path_fraction":str(Decimal(sum(value<initial for value in ordered))/n),
        "below_target_path_count":sum(value<target for value in ordered),
        "below_target_path_fraction":str(Decimal(sum(value<target for value in ordered))/n),
        "terminal_var_loss":str(empirical_quantile(sorted_losses,confidence)),
        "terminal_expected_shortfall_loss":str(tail_mean(losses,confidence)),
        "median_max_drawdown":str(empirical_quantile(sorted(drawdowns),Fraction(1,2))),
        "p95_max_drawdown":str(empirical_quantile(sorted(drawdowns),Fraction(95,100))),
    }


def analyze(request):
    fields={"currency","basis","assets","returns","allocations","initial_wealth",
            "target_wealth","paths","horizon_periods","block_length","seed","confidence"}
    if type(request) is not dict or set(request)!=fields or request["basis"]!=BASIS:
        raise ValueError("Explicit aligned total-return simulation inputs required.")
    currency=request["currency"]
    if not isinstance(currency,str) or not re.fullmatch(r"[A-Z]{3}",currency):
        raise ValueError("Explicit three-letter reporting currency required.")
    assets=request["assets"]
    if type(assets) is not list or not 1<=len(assets)<=MAX_ASSETS or any(
            not isinstance(a,str) or not NAME.fullmatch(a) for a in assets):
        raise ValueError("Expected one to six named assets.")
    if len({a.casefold() for a in assets})!=len(assets):
        raise ValueError("Duplicate asset identifiers.")
    raw=request["returns"]
    if type(raw) is not list or not 2<=len(raw)<=MAX_ROWS:
        raise ValueError("Expected two to 2520 aligned observations.")
    rows=[]
    for row in raw:
        if type(row) is not list or len(row)!=len(assets):
            raise ValueError("Every row must contain all asset returns.")
        values=[number(x) for x in row]
        if any(not -1<=v<=10 for v in values):
            raise ValueError("Simple returns outside supported range.")
        rows.append(values)
    raw=request["allocations"]
    if type(raw) is not list or not 1<=len(raw)<=MAX_ALLOCATIONS:
        raise ValueError("Expected one to four allocations.")
    allocations=[]
    seen=set()
    for item in raw:
        if type(item) is not dict or set(item)!={"name","weights"}:
            raise ValueError("Allocation requires name and weights.")
        name,weights=item["name"],item["weights"]
        if not isinstance(name,str) or not NAME.fullmatch(name) or name.casefold() in seen:
            raise ValueError("Invalid or duplicate allocation name.")
        if type(weights) is not list or len(weights)!=len(assets):
            raise ValueError("One weight per asset required.")
        w=[number(x) for x in weights]
        if any(v<0 for v in w) or sum(w)!=1:
            raise ValueError("Long-only weights must sum exactly to one.")
        allocations.append((name,w))
        seen.add(name.casefold())
    paths=integer(request["paths"],1,MAX_PATHS)
    horizon=integer(request["horizon_periods"],1,MAX_PERIODS)
    block=integer(request["block_length"],1,min(len(rows),horizon))
    seed=integer(request["seed"],0,2**64-1)
    if paths*horizon*len(allocations)>MAX_WORK:
        raise ValueError("Simulation exceeds one million allocation-period updates.")
    initial=number(request["initial_wealth"])
    target=number(request["target_wealth"])
    confidence=number(request["confidence"])
    if initial<=0 or target<0 or not Fraction(1,2)<=confidence<=Fraction(99,100):
        raise ValueError("Invalid wealth or confidence inputs.")

    with localcontext() as ctx:
        ctx.prec=50
        initial,target=decimal(initial),decimal(target)
        factors=[[decimal(1+sum(w*x for w,x in zip(weights,row))) for row in rows]
                 for _,weights in allocations]
        terminal=[[] for _ in allocations]
        drawdowns=[[] for _ in allocations]
        rng=Random(seed)
        for _ in range(paths):
            wealth=[initial]*len(allocations)
            peaks=wealth[:]
            worst=[Decimal(0)]*len(allocations)
            for row in path_indices(rng,len(rows),horizon,block):
                for i in range(len(allocations)):
                    wealth[i]*=factors[i][row]
                    peaks[i]=max(peaks[i],wealth[i])
                    worst[i]=max(worst[i],1-wealth[i]/peaks[i])
            for i in range(len(allocations)):
                terminal[i].append(wealth[i])
                drawdowns[i].append(worst[i])
        results=[]
        for i,(name,weights) in enumerate(allocations):
            results.append({"name":name,"weights":[str(decimal(w)) for w in weights],
                            **summary(terminal[i],drawdowns[i],initial,target,confidence)})
        comparisons=[]
        for i in range(1,len(allocations)):
            differences=[a-b for a,b in zip(terminal[i],terminal[0])]
            comparisons.append({
                "allocation":allocations[i][0],"baseline":allocations[0][0],
                "outperform_path_count":sum(d>0 for d in differences),
                "tie_path_count":sum(d==0 for d in differences),
                "underperform_path_count":sum(d<0 for d in differences),
                "mean_terminal_wealth_difference":str(sum(differences,Decimal(0))/paths),
            })
        return {
            "currency":currency,"basis":BASIS,"assets":assets,
            "observations":len(rows),"paths":paths,"horizon_periods":horizon,
            "block_length":block,"seed":seed,"initial_wealth":str(initial),
            "target_wealth":str(target),"confidence":str(decimal(confidence)),
            "tail_path_equivalents":str(decimal((1-confidence)*paths)),
            "thin_simulated_tail":(1-confidence)*paths<5,
            "allocations":results,"paired_comparisons":comparisons,
            "scope":"Conditional synthetic paths from uniformly sampled overlapping noncircular historical blocks. Constant rebalancing, no flows, costs, taxes or forecasts. Reproducible under the same Python runtime and inputs; path frequencies are not calibrated future probabilities.",
        }
