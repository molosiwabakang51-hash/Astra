"""Matched contrast examples for model-led topic planning."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import re
import subprocess
import tempfile
import time
import urllib.request

import run_qwen_profile_comparison as native
import run_qwen_topic_plan as prior

SEED = 72163984
CAP = 768
BASE = prior.PLAN_SYSTEM
EXAMPLES = (
    "\nExamples of correct plans (examples are not current requests):\n"
    "Request: Add my current greenhouse repair budget and ferry fare in coins.\n"
    "Plan: SUM|greenhouse repair budget|ferry fare\n"
    "Request: How far apart are my current kitchen renovation cost and train fare, both in coins? Give the absolute difference.\n"
    "Plan: DIFF|kitchen renovation cost|train fare\n"
    "Request: What is the ratio of my current greenhouse repair budget to ferry fare?\n"
    "Plan: NONE\n"
    "Request: If I doubled the kitchen renovation cost, what would the total with train fare be?\n"
    "Plan: NONE\n"
    "Do not copy example topics. Read the current request, and output only the plan."
)


def cases(seed):
    topics = [
        ("harbour maintenance fund", "tram fare"),
        ("forest restoration reserve", "theatre ticket"),
        ("hospital equipment budget", "warehouse rent"),
        ("college research allocation", "parking fee"),
        ("bridge repair fund", "cinema admission"),
        ("village water reserve", "office lease"),
        ("garden renovation budget", "bus fare"),
        ("museum equipment allocation", "locker rental"),
    ]
    templates = [
        ("SUM", "How many coins altogether are my current {a} and {b}?"),
        ("DIFF", "Give the absolute difference in coins between my latest {a} and {b}."),
        ("SUM", "Combine my last confirmed {a} with my current {b}, in coins."),
        ("DIFF", "How many coins separate my current {a} from my current {b}? Use absolute difference."),
        ("SUM", "What is the total in coins if you add my remembered {a} to my {b}?"),
        ("DIFF", "Find the nonnegative difference between my latest {a} and {b}, both in coins."),
        ("SUM", "Use the current {a} and {b}, both measured in coins, to calculate the sum."),
        ("DIFF", "Calculate the absolute difference in coins for the current {a} and {b} we discussed."),
    ]
    negatives = [
        "Explain this quotation: 'Add my current {a} and {b} in coins.'",
        "Multiply my current {a} by my current {b}.",
        "Only tell me the current {a}; do not include {b}.",
        "Suppose the {a} rose by ten coins. Add the new amount to the {b}.",
        "Add my {a} measured in coins to my {b} measured in hours.",
        "List the current {a} and {b} separately without computing anything.",
        "Add my current {a}, {b}, and cleaning fee in coins.",
        "What is the percentage difference between my current {a} and {b}?",
    ]
    positives, unsuitable = [], []
    for i, ((a,b),(op,pattern),negative) in enumerate(zip(topics,templates,negatives)):
        positives.append((f"eligible_{i}",pattern.format(a=a,b=b),(op,a,b)))
        unsuitable.append((f"unsuitable_{i}",negative.format(a=a,b=b),None))
    rng=random.Random(seed)
    rng.shuffle(positives)
    rng.shuffle(unsuitable)
    suite=[]
    for i,pair in enumerate(zip(positives,unsuitable)):
        suite.extend(pair if i%2==0 else reversed(pair))
    return suite


def components(plan,oracle):
    if plan is None or oracle is None:
        return dict(operation_correct=False,topics_correct=False,exact_topics=0)
    matches=len(set(plan[1:]) & set(oracle[1:]))
    return dict(operation_correct=plan[0]==oracle[0],topics_correct=matches==2,exact_topics=matches)


def evaluate(seed,infer,suite=None):
    suite=cases(seed) if suite is None else suite
    if len({row[0] for row in suite})!=len(suite):
        raise ValueError("Duplicate cases")
    systems={"baseline":BASE,"contrast":BASE+EXAMPLES}
    settings=native.PROFILES["qwen_direct"]
    rows=[]
    for i,(case,question,oracle) in enumerate(suite):
        for arm in (("baseline","contrast") if i%2==0 else ("contrast","baseline")):
            call_seed=(seed+i*13)%(2**31-1)
            user_prompt="CURRENT REQUEST (quoted data): "+json.dumps(question,ensure_ascii=True)
            payload=dict(prompt=native.raw_prompt(systems[arm],user_prompt,"direct"),
                         n_predict=CAP,seed=call_seed,cache_prompt=False,stream=False,
                         ignore_eos=False,repeat_penalty=1.0,presence_penalty=0.0,
                         frequency_penalty=0.0,**settings)
            started=time.monotonic()
            reply=infer(payload)
            elapsed=round((time.monotonic()-started)*1000)
            for key,value in dict(settings,seed=call_seed,n_predict=CAP).items():
                found=reply.get("generation_settings",{}).get(key)
                if type(found) not in (int,float) or not math.isclose(found,value,rel_tol=1e-6,abs_tol=1e-8):
                    raise RuntimeError("Unverified native settings")
            stop,truncated,tokens=reply.get("stop_type"),reply.get("truncated"),reply.get("tokens_predicted")
            if stop not in ("eos","word","limit") or type(truncated) is not bool:
                raise RuntimeError("Unverified native completion")
            if type(tokens) is not int or not 0<=tokens<=CAP+8:
                raise RuntimeError("Unverified token count")
            complete=stop in ("eos","word") and not truncated
            status,plan=prior.parse_plan(question,native.visible_answer(reply.get("content"))) if complete else ("incomplete",None)
            correct=prior.semantic_match(plan,oracle) if oracle is not None else status=="abstained"
            rows.append(dict(case=case,arm=arm,eligible=oracle is not None,correct=correct,
                             complete=complete,status=status,unsafe_accept=oracle is None and plan is not None,
                             wrong_eligible_plan=oracle is not None and plan is not None and not correct,
                             tokens=tokens,elapsed_ms=elapsed,**components(plan,oracle),
                             prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest()))
    def summary(group):
        return {arm:dict(attempted=len(rr),correct=sum(r["correct"] for r in rr),
                         complete=sum(r["complete"] for r in rr),
                         accepted=sum(r["status"]=="accepted" for r in rr),
                         explicit_abstention=sum(r["status"]=="abstained" for r in rr),
                         unsafe_accept=sum(r["unsafe_accept"] for r in rr),
                         wrong_eligible_plan=sum(r["wrong_eligible_plan"] for r in rr),
                         operation_correct=sum(r["operation_correct"] for r in rr),
                         topics_correct=sum(r["topics_correct"] for r in rr),
                         exact_topics=sum(r["exact_topics"] for r in rr),
                         expected_topics=2*sum(r["eligible"] for r in rr),
                         tokens=sum(r["tokens"] for r in rr),elapsed_ms=sum(r["elapsed_ms"] for r in rr))
                for arm in systems for rr in [[r for r in group if r["arm"]==arm]]}
    matched=dict(both_correct=0,baseline_only=0,candidate_only=0,neither=0)
    for case,_,_ in suite:
        pair={r["arm"]:r["correct"] for r in rows if r["case"]==case}
        a,b=pair["baseline"],pair["contrast"]
        matched["both_correct" if a and b else "baseline_only" if a else "candidate_only" if b else "neither"]+=1
    return dict(status="COMPLETE_TOPIC_CONTRAST_DIAGNOSTIC",seed=seed,cap=CAP,
                model_sha256=native.MODEL_SHA256,
                system_sha256={k:hashlib.sha256(v.encode()).hexdigest() for k,v in systems.items()},
                totals=summary(rows),eligible=summary([r for r in rows if r["eligible"]]),
                unsuitable=summary([r for r in rows if not r["eligible"]]),matched=matched,cases=rows,
                official_ai_rating=None,
                scope="Public synthetic fresh request templates, same direct profile, seed, cap, literal-span parser. Candidate adds four contrast examples only; component scores count accepted plans, not semantic proof. No history or computation executed, no raw plans retained, no live activation or independent rating.")


def run(binary, seed):
    if not native.check_model(native.MODEL_PATH):
        raise RuntimeError("Unverified model")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    command = [str(binary.resolve()), "-m", str(native.MODEL_PATH), "--host", "127.0.0.1",
               "--port", str(native.PORT), "--ctx-size", str(native.CONTEXT), "--parallel", "1",
               "--threads", "4", "--batch-size", "256", "--ubatch-size", "256",
               "--n-gpu-layers", "0", "--no-context-shift"]
    with tempfile.TemporaryFile() as log:
        process = subprocess.Popen(command, stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Native server exited")
                try:
                    if native.request(opener, "/health", timeout=3).get("status") == "ok":
                        break
                except (OSError, ValueError):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("Native server startup timed out")
            report = evaluate(seed, lambda payload: native.request(opener, "/completion", payload))
            if not native.check_model(native.MODEL_PATH):
                raise RuntimeError("Model identity changed")
            return report
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()
    if not 0 <= args.seed < 2**31 - 1:
        parser.error("Invalid seed")
    print(json.dumps(run(args.binary, args.seed), indent=2, allow_nan=False))
