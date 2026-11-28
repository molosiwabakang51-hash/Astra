"""Public developmental generalisation probes, not independent acceptance.

Two surface forms per underlying problem. No answer choices at output, no
tool assistance, no retrieval of correct answers. Oracle constructs labels
from explicitly finite hypotheses/worlds or a chronological record.
"""
from dataclasses import dataclass, asdict
from itertools import product
import hashlib
import json

SYSTEM=("Solve using only the information supplied. Give a brief explanation if needed. "
        "End with a separate line Answer: VALUE. Use UNKNOWN if no unique answer follows.")
DOMAINS=("induction","abduction","deduction","multiturn")

@dataclass(frozen=True)
class Probe:
    ident:str
    domain:str
    form:int
    turns:tuple
    answer:str

def consensus(values):
    values=set(values)
    return next(iter(values)) if len(values)==1 else "UNKNOWN"

def yesno(value):
    return "YES" if value else "NO"

def rule_candidates(observations):
    rules=(lambda a,b:a and b,lambda a,b:a or b,
           lambda a,b:a!=b,lambda a,b:a==b)
    return tuple(r for r in rules if all(bool(r(a,b))==bool(z) for a,b,z in observations))

def induction_answer(obs,query):
    return consensus(yesno(r(*query)) for r in rule_candidates(obs))

CAUSES={"JAM":(1,0,1),"LEAK":(0,1,1),"POWER":(1,1,0)}
def abduction_answer(observations):
    return consensus(name for name,signature in CAUSES.items()
                     if all(signature[i]==v for i,v in observations))

def deduction_answer(facts,query):
    # Exactly one of A/B; C iff A; D iff B.
    worlds=[dict(zip("ABCD",bits)) for bits in product((0,1),repeat=4)]
    worlds=[w for w in worlds if w["A"]+w["B"]==1 and
            w["C"]==w["A"] and w["D"]==w["B"] and
            all(w[k]==v for k,v in facts)]
    return consensus(yesno(w[query]) for w in worlds)

def record_answer(events,target):
    records={}
    for key,value in events:
        if value is None:
            records.pop(key,None)
        else:
            records[key]=value
    return records.get(target,"UNKNOWN")

def suite():
    out=[]
    def add(domain,i,forms,answer):
        for j,turns in enumerate(forms):
            out.append(Probe(f"{domain}-{i}",domain,j,tuple(turns),answer))
    def one(text):
        return (("user",text),)
    for i,(obs,query) in enumerate((
        (((0,0,0),(1,1,0)),(0,1)),
        (((0,0,1),),(0,1)),
        (((0,1,1),),(1,1)),
        (((0,1,1),),(1,0)),
    )):
        a="; ".join(f"({x},{y}) gives {z}" for x,y,z in obs)
        b="; ".join(f"input {x} and {y}: output {z}" for x,y,z in reversed(obs))
        x,y=query
        add("induction",i,(
            one("A device uses exactly one binary rule: AND, OR, XOR or XNOR (equal inputs give 1). "
                f"Observed {a}. For input ({x},{y}), must the output be 1? YES, NO or UNKNOWN."),
            one("The only possible rules are equality, exclusive-or, inclusive-or and conjunction. "
                f"Measurements: {b}. Predict whether output at {x},{y} is necessarily 1. "
                "Answer YES if all compatible rules give 1, NO if all give 0, otherwise UNKNOWN.")
        ),induction_answer(obs,query))
    for i,obs in enumerate((((0,1),(1,0)),((0,0),),((2,0),),((0,1),))):
        a=", ".join(f"signal {j+1}={v}" for j,v in obs)
        b=", ".join(f"S{j+1} is {'on' if v else 'off'}" for j,v in reversed(obs))
        add("abduction",i,(
            one("Exactly one fault is present. Predicted signals 1,2,3: JAM=(1,0,1), LEAK=(0,1,1), POWER=(1,1,0). "
                f"Only these measurements are known: {a}. Identify the unique compatible fault or UNKNOWN."),
            one("The machine has one cause: POWER lights S1 and S2 only; LEAK lights S2 and S3 only; "
                f"JAM lights S1 and S3 only. Observed {b}. Unobserved lights may be on or off. "
                "Which single cause is established, or UNKNOWN?")
        ),abduction_answer(obs))
    for i,(facts,query) in enumerate((
        ((("A",1),),"C"),((("D",1),),"A"),((),"D"),((("C",0),),"B")
    )):
        a="; ".join(f"{k}={v}" for k,v in facts) or "No additional fact"
        b="; ".join(f"{k} is {'true' if v else 'false'}" for k,v in facts) or "No extra observation"
        add("deduction",i,(
            one("Exactly one of A and B is true. C is true if and only if A is true. "
                f"D is true if and only if B is true. {a}. Is {query} true? YES, NO or UNKNOWN."),
            one("A and B have opposite truth values; C has A's truth value and D has B's. "
                f"{b}. Does the information determine that {query} is true? "
                "YES if forced true, NO if forced false, UNKNOWN if either is possible.")
        ),deduction_answer(facts,query))
    scenarios=(
        (("R","NORTH"),("S","EAST"),("R","WEST")),
        (("R","NORTH"),("S","EAST"),("R",None)),
        (("R","NORTH"),("S","EAST"),("S","WEST")),
        (("R","NORTH"),("S","EAST"),("R",None),("R","SOUTH")),
    )
    for i,events in enumerate(scenarios):
        rest=events[2:]
        update=" ".join(f"Withdraw the recorded location of crate {k} without replacement." if v is None
                        else f"Crate {k} is now in {v}." for k,v in rest)
        update2=" ".join(f"Cancel {k}'s location entry; no new value yet." if v is None
                         else f"Set {k}'s confirmed location to {v}." for k,v in rest)
        add("multiturn",i,(
            (("user","Record these locations: crate R is in NORTH and crate S is in EAST."),
             ("assistant","Recorded the two locations."),
             ("user",update+" What is the current confirmed location of crate R?")),
            (("user","Keep track of S in EAST and R in NORTH."),
             ("assistant","I have noted both crate locations."),
             ("user",update2+" Give R's current location, or UNKNOWN if none is confirmed."))
        ),record_answer(events,"R"))
    if len(out)!=32 or len({(p.ident,p.form) for p in out})!=32:
        raise RuntimeError("Invalid suite cardinality")
    return tuple(out)

def prompt(tokenizer,probe):
    messages=[{"role":"system","content":SYSTEM}]+[{"role":r,"content":t} for r,t in probe.turns]
    ids=tokenizer.apply_chat_template(messages,tokenize=True,add_generation_prompt=True,enable_thinking=False)
    if not ids or len(ids)>512:
        raise ValueError("Invalid prompt length")
    return ids

def digest(probes):
    return hashlib.sha256(json.dumps([asdict(p) for p in probes],sort_keys=True,
        separators=(",",":")).encode()).hexdigest()

def summarize(rows,probes):
    expected={(p.ident,p.form):p for p in probes}
    actual={}
    for row in rows:
        key=(row["ident"],row["form"])
        if key not in expected or key in actual:
            raise ValueError("Unexpected or duplicate evaluation row")
        p=expected[key]
        if (row["domain"]!=p.domain or row["expected"]!=p.answer or
            type(row["complete"]) is not bool or type(row["correct"]) is not bool or
            row["correct"] and not row["complete"]):
            raise ValueError("Invalid scoring record")
        actual[key]=row
    if set(actual)!=set(expected):
        raise ValueError("Incomplete evaluation set")
    def count(rr):
        ids=sorted({r["ident"] for r in rr})
        if any({r["form"] for r in rr if r["ident"]==i}!={0,1} for i in ids):
            raise ValueError("Unpaired paraphrase")
        return dict(questions=len(ids),prompts=len(rr),completed=sum(r["complete"] for r in rr),
            correct_prompts=sum(r["correct"] for r in rr),
            both_correct=sum(all(r["correct"] for r in rr if r["ident"]==i) for i in ids),
            any_correct=sum(any(r["correct"] for r in rr if r["ident"]==i) for i in ids))
    return {"all":count(rows),**{d:count([r for r in rows if r["domain"]==d]) for d in DOMAINS}}

def paired(a,b):
    aa={(r["ident"],r["form"]):r for r in a}
    bb={(r["ident"],r["form"]):r for r in b}
    if not aa or len(aa)!=len(a) or len(bb)!=len(b) or aa.keys()!=bb.keys() or any(
        any(aa[k][field]!=bb[k][field] for field in ("domain","expected","prompt_sha256")) for k in aa):
        raise ValueError("Unmatched prompts")
    ids={k[0] for k in aa}
    robust=lambda m,i:all(m[(i,f)]["correct"] for f in (0,1))
    if any((i,f) not in aa for i in ids for f in (0,1)):
        raise ValueError("Unpaired question")
    return dict(prompt_gains=sum(not aa[k]["correct"] and bb[k]["correct"] for k in aa),
                prompt_losses=sum(aa[k]["correct"] and not bb[k]["correct"] for k in aa),
                robust_gains=sum(not robust(aa,i) and robust(bb,i) for i in ids),
                robust_losses=sum(robust(aa,i) and not robust(bb,i) for i in ids))
