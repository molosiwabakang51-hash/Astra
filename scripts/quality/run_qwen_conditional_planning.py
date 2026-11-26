"""Conditional calculator planning retry: host-only, no production activation.

Measure exact-value gains and inappropriate tool acceptance separately. A
rejected plan is not evidence that a question is solvable; explicit NONE and
incomplete planning are preserved. The selector never receives an answer key.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import subprocess
import tempfile
import time
import urllib.request

import run_qwen_calculator_probe as calc
import run_qwen_profile_comparison as native

SEED = 92846173

def cases(seed):
    rng = random.Random(seed)
    a,b,c,d = rng.randint(12,23),rng.randint(4,8),rng.randint(2,5),rng.randint(8,17)
    return [
        ("eligible_boxes",f"A depot has {a} boxes of {b} items. It ships {c} full boxes, then loses {d} individual items. How many remain? Reply only with the number.",str((a-c)*b-d)),
        ("eligible_revenue",f"A shop sells {a} items for {d} coins each, with purchase cost {b} coins per item and a fee of {c} coins. What is its net profit? Reply only with the number.",str(a*(d-b)-c)),
        ("eligible_unit",f"A total bill of {a*b+c} coins covers {a} identical items and a single fee of {c} coins. What is the cost per item? Reply only with the number.",str(b)),
        ("eligible_weight",f"A crate holds {a} pieces weighing {b} grams each, and packaging weighing {d} grams. What is the total mass? Reply only with the number.",str(a*b+d)),
        ("eligible_change",f"A counter starts at {d}, decreases by {c}, then is multiplied by {b}. What is its final value? Reply only with the number.",str((d-c)*b)),
        ("eligible_fraction",f"Divide {a} by {b}, then multiply that result by {b}. What is the exact result? Reply only with the number.",str(a)),
        ("eligible_average",f"There are 3 measurements: {d-c}, {d}, and {d+c}. What is their mean? Reply only with the number.",str(d)),
        ("eligible_negative",f"An account has {c} coins and spends {a} coins. Negative balances are allowed. What is the final balance? Reply only with the number.",str(c-a)),
        ("unsuitable_missing",f"A shop buys {a} items. The unit cost is not known. What is the total cost?",None),
        ("unsuitable_comma",f"Add {a},{b} and {c}. The comma convention is unspecified.",None),
        ("unsuitable_undefined",f"What is the exact numeric result of dividing {d} by 0?",None),
        ("unsuitable_nonnumeric",f"A text has {a} pages and another has {b} pages. Which has more accurate factual claims?",None),
    ]

def should_retry(complete, proposal, parsed):
    # Never reinterpret an explicit abstention, incomplete answer, or an
    # accepted expression as permission to search for a different answer.
    return complete and parsed is None and bool(proposal.strip()) and proposal.strip() != "NONE"

def evaluate(infer, invoke, seed=SEED, suite=None):
    suite=cases(seed) if suite is None else suite
    system=native.system_prompt()
    rows=[]
    def generate(prompt,mode,cap,call_seed):
        settings=native.PROFILES["qwen_"+mode]
        payload=dict(prompt=native.raw_prompt(system,prompt,mode),n_predict=cap,seed=call_seed,
                     cache_prompt=False,stream=False,ignore_eos=False,
                     repeat_penalty=1.0,presence_penalty=0.0,frequency_penalty=0.0,**settings)
        t=time.monotonic()
        reply=infer(payload)
        elapsed=round((time.monotonic()-t)*1000)
        actual=reply.get("generation_settings",{})
        for k,v in dict(settings,seed=call_seed,n_predict=cap).items():
            x=actual.get(k)
            if type(x) not in (int,float) or not math.isclose(x,v,rel_tol=1e-6,abs_tol=1e-8):
                raise RuntimeError("Unverified native settings")
        stop,truncated,tokens=reply.get("stop_type"),reply.get("truncated"),reply.get("tokens_predicted")
        if stop not in ("eos","word","limit") or type(truncated) is not bool:
            raise RuntimeError("Unverified completion")
        if type(tokens) is not int or not 0<=tokens<=cap+8:
            raise RuntimeError("Unverified token count")
        return native.visible_answer(reply.get("content")),stop in ("eos","word") and not truncated,tokens,elapsed
    for i,(case,question,expected) in enumerate(suite):
        prompt=invoke("plan",question)
        first,done,tokens,ms=generate(prompt,"direct",128,(seed+i*13)%(2**31-1))
        old=invoke("evaluate",question,first) if done else None
        retry=should_retry(done,first,old)
        candidate=old
        retry_done=None
        retry_none=False
        rt=rm=0
        if retry:
            second,retry_done,rt,rm=generate(prompt,"think",768,(seed+i*13+104729)%(2**31-1))
            retry_none=retry_done and second.strip()=="NONE"
            candidate=invoke("evaluate",question,second) if retry_done else None
        # Expected values only enter evaluator-side scoring after selection.
        eligible=expected is not None
        rows.append(dict(case=case,eligible=eligible,baseline_accepted=old is not None,
                         candidate_accepted=candidate is not None,
                         baseline_correct=bool(eligible and old is not None and calc.scalar_equal(old[0],expected)),
                         candidate_correct=bool(eligible and candidate is not None and calc.scalar_equal(candidate[0],expected)),
                         baseline_complete=done,baseline_none=done and first.strip()=="NONE",
                         retried=retry,retry_complete=retry_done,retry_none=retry_none,
                         baseline_tokens=tokens,candidate_tokens=tokens+rt,
                         baseline_ms=ms,candidate_ms=ms+rm))
    positive=[r for r in rows if r["eligible"]]
    negative=[r for r in rows if not r["eligible"]]
    return dict(status="COMPLETE_CONDITIONAL_PLANNING_DIAGNOSTIC",seed=seed,
                model_sha256=native.MODEL_SHA256,system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                eligible=len(positive),unsuitable=len(negative),
                baseline_correct=sum(r["baseline_correct"] for r in positive),
                candidate_correct=sum(r["candidate_correct"] for r in positive),
                candidate_only=sum(r["candidate_correct"] and not r["baseline_correct"] for r in positive),
                baseline_only=sum(r["baseline_correct"] and not r["candidate_correct"] for r in positive),
                baseline_unsuitable_accepted=sum(r["baseline_accepted"] for r in negative),
                candidate_unsuitable_accepted=sum(r["candidate_accepted"] for r in negative),
                newly_unsuitable_accepted=sum(r["candidate_accepted"] and not r["baseline_accepted"] for r in negative),
                eligible_retries=sum(r["retried"] for r in positive),
                unsuitable_retries=sum(r["retried"] for r in negative),
                retry_complete=sum(r["retry_complete"] is True for r in rows),
                baseline_tokens=sum(r["baseline_tokens"] for r in rows),
                candidate_tokens=sum(r["candidate_tokens"] for r in rows),
                baseline_ms=sum(r["baseline_ms"] for r in rows),
                candidate_ms=sum(r["candidate_ms"] for r in rows),
                cases=rows,official_ai_rating=None,
                scope="Public developmental tool-planning cases. Conditional second thinking call; different mode, seed and cap by design. Exact values do not certify semantic interpretation. No synthesis or live routing.")

def run(binary,jar,seed):
    if not native.check_model(native.MODEL_PATH):
        raise RuntimeError("Unverified model")
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    command=[str(binary.resolve()),"-m",str(native.MODEL_PATH),"--host","127.0.0.1",
             "--port",str(native.PORT),"--ctx-size",str(native.CONTEXT),"--parallel","1",
             "--threads","4","--batch-size","256","--ubatch-size","256","--n-gpu-layers","0","--no-context-shift"]
    with tempfile.TemporaryFile() as log:
        process=subprocess.Popen(command,stdout=log,stderr=log)
        try:
            deadline=time.monotonic()+180
            while time.monotonic()<deadline:
                if process.poll() is not None:
                    raise RuntimeError("Native server exited")
                try:
                    if native.request(opener,"/health",timeout=3).get("status")=="ok":
                        break
                except (OSError,ValueError):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("Native server startup timed out")
            result=evaluate(lambda p:native.request(opener,"/completion",p),
                            lambda operation,q,proposal=None:calc.bridge(jar,operation,q,proposal),seed)
            if not native.check_model(native.MODEL_PATH):
                raise RuntimeError("Model identity changed")
            return result
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary",type=Path)
    parser.add_argument("bridge",type=Path)
    parser.add_argument("--seed",type=int,default=SEED)
    args=parser.parse_args()
    if not 0<=args.seed<2**31-1:
        parser.error("Invalid seed")
    print(json.dumps(run(args.binary,args.bridge,args.seed),indent=2,allow_nan=False))
