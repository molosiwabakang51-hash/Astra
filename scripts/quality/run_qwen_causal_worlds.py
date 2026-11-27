"""Paired public causal reasoning diagnostic with independently enumerated worlds.

Fixed binary causal systems with observational evidence, interventions and
counterfactuals. Only the optional decomposition instruction differs by arm.
No private cases, automatic routing or general intelligence score.
"""
import argparse
import hashlib
import itertools
import json
import math
from pathlib import Path
import tempfile
import subprocess
import time
import urllib.request

import run_qwen_profile_comparison as native

SEED = 71203851
CAP = 768
GUIDANCE = (
    "Distinguish observations from forced changes. Consider every original state consistent "
    "with the observed evidence. For a hypothetical forced change, replace only the rule "
    "for the forced variable, retain the same original independent inputs except those "
    "explicitly forced, and recompute downstream variables. Do not reapply old observations "
    "to the changed world. If compatible worlds disagree about the queried state, answer UNKNOWN.\n\n"
)
# name, two functions, observations in the original world, interventions, target, oracle.
# Node 2 is computed from nodes 0,1; node 3 from nodes 2,1.
CASES = (
    ("forward_0", "OR", "AND", ((0,1),(1,0)), (), 3, "NO"),
    ("abduction_0", "XOR", "OR", ((2,1),(1,1)), (), 0, "NO"),
    ("intervention_0", "AND", "AND", ((0,1),(1,1)), ((2,0),), 3, "NO"),
    ("counterfactual_0", "AND", "OR", ((2,0),(1,1)), ((1,0),), 3, "NO"),
    ("abduction_1", "AND", "OR", ((2,1),), (), 0, "YES"),
    ("forward_1", "XOR", "OR", ((0,0),(1,1)), (), 3, "YES"),
    ("counterfactual_1", "XOR", "OR", ((2,1),(1,0)), ((1,1),), 3, "YES"),
    ("intervention_1", "AND", "AND", ((0,0),(1,1)), ((2,1),), 3, "YES"),
    ("forward_2", "AND", "AND", ((0,1),), (), 3, "UNKNOWN"),
    ("abduction_2", "OR", "AND", ((2,1),), (), 0, "UNKNOWN"),
    ("intervention_2", "AND", "AND", (), ((2,1),), 3, "UNKNOWN"),
    ("counterfactual_2", "OR", "AND", ((2,1),), ((1,0),), 2, "UNKNOWN"),
    ("counterfactual_3", "XOR", "AND", ((2,1),(1,1)), ((2,0),), 0, "NO"),
    ("intervention_3", "OR", "AND", ((1,0),), ((2,1),), 0, "UNKNOWN"),
    ("abduction_3", "OR", "AND", ((3,1),), (), 1, "YES"),
    ("forward_3", "XOR", "OR", ((0,0),(1,0)), (), 3, "NO"),
)
NAMES = (
    ("amber switch","blue switch","relay","lamp"),
    ("north sensor","south sensor","gate","alarm"),
    ("left lever","right lever","valve","indicator"),
    ("input coil","backup coil","controller","beacon"),
)

def apply(op,a,b):
    if op=="AND":
        return int(bool(a and b))
    if op=="OR":
        return int(bool(a or b))
    if op=="XOR":
        return int(a != b)
    raise ValueError("Unknown causal operator")

def checked_assignments(items):
    out = dict(items)
    if len(out)!=len(items) or any(type(k) is not int or k not in range(4) or
                                  type(v) is not int or v not in (0,1)
                                  for k,v in items):
        raise ValueError("Invalid causal assignments")
    return out

def world(a,b,first,second,forced):
    a,b=forced.get(0,a),forced.get(1,b)
    c=forced.get(2,apply(first,a,b))
    d=forced.get(3,apply(second,c,b))
    return (a,b,c,d)

def oracle(case):
    name,first,second,observations,interventions,target,_=case
    observed=checked_assignments(observations)
    forced=checked_assignments(interventions)
    if type(target) is not int or target not in range(4):
        raise ValueError("Invalid causal target")
    outcomes=set()
    for a,b in itertools.product((0,1),repeat=2):
        original=world(a,b,first,second,{})
        if all(original[k]==v for k,v in observed.items()):
            outcomes.add(world(a,b,first,second,forced)[target])
    if not outcomes:
        raise ValueError("Inconsistent original evidence")
    return "YES" if outcomes=={1} else "NO" if outcomes=={0} else "UNKNOWN"

def describe(op,a,b):
    return {"AND":f"both {a} and {b} are ON",
            "OR":f"at least one of {a} and {b} is ON (possibly both)",
            "XOR":f"exactly one of {a} and {b} is ON"}[op]

def question(case,index):
    _,first,second,observations,interventions,target,_=case
    a,b,c,d=NAMES[index % len(NAMES)]
    names=(a,b,c,d)
    parts=[f"In a fictional machine, {a} and {b} are independent inputs, each either ON or OFF.",
           f"The {c} is ON if and only if {describe(first,a,b)}.",
           f"The {d} is ON if and only if {describe(second,c,b)}.",
           "All four states are binary and these are the complete rules."]
    if observations:
        parts.append("Original observations: "+ "; ".join(f"{names[k]} is {'ON' if v else 'OFF'}" for k,v in observations)+".")
    else:
        parts.append("No original states are observed.")
    if interventions:
        parts.append("In a hypothetical version of the same machine and same original inputs, "+
                     "; ".join(f"force {names[k]} to {'ON' if v else 'OFF'}" for k,v in interventions)+
                     ", overriding only the forced variables. Other rules still apply.")
        parts.append(f"Is the {names[target]} necessarily ON in that hypothetical version?")
    else:
        parts.append(f"Is the {names[target]} necessarily ON in the original version?")
    # Explicit ternary question avoids conflating "not necessarily" and "necessarily not".
    parts.append("Reply only YES if ON in every compatible state, NO if OFF in every compatible state, or UNKNOWN if both are possible.")
    return " ".join(parts)

def evaluate(infer,cases=CASES):
    if not cases or len({c[0] for c in cases})!=len(cases):
        raise ValueError("Empty or duplicate causal cases")
    # Validate all independent expected outcomes before any model calls.
    if any(oracle(c)!=c[-1] for c in cases):
        raise RuntimeError("Causal fixture answer disagrees with finite-world oracle")
    system=native.system_prompt()
    settings=native.PROFILES["qwen_think"]
    rows=[]
    for i,case in enumerate(cases):
        q=question(case,i)
        for arm in (("baseline","causal") if i%2==0 else ("causal","baseline")):
            seed=SEED+i*13
            prompt=(GUIDANCE if arm=="causal" else "")+q
            payload=dict(prompt=native.raw_prompt(system,prompt,"think"),n_predict=CAP,
                         seed=seed,cache_prompt=False,stream=False,ignore_eos=False,
                         repeat_penalty=1.0,presence_penalty=0.0,frequency_penalty=0.0,**settings)
            start=time.monotonic()
            reply=infer(payload)
            elapsed=round((time.monotonic()-start)*1000)
            for k,v in dict(settings,seed=seed,n_predict=CAP).items():
                actual=reply.get("generation_settings",{}).get(k)
                if type(actual) not in (int,float) or not math.isclose(actual,v,rel_tol=1e-6,abs_tol=1e-8):
                    raise RuntimeError("Unverified native settings")
            stop,truncated,tokens=reply.get("stop_type"),reply.get("truncated"),reply.get("tokens_predicted")
            if stop not in ("eos","word","limit") or type(truncated) is not bool or type(tokens) is not int or not 0<=tokens<=CAP+8:
                raise RuntimeError("Unverified completion metadata")
            complete=stop in ("eos","word") and not truncated
            answer=native.visible_answer(reply.get("content"))
            valid=complete and answer in ("YES","NO","UNKNOWN")
            rows.append(dict(case=case[0],arm=arm,complete=complete,valid=valid,
                             correct=valid and answer==case[-1],
                             false_certainty=valid and case[-1]=="UNKNOWN" and answer!="UNKNOWN",
                             false_abstention=valid and case[-1]!="UNKNOWN" and answer=="UNKNOWN",
                             tokens=tokens,elapsed_ms=elapsed,
                             prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest()))
    metrics=("complete","valid","correct","false_certainty","false_abstention","tokens","elapsed_ms")
    def summary(group):
        return {arm:dict(attempted=len(rr),**{k:sum(r[k] for r in rr) for k in metrics})
                for arm in ("baseline","causal") for rr in [[r for r in group if r["arm"]==arm]]}
    matched=dict(both=0,baseline_only=0,causal_only=0,neither=0)
    for case,*_ in cases:
        answers={r["arm"]:r["correct"] for r in rows if r["case"]==case}
        a,b=answers["baseline"],answers["causal"]
        matched["both" if a and b else "baseline_only" if a else "causal_only" if b else "neither"]+=1
    return dict(status="COMPLETE_CAUSAL_WORLDS_DIAGNOSTIC",seed=SEED,cap=CAP,
                model_sha256=native.MODEL_SHA256,system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                guidance_sha256=hashlib.sha256(GUIDANCE.encode()).hexdigest(),totals=summary(rows),matched=matched,
                by_domain={d:summary([r for r in rows if r["case"].startswith(d+"_")])
                           for d in ("forward","abduction","intervention","counterfactual")},
                cases=rows,official_ai_rating=None,
                scope="Public finite-world binary causal scenarios; matched thinking settings, only decomposition instruction differs. Not private assessment, live routing or device acceptance.")

def run(binary):
    if not native.check_model(native.MODEL_PATH):
        raise RuntimeError("Unverified model")
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    cmd=[str(binary.resolve()),"-m",str(native.MODEL_PATH),"--host","127.0.0.1",
         "--port",str(native.PORT),"--ctx-size",str(native.CONTEXT),"--parallel","1",
         "--threads","4","--batch-size","256","--ubatch-size","256","--n-gpu-layers","0","--no-context-shift"]
    with tempfile.TemporaryFile() as log:
        proc=subprocess.Popen(cmd,stdout=log,stderr=log)
        try:
            deadline=time.monotonic()+180
            while time.monotonic()<deadline:
                if proc.poll() is not None:
                    raise RuntimeError("Native server exited")
                try:
                    if native.request(opener,"/health",timeout=3).get("status")=="ok":
                        break
                except (OSError,ValueError):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("Native startup timed out")
            report=evaluate(lambda payload:native.request(opener,"/completion",payload))
            if not native.check_model(native.MODEL_PATH):
                raise RuntimeError("Model identity changed")
            return report
        finally:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary",type=Path)
    args=parser.parse_args()
    print(json.dumps(run(args.binary),indent=2,allow_nan=False))
