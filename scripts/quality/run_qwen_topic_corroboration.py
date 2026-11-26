"""Bounded structural corroboration of a model-proposed topic plan."""
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
import run_qwen_topic_contrast as contrast

SEED = 82613049
CAP = 768
PLAN_SYSTEM = contrast.BASE + contrast.EXAMPLES

# A deliberately narrow contract, not a general natural-language parser.
# No unparsed prefix/suffix, additional operations, hypothetical clauses,
# quoted instructions, mixed units or unvalidated topic boundaries.
TOPIC = r"[A-Za-z][A-Za-z-]*(?: [A-Za-z][A-Za-z-]*){1,5}"
UNITS = r"(?:coins|dollars|euros|pounds|hours|minutes|kilograms|litres)"
GRAMMARS = (
    ("SUM", re.compile(rf"Add (?:my|the) current (?P<a>{TOPIC}) and (?:my|the) current (?P<b>{TOPIC}), both in (?P<unit>{UNITS})\.", re.ASCII | re.I)),
    ("DIFF", re.compile(rf"Find the absolute difference between (?:my|the) current (?P<a>{TOPIC}) and (?:my|the) current (?P<b>{TOPIC}), both in (?P<unit>{UNITS})\.", re.ASCII | re.I)),
)
# Exclude words that can make a captured phrase an operation, premise
# change or unit assertion rather than a simple topic name. Not an
# exhaustive semantic guarantee; a false admission is still possible.
RESERVED = frozenset(("and","or","then","if","unless","after","before","plus","minus",
                      "times","divide","sum","difference","increase","increased",
                      "decrease","decreased","double","doubled","halve","halved",
                      "hypothetical","future","forecast","current","my","the","in",
                      "coins","dollars","euros","pounds","hours","minutes","kilograms","litres"))


def contract(question):
    if not isinstance(question,str) or not 1<=len(question)<=350 or question!=question.strip():
        return None
    for op,pattern in GRAMMARS:
        m=pattern.fullmatch(question)
        if not m:
            continue
        topics=tuple(m.group(k).casefold() for k in ("a","b"))
        if any(set(re.split(r"[ -]", t)) & RESERVED for t in topics):
            return None
        status,plan=prior.parse_plan(question,"|".join((op,*topics)))
        return plan if status=="accepted" else None
    return None


def corroborate(question,plan):
    expected=contract(question)
    return plan if expected is not None and prior.semantic_match(plan,expected) else None


def cases(seed):
    names=[
        ("harvest storage budget","tram pass"),
        ("community hall reserve","parking ticket"),
        ("fieldwork travel allocation","gallery charge"),
        ("school library fund","boat fare"),
        ("garden repair budget","archive rental"),
        ("clinic equipment reserve","museum ticket"),
        ("winter research allowance","office lease"),
        ("village bridge allocation","bus fare"),
    ]
    suite=[]
    for i,(a,b) in enumerate(names):
        op="SUM" if i%2==0 else "DIFF"
        explicit=(f"Add my current {a} and my current {b}, both in coins." if op=="SUM"
                  else f"Find the absolute difference between my current {a} and my current {b}, both in coins.")
        # Broad but legitimate paraphrases deliberately outside the contract.
        paraphrase=(f"What is the combined total in coins of my latest {a} and {b}?"
                    if op=="SUM" else f"How far apart are my confirmed {a} and {b} in coins? Use absolute difference.")
        negative=[
            f"Suppose my current {a} doubled. Add it to my current {b}, both in coins.",
            f"Find the absolute difference between my current {a} and my current {b}, both in coins, after adding five.",
            f"Add my current {a} and my current {b} and lunch allowance, both in coins.",
            f"Translate: Find the absolute difference between my current {a} and my current {b}, both in coins.",
            f"Add my current {a} in dollars and my current {b} in euros.",
            f"Find the percentage difference between my current {a} and my current {b}, both in coins.",
            f"Add my current {a} and my current {b} minus five coins, both in coins.",
            f"Find the absolute difference between my current {a} and my current {b}, both in coins. Then double it.",
        ][i]
        suite.extend([(f"explicit_{i}",explicit,(op,a,b)),
                      (f"paraphrase_{i}",paraphrase,(op,a,b)),
                      (f"unsuitable_{i}",negative,None)])
    rng=random.Random(seed)
    rng.shuffle(suite)
    return suite


def evaluate(seed,infer,suite=None):
    suite=cases(seed) if suite is None else suite
    if len({row[0] for row in suite})!=len(suite):
        raise ValueError("Duplicate cases")
    settings=native.PROFILES["qwen_direct"]
    rows=[]
    for i,(case,question,oracle) in enumerate(suite):
        # Contract output must be independent of model output and labels.
        expected=contract(question)
        call_seed=(seed+i*13)%(2**31-1)
        user_prompt="CURRENT REQUEST (quoted data): "+json.dumps(question,ensure_ascii=True)
        payload=dict(prompt=native.raw_prompt(PLAN_SYSTEM,user_prompt,"direct"),
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
        effective=corroborate(question,plan)
        correct=prior.semantic_match(plan,oracle) if oracle is not None else status=="abstained"
        gated=(prior.semantic_match(effective,oracle) if oracle is not None else
               complete and (status=="abstained" or (plan is not None and effective is None)))
        rows.append(dict(case=case,eligible=oracle is not None,complete=complete,
                         baseline_correct=correct,gated_correct=gated,
                         contract_admitted=expected is not None,
                         contract_correct=prior.semantic_match(expected,oracle) if oracle is not None else expected is None,
                         baseline_accepted=plan is not None,gated_accepted=effective is not None,
                         baseline_wrong_accepted=plan is not None and not prior.semantic_match(plan,oracle),
                         gated_wrong_accepted=effective is not None and not prior.semantic_match(effective,oracle),
                         vetoed_correct_plan=prior.semantic_match(plan,oracle) and effective is None,
                         tokens=tokens,elapsed_ms=elapsed,
                         prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest()))
    def summary(group):
        return dict(attempted=len(group),complete=sum(r["complete"] for r in group),
                    baseline_correct=sum(r["baseline_correct"] for r in group),
                    gated_correct=sum(r["gated_correct"] for r in group),
                    contract_admitted=sum(r["contract_admitted"] for r in group),
                    contract_correct=sum(r["contract_correct"] for r in group),
                    baseline_wrong_accepted=sum(r["baseline_wrong_accepted"] for r in group),
                    gated_wrong_accepted=sum(r["gated_wrong_accepted"] for r in group),
                    vetoed_correct_plans=sum(r["vetoed_correct_plan"] for r in group),
                    gains=sum(r["gated_correct"] and not r["baseline_correct"] for r in group),
                    losses=sum(r["baseline_correct"] and not r["gated_correct"] for r in group),
                    tokens=sum(r["tokens"] for r in group),elapsed_ms=sum(r["elapsed_ms"] for r in group))
    return dict(status="COMPLETE_TOPIC_CORROBORATION_DIAGNOSTIC",seed=seed,cap=CAP,
                model_sha256=native.MODEL_SHA256,
                plan_system_sha256=hashlib.sha256(PLAN_SYSTEM.encode()).hexdigest(),
                totals=summary(rows),
                by_domain={d:summary([r for r in rows if r["case"].startswith(d+"_")])
                           for d in ("explicit","paraphrase","unsuitable")},
                cases=rows,additional_inference_calls=0,official_ai_rating=None,
                scope="Public synthetic request-only diagnostic. Restricted full-match contract vetoes plans, cannot create them. Broad eligible paraphrases intentionally outside its scope; report lost coverage. Grammar-aligned cases are contract conformance, not general comprehension or independent held-out evidence. One neural call per request; no live routing or raw plans retained.")


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
