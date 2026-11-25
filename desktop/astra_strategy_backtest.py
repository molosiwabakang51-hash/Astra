"""Chronological, long-only backtesting with explicit transaction-cost accounting.

All data are aligned periodic simple total returns, supplied oldest first.
A trade at the start of period t may use only observations through t-1.
Fixed weights, buy-and-hold and lagged trailing-return selection are supported.
No automatic parameter tuning, short sales, leverage, or market-data fetching.
"""
from decimal import Decimal, localcontext
from fractions import Fraction
import re

from astra_quantitative_verify import number

BASIS = "chronological_aligned_periodic_total_returns"
MAX_ROWS = 2520
MAX_ASSETS = 6
MAX_STRATEGIES = 4
NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,39}\Z")


def dec(value):
    f = Fraction(value)
    return Decimal(f.numerator)/Decimal(f.denominator)


def integer(value, lo, hi):
    if type(value) is not int or not lo <= value <= hi:
        raise ValueError("Integer outside backtest bounds.")
    return value


def valid_weights(raw, n):
    if type(raw) is not list or len(raw) != n:
        raise ValueError("Expected one weight per asset.")
    values = [number(x) for x in raw]
    if any(x < 0 for x in values) or sum(values) != 1:
        raise ValueError("Long-only weights must sum exactly to one.")
    return values


def returns(raw, n):
    if type(raw) is not list or not 2 <= len(raw) <= MAX_ROWS:
        raise ValueError("Expected 2–2520 chronological observations.")
    rows = []
    for row in raw:
        if type(row) is not list or len(row) != n:
            raise ValueError("Every row must contain all asset returns.")
        values = [number(x) for x in row]
        if any(not -1 <= x <= 10 for x in values):
            raise ValueError("Simple return outside -100% to +1000%.")
        rows.append(values)
    return rows


def rebalance(holdings, weights, fee):
    """Solve V' + fee * sum(abs(V' * w_i - h_i)) = V.

    Costs are charged on both purchases and sales. The monotone root gives
    a self-financing post-cost portfolio rather than charging a fee after
    allocating all wealth. Decimal residual is verified.
    """
    total = sum(holdings, Decimal(0))
    if not total:
        return holdings[:], Decimal(0), Decimal(0)
    def volume(value):
        return sum((abs(value*w-h) for w,h in zip(weights,holdings)), Decimal(0))
    if not fee:
        post = total
    elif not volume(total):
        return holdings[:], Decimal(0), Decimal(0)
    else:
        low, high = Decimal(0), total
        for _ in range(140):
            mid = (low+high)/2
            if mid+fee*volume(mid)>total:
                high=mid
            else:
                low=mid
        post=(low+high)/2
    traded=volume(post)
    cost=total-post
    if abs(cost-fee*traded)>total*Decimal("1e-38"):
        raise ArithmeticError("Transaction-cost reconciliation failed.")
    target=[post*w for w in weights]
    # Decimal rounding of repeating weights must not create material cash.
    target[-1]+=post-sum(target)
    if min(target)<0:
        raise ArithmeticError("Invalid post-trade allocation.")
    return target,cost,traded


def momentum_weights(rows, t, lookback, count, n):
    # Caller enforces t >= lookback; the current row is never read.
    scores=[]
    for i in range(n):
        wealth=Fraction(1)
        for row in rows[t-lookback:t]:
            wealth*=1+row[i]
        scores.append(wealth)
    # Stable input-order tie break, chosen before evaluation.
    winners=sorted(range(n),key=lambda i:(-scores[i],i))[:count]
    return [Fraction(1,count) if i in winners else Fraction(0) for i in range(n)]


def run_strategy(rows, factors, strategy, initial, fee):
    n=len(rows[0])
    target=[dec(w) for w in strategy["initial_weights"]]
    target[-1]+=1-sum(target)
    holdings=[initial*w for w in target]
    holdings[-1]+=initial-sum(holdings)
    peak=initial
    worst=Decimal(0)
    total_cost=Decimal(0)
    turnover=Decimal(0)
    trades=0
    series=[]
    for t,row in enumerate(factors):
        before=sum(holdings,Decimal(0))
        cost=volume=Decimal(0)
        did_rebalance=False
        if t and strategy["kind"]!="buy_and_hold" and t%strategy["rebalance_every"]==0 and before:
            if strategy["kind"]=="lagged_momentum":
                if t>=strategy["lookback"]:
                    target=[dec(w) for w in momentum_weights(rows,t,strategy["lookback"],strategy["top_k"],n)]
                    target[-1]+=1-sum(target)
                else:
                    target=[dec(w) for w in strategy["initial_weights"]]
                    target[-1]+=1-sum(target)
            holdings,cost,volume=rebalance(holdings,target,fee)
            if volume:
                trades+=1
                did_rebalance=True
                turnover+=volume/(2*before)
                total_cost+=cost
        post=sum(holdings,Decimal(0))
        start_weights=[str(h/post) for h in holdings] if post else None
        holdings=[h*f for h,f in zip(holdings,row)]
        ending=sum(holdings,Decimal(0))
        peak=max(peak,ending)
        drawdown=1-ending/peak
        worst=max(worst,drawdown)
        series.append({"period":t+1,"opening_wealth":str(before),
                       "post_trade_wealth":str(post),"start_weights":start_weights,
                       "transaction_cost":str(cost),"gross_traded_value":str(volume),
                       "rebalanced":did_rebalance,"closing_wealth":str(ending),
                       "net_return":str(ending/before-1) if before else None,
                       "drawdown":str(drawdown)})
    ending=sum(holdings,Decimal(0))
    return {"name":strategy["name"],"kind":strategy["kind"],
            "terminal_wealth":str(ending),"cumulative_net_return":str(ending/initial-1),
            "maximum_drawdown_loss":str(worst),"total_transaction_cost":str(total_cost),
            "cumulative_one_way_turnover":str(turnover),"rebalance_trade_count":trades,
            "series":series}


def analyze(request):
    required={"basis","currency","assets","returns","initial_wealth","strategies","transaction_cost_bps"}
    if type(request) is not dict or not required<=request.keys() or set(request)-required-{"benchmark_returns"}:
        raise ValueError("Unexpected backtest fields.")
    if request["basis"]!=BASIS:
        raise ValueError("Confirm chronological aligned total returns.")
    currency=request["currency"]
    if not isinstance(currency,str) or not re.fullmatch(r"[A-Z]{3}",currency):
        raise ValueError("Explicit three-letter reporting currency required.")
    assets=request["assets"]
    if type(assets) is not list or not 1<=len(assets)<=MAX_ASSETS or any(
            not isinstance(a,str) or not NAME.fullmatch(a) for a in assets):
        raise ValueError("Expected one to six valid asset identifiers.")
    if len({a.casefold() for a in assets})!=len(assets):
        raise ValueError("Duplicate asset identifiers.")
    n=len(assets)
    rows=returns(request["returns"],n)
    raw=request["strategies"]
    if type(raw) is not list or not 1<=len(raw)<=MAX_STRATEGIES:
        raise ValueError("Expected one to four strategies.")
    strategies=[]
    seen=set()
    for item in raw:
        if type(item) is not dict or not {"name","kind","initial_weights"}<=item.keys():
            raise ValueError("Invalid strategy definition.")
        name,kind=item["name"],item["kind"]
        if not isinstance(name,str) or not NAME.fullmatch(name) or name.casefold() in seen:
            raise ValueError("Invalid or duplicate strategy name.")
        if kind not in ("buy_and_hold","fixed_weight","lagged_momentum"):
            raise ValueError("Unsupported strategy.")
        keys={"name","kind","initial_weights"}
        if kind!="buy_and_hold":
            keys.add("rebalance_every")
        if kind=="lagged_momentum":
            keys.update(("lookback","top_k"))
        if set(item)!=keys:
            raise ValueError("Unexpected strategy fields.")
        strategy=dict(item,initial_weights=valid_weights(item["initial_weights"],n))
        if kind!="buy_and_hold":
            integer(item["rebalance_every"],1,252)
        if kind=="lagged_momentum":
            integer(item["lookback"],1,min(252,len(rows)-1))
            integer(item["top_k"],1,n)
        strategies.append(strategy)
        seen.add(name.casefold())
    initial=number(request["initial_wealth"])
    fee=number(request["transaction_cost_bps"])
    if initial<=0 or not 0<=fee<=1000:
        raise ValueError("Positive wealth and transaction costs of 0–1000 bp required.")
    benchmark=None
    if "benchmark_returns" in request:
        raw=request["benchmark_returns"]
        if type(raw) is not list or len(raw)!=len(rows):
            raise ValueError("Benchmark must align with all observations.")
        benchmark=[row[0] for row in returns([[x] for x in raw],1)]
    with localcontext() as ctx:
        ctx.prec=50
        initial=dec(initial)
        cost_rate=dec(fee)/10000
        factors=[[dec(1+x) for x in row] for row in rows]
        results=[run_strategy(rows,factors,s,initial,cost_rate) for s in strategies]
        result={"basis":BASIS,"currency":currency,"assets":assets,"observations":len(rows),
                "initial_wealth":str(initial),"transaction_cost_bps":str(dec(fee)),
                "strategies":results,
                "scope":"Historical conditional strategy replay; predeclared rules, no future observations in decision logic. No independent evidence of real execution, prediction or freedom from selection bias."}
        if benchmark is not None:
            wealth=initial
            for value in benchmark:
                wealth*=dec(1+value)
            result["benchmark"]={"terminal_wealth":str(wealth),
                                 "cumulative_return":str(wealth/initial-1)}
            for strategy in results:
                strategy["active_cumulative_return"]=str(Decimal(strategy["cumulative_net_return"])-(wealth/initial-1))
        return result
