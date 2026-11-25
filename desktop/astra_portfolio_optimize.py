"""Deterministic constrained long-only mean-variance grid optimization.

A finite allocation grid is exhaustively enumerated under explicit limits.
Global optima are established on that grid only, not in continuous space.
Inputs are supplied annual arithmetic return estimates and annual covariance,
not forecasts inferred from prices. No network or external solver required.
"""
from decimal import Decimal, localcontext
from fractions import Fraction
from math import comb, lcm
import re

from astra_quantitative_verify import number

MAX_ASSETS = 6
MAX_GRID = 150000
MAX_FRONTIER = 200
SYMBOL = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,39}\Z")


def metric(value):
    value = Fraction(value)
    with localcontext() as ctx:
        ctx.prec = 40
        approximation = str(Decimal(value.numerator)/Decimal(value.denominator))
    return {"numerator":str(value.numerator),"denominator":str(value.denominator),
            "decimal_approximation":approximation}


def root(value):
    with localcontext() as ctx:
        ctx.prec = 40
        return (Decimal(value.numerator)/Decimal(value.denominator)).sqrt()


def psd(matrix):
    """Exact Schur-complement PSD test, including singular zero pivots."""
    a = [list(row) for row in matrix]
    for k in range(len(a)):
        pivot = a[k][k]
        if pivot < 0:
            return False
        if pivot == 0:
            if any(a[k][j] != 0 for j in range(k+1,len(a))):
                return False
            continue
        for i in range(k+1,len(a)):
            for j in range(i,len(a)):
                a[i][j] -= a[i][k]*a[k][j]/pivot
                a[j][i] = a[i][j]
    return True


def scaled(values):
    denominator = lcm(*(v.denominator for v in values))
    return [int(v*denominator) for v in values], denominator


def allocations(lower, upper, total, prefix=()):
    if len(lower)==1:
        if lower[0]<=total<=upper[0]:
            yield prefix+(total,)
        return
    lo=max(lower[0],total-sum(upper[1:]))
    hi=min(upper[0],total-sum(lower[1:]))
    for weight in range(lo,hi+1):
        yield from allocations(lower[1:],upper[1:],total-weight,prefix+(weight,))


def analyze(request):
    required={"assets","expected_returns","covariance","risk_free_rate","grid_units"}
    if type(request) is not dict or not required<=request.keys() or set(request)-required-{"bounds","target_returns"}:
        raise ValueError("Unexpected optimization inputs.")
    assets=request["assets"]
    if type(assets) is not list or not 2<=len(assets)<=MAX_ASSETS or any(
            not isinstance(s,str) or not SYMBOL.fullmatch(s) for s in assets):
        raise ValueError("Expected two to six valid asset identifiers.")
    n=len(assets)
    if len({s.casefold() for s in assets})!=n:
        raise ValueError("Duplicate asset identifiers.")
    units=request["grid_units"]
    if type(units) is not int or not 4<=units<=100:
        raise ValueError("Grid units must be an integer from four to 100.")
    if comb(units+n-1,n-1)>MAX_GRID:
        raise ValueError("Allocation grid exceeds enumeration budget; choose fewer assets or coarser units.")
    raw=request["expected_returns"]
    if type(raw) is not list or len(raw)!=n:
        raise ValueError("Expected one annual return per asset.")
    mu=[number(x) for x in raw]
    rf=number(request["risk_free_rate"])
    if any(not -1<=v<=10 for v in mu) or not -1<=rf<=10:
        raise ValueError("Return estimates outside supported annual range.")
    raw=request["covariance"]
    if type(raw) is not list or len(raw)!=n or any(type(row) is not list or len(row)!=n for row in raw):
        raise ValueError("Expected a square covariance matrix.")
    cov=[[number(x) for x in row] for row in raw]
    if any(abs(cov[i][j])>100 or cov[i][j]!=cov[j][i] for i in range(n) for j in range(n)) or not psd(cov):
        raise ValueError("Covariance must be symmetric, bounded and positive semidefinite.")
    bounds=request.get("bounds",{})
    if type(bounds) is not dict or not bounds.keys()<=set(assets):
        raise ValueError("Bounds must refer to supplied assets.")
    lower,upper=[],[]
    for asset in assets:
        bound=bounds.get(asset,{"min_units":0,"max_units":units})
        if type(bound) is not dict or set(bound)!={"min_units","max_units"}:
            raise ValueError("Bounds require explicit minimum and maximum grid units.")
        lo,hi=bound["min_units"],bound["max_units"]
        if type(lo) is not int or type(hi) is not int or not 0<=lo<=hi<=units:
            raise ValueError("Invalid long-only allocation bounds.")
        lower.append(lo)
        upper.append(hi)
    if not sum(lower)<=units<=sum(upper):
        raise ValueError("Allocation bounds cannot sum to one.")
    raw_targets=request.get("target_returns",[])
    if type(raw_targets) is not list or len(raw_targets)>20:
        raise ValueError("At most twenty return targets supported.")
    targets=[number(x) for x in raw_targets]
    if len(set(targets))!=len(targets) or any(not -1<=x<=10 for x in targets):
        raise ValueError("Invalid or duplicate return target.")

    # Integer scaling makes every grid comparison exact and avoids expensive
    # Fraction operations inside the enumeration loop.
    ret,ret_scale=scaled(mu+[rf])
    riskfree=ret[-1]*units
    flat,cov_scale=scaled([v for row in cov for v in row])
    C=[flat[i*n:(i+1)*n] for i in range(n)]
    ret_den=ret_scale*units
    var_den=cov_scale*units*units
    points={}
    minimum=None
    best_positive=None
    zero_risk_positive=None
    count=0
    for w in allocations(lower,upper,units):
        count+=1
        ret_num=sum(ret[i]*w[i] for i in range(n))
        var_num=sum(C[i][i]*w[i]*w[i] for i in range(n))+2*sum(
            C[i][j]*w[i]*w[j] for i in range(n) for j in range(i+1,n))
        if var_num<0:
            raise ArithmeticError("Negative variance after exact PSD validation.")
        point=(var_num,ret_num,w)
        if minimum is None or (var_num,-ret_num,w)<(minimum[0],-minimum[1],minimum[2]):
            minimum=point
        prior=points.get(ret_num)
        if prior is None or (var_num,w)<(prior[0],prior[2]):
            points[ret_num]=point
        excess=ret_num-riskfree
        if excess>0:
            if var_num==0:
                if zero_risk_positive is None or (-ret_num,w)<(-zero_risk_positive[1],zero_risk_positive[2]):
                    zero_risk_positive=point
            elif best_positive is None:
                best_positive=point
            else:
                old_var,old_ret,old_w=best_positive
                comparison=excess*excess*old_var-(old_ret-riskfree)**2*var_num
                if comparison>0 or (comparison==0 and (var_num,-ret_num,w)<(old_var,-old_ret,old_w)):
                    best_positive=point
    if minimum is None:
        raise ValueError("No feasible allocations.")

    frontier=[]
    best_var=None
    for r in sorted(points,reverse=True):
        point=points[r]
        if best_var is None or point[0]<best_var:
            frontier.append(point)
            best_var=point[0]
    frontier.reverse()
    def describe(point):
        v,r,w=point
        variance=Fraction(v,var_den)
        expected=Fraction(r,ret_den)
        volatility=root(variance)
        sharpe=None
        if v:
            with localcontext() as ctx:
                ctx.prec=40
                excess=expected-rf
                sharpe=str((Decimal(excess.numerator)/Decimal(excess.denominator))/volatility)
        return {"weight_units":dict(zip(assets,w)),
                "weights":{asset:metric(Fraction(weight,units)) for asset,weight in zip(assets,w)},
                "expected_return":metric(expected),"variance":metric(variance),
                "volatility":str(volatility),"sharpe_ratio":sharpe}

    # Report a bounded sample, preserving both frontier endpoints. Target
    # portfolios are still selected using the complete nondominated frontier.
    full_count=len(frontier)
    if full_count>MAX_FRONTIER:
        indices=[i*(full_count-1)//(MAX_FRONTIER-1) for i in range(MAX_FRONTIER)]
        displayed=[frontier[i] for i in indices]
    else:
        displayed=frontier
    selected=[]
    for target in targets:
        eligible=next((p for p in frontier if Fraction(p[1],ret_den)>=target),None)
        selected.append({"target":metric(target),"status":"feasible" if eligible else "infeasible",
                         "portfolio":describe(eligible) if eligible else None})
    correlation=[]
    for i in range(n):
        row=[]
        for j in range(n):
            if cov[i][i]==0 or cov[j][j]==0:
                row.append(None)
            else:
                with localcontext() as ctx:
                    ctx.prec=40
                    x=cov[i][j]
                    row.append(str((Decimal(x.numerator)/Decimal(x.denominator))/root(cov[i][i]*cov[j][j])))
        correlation.append(row)
    if zero_risk_positive is not None:
        sharpe_status="positive_excess_zero_variance_no_finite_sharpe"
        sharpe_portfolio=describe(zero_risk_positive)
    elif best_positive is not None:
        sharpe_status="maximum_positive_sharpe_on_grid"
        sharpe_portfolio=describe(best_positive)
    else:
        sharpe_status="no_positive_excess_return_on_grid"
        sharpe_portfolio=None
    return {
        "assets":assets,"grid_units":units,"grid_step":metric(Fraction(1,units)),
        "bounds_units":{a:{"min":lo,"max":hi} for a,lo,hi in zip(assets,lower,upper)},
        "expected_returns":[metric(x) for x in mu],
        "risk_free_rate":metric(rf),"covariance":[[metric(x) for x in row] for row in cov],
        "correlation":correlation,"feasible_allocations":count,
        "minimum_variance":describe(minimum),"sharpe_status":sharpe_status,
        "maximum_sharpe":sharpe_portfolio,"frontier_count":full_count,
        "efficient_frontier":[describe(p) for p in displayed],
        "frontier_sampled":full_count>MAX_FRONTIER,"targets":selected,
        "scope":"Exact exhaustive long-only allocation grid comparisons under supplied annual arithmetic estimates; numerical square roots; not continuous optimization or investment advice.",
    }
