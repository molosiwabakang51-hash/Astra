"""Matched public reasoning curriculum: vary the surface form, not the answer.

Each underlying task has two semantically equivalent prompts, one verified
reasoning trace, and a finite oracle specification. Development names, values,
and wording differ from training. These are not independent held-out items.
"""
from dataclasses import dataclass, asdict
from itertools import product
import hashlib
import json
import random
from qwen_cross_domain_transfer import Probe, DOMAINS, SYSTEM, prompt

SEED=720183

@dataclass(frozen=True)
class Task:
    ident:str
    domain:str
    forms:tuple
    answer:str
    rationale:str
    oracle:tuple

    def probes(self):
        return tuple(Probe(self.ident,self.domain,i,p,self.answer) for i,p in enumerate(self.forms))

def one(s):
    return (("user",s),)

def decide(values):
    vals=set(values)
    return next(iter(vals)) if len(vals)==1 else "UNKNOWN"

def suite(training):
    if type(training) is not bool:
        raise ValueError("Invalid split")
    rng=random.Random(SEED+(0 if training else 1))
    n=8 if training else 4
    split="train" if training else "dev"
    rows=[]
    for i in range(n):
        tags=tuple(f"{'K' if training else 'V'}{i}{c}" for c in "ABC")
        # Finite hypotheses: each row describes outputs at four input symbols.
        # Construct one unique or two compatible hypotheses, balanced query
        # outcomes. Query never occurs in the observations.
        symbols=tuple(f"{'p' if training else 'z'}{i}{j}" for j in range(4))
        observed=rng.sample(range(4),2)
        query=next(j for j in range(4) if j not in observed)
        tables=[list(rng.choices((0,1),k=4)) for _ in range(3)]
        chosen=i%3
        compatible=[chosen] if i%4 in (0,1) else [chosen,(chosen+1)%3]
        values=[tables[chosen][j] for j in observed]
        for k,t in enumerate(tables):
            for j,v in zip(observed,values):
                t[j]=v
            if k not in compatible:
                t[observed[k%2]]=1-values[k%2]
            t[query]=(i%2 if i%4!=3 else compatible.index(k)%2) if k in compatible else rng.randrange(2)
        hypotheses=tuple(tuple(t) for t in tables)
        matches=[k for k,t in enumerate(hypotheses) if all(t[j]==v for j,v in zip(observed,values))]
        answer=decide("YES" if hypotheses[k][query] else "NO" for k in matches)
        trace=f"Compatible rules: {', '.join(tags[k] for k in matches)}. Query outputs: {', '.join(str(hypotheses[k][query]) for k in matches)}."
        observations=", ".join(f"{symbols[j]}={v}" for j,v in zip(observed,values))
        table="; ".join(f"{tag}: "+", ".join(f"{s}={v}" for s,v in zip(symbols,t)) for tag,t in zip(tags,hypotheses))
        reversed_table="; ".join(f"{tag} gives "+", ".join(f"{v} on {s}" for s,v in reversed(list(zip(symbols,t)))) for tag,t in reversed(list(zip(tags,hypotheses))))
        q1=(f"Exactly one rule is used. {table}. Observed {observations}. Must output on {symbols[query]} be 1? YES, NO or UNKNOWN." if training else
            f"A controller follows one of these complete lookup tables: {table}. Measurements: {observations}. Is a 1 at {symbols[query]} forced? Use YES for forced 1, NO for forced 0, UNKNOWN otherwise.")
        q2=(f"Possible rules are {reversed_table}. Actual readings: {observations}. For {symbols[query]}, say YES if all remaining rules output 1, NO if all output 0, otherwise UNKNOWN." if training else
            f"Three alternative behaviours are specified: {reversed_table}. Retain only behaviours matching {observations}. For input {symbols[query]}, determine whether the output is necessarily 1 (YES), necessarily 0 (NO), or not fixed (UNKNOWN).")
        rows.append(Task(f"{split}-induction-{i}","induction",(one(q1),one(q2)),answer,trace,
                         (hypotheses,tuple(zip(observed,values)),query)))

        # Abduction: observed signal assignments filter exactly one underlying
        # cause or several indistinguishable ones. Reorder evidence and causes.
        names=tuple(f"{'Fault' if training else 'Cause'}{i}{c}" for c in "XYZ")
        signatures=tuple(tuple(int(j==k) for j in range(3)) for k in range(3))
        cause=rng.randrange(3)
        obs=((cause,1),) if i%2==0 else (((cause+1)%3,0),)
        valid=[k for k,s in enumerate(signatures) if all(s[j]==v for j,v in obs)]
        answer=decide(names[k] for k in valid)
        trace="Causes consistent with every observed signal: "+", ".join(names[k] for k in valid)+"."
        listing="; ".join(f"{name}: {','.join(map(str,s))}" for name,s in zip(names,signatures))
        listing2="; ".join(f"{name} activates only sensor {k+1}" for k,name in reversed(list(enumerate(names))))
        observation=", ".join(f"sensor {j+1}={v}" for j,v in obs)
        q1=f"Exactly one {'fault' if training else 'explanation'} holds. Sensor patterns 1,2,3: {listing}. Measured {observation}. Which cause is uniquely established, or UNKNOWN?"
        q2=f"Consider these mutually exclusive causes: {listing2}. Every other sensor is off for each cause. Evidence: {observation}. Unmeasured sensors are unknown. Name the sole compatible cause or UNKNOWN."
        rows.append(Task(f"{split}-abduction-{i}","abduction",(one(q1),one(q2)),answer,trace,(names,signatures,obs)))

        # Deduction: a three-variable parity relation. Observing zero, one or
        # two other variables changes whether the query is forced.
        vars_=tuple(f"{'T' if training else 'W'}{i}{c}" for c in "ABC")
        a,b,c=vars_
        parity=i%2
        k=i%3
        facts=() if k==0 else ((0,rng.randrange(2)),) if k==1 else ((0,rng.randrange(2)),(1,rng.randrange(2)))
        worlds=[bits for bits in product((0,1),repeat=3) if sum(bits)%2==parity and all(bits[j]==v for j,v in facts)]
        answer=decide("YES" if w[2] else "NO" for w in worlds)
        trace="Possible query values under the parity constraint: "+", ".join(map(str,sorted({w[2] for w in worlds})))+"."
        fa=", ".join(f"{vars_[j]}={v}" for j,v in facts) or "no additional observations"
        q1=f"Bits {a}, {b}, {c} have {'odd' if parity else 'even'} total parity. Given {fa}, must {c}=1? YES if forced 1, NO if forced 0, otherwise UNKNOWN."
        q2=f"The sum of the three binary values {c}, {a}, {b}, reduced modulo 2, is {parity}. Known: {fa}. Determine if {c} is fixed at one (YES), fixed at zero (NO), or undetermined (UNKNOWN)."
        rows.append(Task(f"{split}-deduction-{i}","deduction",(one(q1),one(q2)),answer,trace,(parity,facts)))

        # Real message roles, with a scripted acknowledgement. Target entity
        # can be changed, withdrawn, unaffected or restored after withdrawal.
        keys=(f"{'R' if training else 'Box'}{i}A",f"{'R' if training else 'Box'}{i}B")
        x,y=keys
        old,new,other=rng.sample(range(10,99) if training else range(200,400),3)
        initial=((x,str(old)),(y,str(other)))
        events=(((x,str(new)),),((x,None),),((y,str(new)),),((x,None),(x,str(new))))[i%4]
        record=dict(initial)
        for key,val in events:
            if val is None: record.pop(key,None)
            else: record[key]=val
        answer=record.get(x,"UNKNOWN")
        trace=f"Only the latest confirmed entry for {x} is relevant: {answer}."
        def changes(form):
            return " ".join((f"Withdraw {key}'s value." if form==0 else f"Cancel the entry for {key}; no replacement yet.") if val is None else
                            (f"Set {key} to {val}." if form==0 else f"The confirmed number for {key} is now {val}.") for key,val in events)
        turns1=(("user",f"Record {x}={old}, {y}={other}."),("assistant","Recorded."),("user",changes(0)+f" What is the current confirmed value of {x}, or UNKNOWN?"))
        turns2=(("user",f"Keep two separate entries: {y} contains {other}; {x} contains {old}."),("assistant","Both entries noted."),("user",changes(1)+f" Give the latest valid entry for {x}; if none, UNKNOWN."))
        rows.append(Task(f"{split}-multiturn-{i}","multiturn",(turns1,turns2),answer,trace,(initial,events,x)))
    if len(rows)!=4*n or len({t.ident for t in rows})!=len(rows):
        raise RuntimeError("Invalid task cardinality")
    rng.shuffle(rows)
    return tuple(rows)

def schedule(tasks,varied):
    if type(varied) is not bool:
        raise ValueError("Invalid arm")
    # Same ordered latent problems, traces and answers. Only the second
    # presentation differs; number of source tokens is not controlled.
    return tuple((t,f if varied else 0) for t in tasks for f in (0,1))

def encode_task(tok,task,form):
    prefix=prompt(tok,task.probes()[form])
    trace="Reason: "+task.rationale+"\nAnswer: "
    suffix=trace+task.answer
    enc=tok(suffix,add_special_tokens=False,return_offsets_mapping=True)
    ids=list(enc["input_ids"]); offsets=enc["offset_mapping"]
    if ids!=tok.encode(suffix,add_special_tokens=False) or not ids or len(ids)!=len(offsets) or any(not 0<=a<b<=len(suffix) for a,b in offsets):
        raise ValueError("Invalid target tokenization")
    mask=[int(b>len(trace)) for a,b in offsets]+[1]
    ids+=[tok.eos_token_id]
    if not any(mask[:-1]) or all(mask) or len(prefix)+len(ids)>640:
        raise ValueError("Invalid supervised suffix")
    return prefix+ids,prefix,mask

def digest(tasks):
    return hashlib.sha256(json.dumps([asdict(t) for t in tasks],sort_keys=True,separators=(",",":")).encode()).hexdigest()
