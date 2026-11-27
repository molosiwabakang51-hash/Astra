"""Public paired induction diagnostic: deterministic hypothesis filtering + Qwen synthesis.

Both arms receive the same finite set of allowed rules, observations and unseen
query. Candidate additionally receives the surviving rule names, never their
predictions for the query. This is assisted reasoning, not raw neural ability
or unrestricted rule discovery. No production routing or independent score.
"""
import argparse
import hashlib
import itertools
import json
import math
from pathlib import Path
import random
import subprocess
import tempfile
import time
import urllib.request

import run_qwen_profile_comparison as native

SEED = 61583921
CAP = 768
RULES = (
    ("R1", "A", 240),
    ("R2", "B", 204),
    ("R3", "C", 170),
    ("R4", "NOT A", 15),
    ("R5", "A AND B", 192),
    ("R6", "A OR B", 252),
    ("R7", "exactly one of A and B", 60),
    ("R8", "A AND C", 160),
    ("R9", "A OR C", 250),
    ("R10", "exactly one of A and C", 90),
    ("R11", "B AND C", 136),
    ("R12", "B OR C", 238),
)
BY_NAME = {name: mask for name, _, mask in RULES}
GROUPS = ("unique_yes", "unique_no", "consensus_yes", "consensus_no", "ambiguous")

def predict(mask, state):
    return (mask >> state) & 1

def compatible(observations):
    """Bounded version space. Source row labels must be unique and binary."""
    rows = dict(observations)
    if len(rows) != len(observations) or len(rows) > 7 or any(
            type(state) is not int or state not in range(8) or
            type(value) is not int or value not in (0, 1)
            for state, value in observations):
        raise ValueError("Invalid observations")
    return tuple(name for name, _, mask in RULES
                 if all(predict(mask, state) == value for state, value in observations))

def reference(observations, target):
    if type(target) is not int or target not in range(8) or target in dict(observations):
        raise ValueError("Query must be an unseen binary input")
    names = compatible(observations)
    if not names:
        return names, "INCONSISTENT"
    outcomes = {predict(BY_NAME[name], target) for name in names}
    return names, "YES" if outcomes == {1} else "NO" if outcomes == {0} else "UNKNOWN"

def independent_reference(observations, target):
    """Separate functional oracle, not the bitmask filtering implementation."""
    def evaluate(state):
        a, b, c = (state >> 2) & 1, (state >> 1) & 1, state & 1
        return (a, b, c, 1-a, a*b, max(a,b), int(a!=b),
                a*c, max(a,c), int(a!=c), b*c, max(b,c))
    table = [evaluate(i) for i in range(8)]
    indices = [i for i in range(len(RULES))
               if all(table[state][i] == y for state,y in observations)]
    values = {table[target][i] for i in indices}
    return tuple(RULES[i][0] for i in indices), (
        "INCONSISTENT" if not values else "YES" if values == {1}
        else "NO" if values == {0} else "UNKNOWN")

def cases(seed=SEED):
    # Select unique public fixtures deterministically from a finite candidate pool.
    # No query labels or incompatible rules are supplied to the model as answers.
    pools = {group: [] for group in GROUPS}
    seen = set()
    for n in (2,3,4,5):
        for states in itertools.combinations(range(8), n):
            for _,_,mask in RULES:
                obs = tuple((i,predict(mask,i)) for i in states)
                for target in range(8):
                    if target in states or (obs,target) in seen:
                        continue
                    seen.add((obs,target))
                    names,answer = reference(obs,target)
                    group = ("ambiguous" if answer=="UNKNOWN" else
                             ("unique_" if len(names)==1 else "consensus_")+answer.lower())
                    if group not in pools:
                        raise RuntimeError("Unexpected generated fixture")
                    pools[group].append((obs,target,answer))
    rng = random.Random(seed)
    suite = []
    for group in GROUPS:
        size = 4 if group=="ambiguous" else 3
        for index,(obs,target,answer) in enumerate(rng.sample(pools[group],size)):
            suite.append((f"{group}_{index}",group,obs,target,answer))
    rng.shuffle(suite)
    return tuple(suite)

def bits(state):
    return f"A={(state>>2)&1}, B={(state>>1)&1}, C={state&1}"

def prompt(observations,target,assisted):
    names,_ = reference(observations,target)
    lines = ["A fictional machine uses exactly one of the following binary rules. "
             "All listed rules are possible initially; do not assume any other rule.",
             "Here 1 means ON and 0 means OFF.",
             *[f"{name}: output is 1 if and only if {description} is true."
               for name,description,_ in RULES],
             "Observed examples:"]
    lines += [f"{bits(state)} => output={y}" for state,y in observations]
    if assisted:
        lines.append("An exact filter checked the observed examples against every allowed rule. "
                     "The complete set of compatible rules is: "+", ".join(names)+".")
    lines += [f"Unseen input: {bits(target)}.",
              "Reply only YES if every compatible rule predicts output 1, "
              "NO if every compatible rule predicts output 0, or UNKNOWN if compatible rules disagree."]
    return "\n".join(lines)

def evaluate(infer,suite=None):
    suite = cases() if suite is None else tuple(suite)
    if not suite or len({c[0] for c in suite}) != len(suite):
        raise ValueError("Missing or duplicate cases")
    for name,group,obs,target,expected in suite:
        actual = reference(obs,target)
        if actual != independent_reference(obs,target) or actual[1] != expected:
            raise RuntimeError("Reference disagreement: "+name)
        correct_group = ("ambiguous" if expected=="UNKNOWN" else
                         ("unique_" if len(actual[0])==1 else "consensus_")+expected.lower())
        if not actual[0] or group!=correct_group:
            raise RuntimeError("Invalid fixture group")
    system = native.system_prompt()
    settings = native.PROFILES["qwen_think"]
    rows = []
    for index,(name,group,obs,target,expected) in enumerate(suite):
        names,_ = reference(obs,target)
        for arm in (("baseline","assisted") if index%2==0 else ("assisted","baseline")):
            seed=SEED+index*13
            request=prompt(obs,target,arm=="assisted")
            payload=dict(prompt=native.raw_prompt(system,request,"think"),n_predict=CAP,
                         seed=seed,cache_prompt=False,stream=False,ignore_eos=False,
                         repeat_penalty=1.0,presence_penalty=0.0,frequency_penalty=0.0,**settings)
            start=time.monotonic()
            reply=infer(payload)
            elapsed=round((time.monotonic()-start)*1000)
            for key,value in dict(settings,seed=seed,n_predict=CAP).items():
                found=reply.get("generation_settings",{}).get(key)
                if type(found) not in (int,float) or not math.isclose(found,value,rel_tol=1e-6,abs_tol=1e-8):
                    raise RuntimeError("Unverified native configuration")
            stop,truncated,tokens=reply.get("stop_type"),reply.get("truncated"),reply.get("tokens_predicted")
            if stop not in ("eos","word","limit") or type(truncated) is not bool or type(tokens) is not int or not 0<=tokens<=CAP+8:
                raise RuntimeError("Unverified completion metadata")
            complete=stop in ("eos","word") and not truncated
            answer=native.visible_answer(reply.get("content"))
            valid=complete and answer in ("YES","NO","UNKNOWN")
            rows.append(dict(case=name,group=group,arm=arm,compatible_count=len(names),
                             complete=complete,valid=valid,correct=valid and answer==expected,
                             false_certainty=valid and expected=="UNKNOWN" and answer!="UNKNOWN",
                             false_abstention=valid and expected!="UNKNOWN" and answer=="UNKNOWN",
                             tokens=tokens,elapsed_ms=elapsed,
                             prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest()))
    metrics=("complete","valid","correct","false_certainty","false_abstention","tokens","elapsed_ms")
    def summary(group):
        return {arm:dict(attempted=len(rr),**{k:sum(r[k] for r in rr) for k in metrics})
                for arm in ("baseline","assisted") for rr in [[r for r in group if r["arm"]==arm]]}
    matched=dict(both=0,baseline_only=0,assisted_only=0,neither=0)
    for name,*_ in suite:
        values={r["arm"]:r["correct"] for r in rows if r["case"]==name}
        a,b=values["baseline"],values["assisted"]
        matched["both" if a and b else "baseline_only" if a else "assisted_only" if b else "neither"]+=1
    return dict(status="COMPLETE_HYPOTHESIS_ASSISTANCE_DIAGNOSTIC",seed=SEED,cap=CAP,
                model_sha256=native.MODEL_SHA256,system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                suite_sha256=hashlib.sha256(json.dumps(suite).encode()).hexdigest(),
                totals=summary(rows),matched=matched,
                by_domain={g:summary([r for r in rows if r["group"]==g]) for g in GROUPS},
                cases=rows,official_ai_rating=None,
                scope="Public finite-rule induction. Exact filtering is tool assistance, not autonomous natural-language extraction or independent general intelligence. Candidate sees only compatible rule names, not target predictions.")

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
