"""Paired arithmetic planning sufficiency diagnostic; no live activation."""
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

import run_qwen_profile_comparison as native
import run_qwen_calculator_probe as calc

SEED = 68219457
CAP = 128
CHECK = ("First identify the exact requested quantity and its units. "
         "Check that every required quantity is supplied or follows from the facts. "
         "A number that appears in the question is not necessarily relevant. "
         "If a required rate, count or relationship is unknown, return NONE. "
         "Never substitute an unrelated quantity for a missing one. ")


def cases(seed):
    rng = random.Random(seed)
    a, b, c = rng.sample(range(4, 15), 3)
    fmt = " Reply only with the number."
    # Paired minimal contrasts: change one semantic condition, not format.
    return [
        ("cost_known", f"A shop has {a} boxes and buys {b} notebooks at {c} coins each. What is the cost of the notebooks?" + fmt, str(b*c)),
        ("cost_missing", f"A shop has {a} boxes and buys {b} notebooks. The price per notebook is unknown. What is the cost of the notebooks?" + fmt, None),
        ("stock_known", f"There are {a} crates with {b} items per crate. Remove {c} loose items. How many items remain?" + fmt, str(a*b-c)),
        ("stock_missing", f"There are {a} crates and {b} shelves. Remove {c} loose items. The number of items per crate is not stated. How many items remain?" + fmt, None),
        ("rate_known", f"A vehicle travels at {a} kilometres per hour for {b} hours. Its driver is {c} years old in this fictional example. What is the distance in kilometres?" + fmt, str(a*b)),
        ("rate_missing", f"A vehicle travels for {b} hours. Its driver is {c} years old in this fictional example. Its speed is unknown. What is the distance in kilometres?" + fmt, None),
        ("profit_known", f"Revenue is {a*b+c} coins. Expenses are {a} coins and {b} coins. What is net profit?" + fmt, str(a*b+c-a-b)),
        ("profit_missing", f"Revenue is {a*b+c} coins from {a} customers. Expenses are not stated. What is net profit?" + fmt, None),
        ("unit_known", f"A total of {a*b+c} coins includes {c} coins delivery for {a} equal-priced items. What is the price per item?" + fmt, str(b)),
        ("unit_missing", f"A total of {a*b+c} coins includes delivery for {a} equal-priced items. Delivery cost is unknown. What is the price per item?" + fmt, None),
        ("share_known", f"Share {a*b} coins equally among {a} people. A separate envelope contains {c} coins. What is each person's share of the first amount?" + fmt, str(b)),
        ("share_missing", f"Share {a*b} coins equally among an unspecified number of people. A separate envelope contains {c} coins. What is each person's share of the first amount?" + fmt, None),
        ("control_negative", f"Start with {a} coins and spend {a+b} coins. Negative balances are allowed. What is the balance?" + fmt, str(-b)),
        ("control_exact", f"What is {a} times {b} plus {c}?" + fmt, str(a*b+c)),
        ("control_undefined", f"What is {a} divided by 0?" + fmt, None),
        ("control_comma", f"Add 1,25 and {b}. The comma convention is unspecified." + fmt, None),
    ]


def candidate_prompt(prompt):
    marker = "\nQUESTION: "
    if prompt.count(marker) != 1:
        raise ValueError("Production planning prompt boundary changed")
    head, question = prompt.split(marker, 1)
    return head + " " + CHECK.strip() + marker + question


def evaluate(infer, invoke, seed=SEED, suite=None):
    suite = cases(seed) if suite is None else suite
    if len({c[0] for c in suite}) != len(suite):
        raise ValueError("Duplicate case IDs")
    system = native.system_prompt()
    settings = native.PROFILES["qwen_direct"]
    rows = []
    for i, (case, question, expected) in enumerate(suite):
        baseline = invoke("plan", question)
        arms = ("baseline", "sufficiency") if i % 2 == 0 else ("sufficiency", "baseline")
        for arm in arms:
            prompt = baseline if arm == "baseline" else candidate_prompt(baseline)
            call_seed = (seed + i * 13) % (2**31 - 1)
            payload = dict(prompt=native.raw_prompt(system, prompt, "direct"), n_predict=CAP,
                           seed=call_seed, cache_prompt=False, stream=False, ignore_eos=False,
                           repeat_penalty=1.0, presence_penalty=0.0, frequency_penalty=0.0, **settings)
            start = time.monotonic()
            reply = infer(payload)
            elapsed = round((time.monotonic()-start)*1000)
            for key, value in dict(settings, seed=call_seed, n_predict=CAP).items():
                found = reply.get("generation_settings", {}).get(key)
                if type(found) not in (int, float) or not math.isclose(found, value, rel_tol=1e-6, abs_tol=1e-8):
                    raise RuntimeError("Unverified native settings")
            stop, truncated, tokens = reply.get("stop_type"), reply.get("truncated"), reply.get("tokens_predicted")
            if stop not in ("eos", "word", "limit") or type(truncated) is not bool:
                raise RuntimeError("Unverified stop state")
            if type(tokens) is not int or not 0 <= tokens <= CAP+8:
                raise RuntimeError("Unverified token count")
            complete = stop in ("eos", "word") and not truncated
            proposal = native.visible_answer(reply.get("content"))
            parsed = invoke("evaluate", question, proposal) if complete else None
            eligible = expected is not None
            correct = bool(eligible and parsed is not None and calc.scalar_equal(parsed[0], expected))
            rows.append(dict(case=case, arm=arm, eligible=eligible, correct=correct, accepted=parsed is not None,
                             explicit_none=complete and proposal == "NONE", complete=complete, tokens=tokens,
                             elapsed_ms=elapsed, prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest()))
    totals = {}
    for arm in ("baseline", "sufficiency"):
        selected = [r for r in rows if r["arm"] == arm]
        totals[arm] = dict(eligible=sum(r["eligible"] for r in selected),
                           unsuitable=sum(not r["eligible"] for r in selected),
                           correct=sum(r["correct"] for r in selected),
                           wrong_eligible_accepted=sum(r["eligible"] and r["accepted"] and not r["correct"] for r in selected),
                           unsuitable_accepted=sum(not r["eligible"] and r["accepted"] for r in selected),
                           explicit_none=sum(r["explicit_none"] for r in selected),
                           complete=sum(r["complete"] for r in selected),
                           tokens=sum(r["tokens"] for r in selected), elapsed_ms=sum(r["elapsed_ms"] for r in selected))
    return dict(status="COMPLETE_SEMANTIC_PLANNING_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256, system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                totals=totals, cases=rows, official_ai_rating=None,
                scope="Public synthetic single-call planning comparison. No answer keys in prompts; no production activation, synthesis or independent rating.")


def run(binary, jar, seed):
    if not native.check_model(native.MODEL_PATH):
        raise RuntimeError("Unverified model")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    command = [str(binary.resolve()), "-m", str(native.MODEL_PATH), "--host", "127.0.0.1",
               "--port", str(native.PORT), "--ctx-size", str(native.CONTEXT), "--parallel", "1",
               "--threads", "4", "--batch-size", "256", "--ubatch-size", "256",
               "--n-gpu-layers", "0", "--no-context-shift"]
    with tempfile.TemporaryFile() as log:
        proc = subprocess.Popen(command, stdout=log, stderr=log)
        try:
            deadline = time.monotonic()+180
            while time.monotonic() < deadline:
                if proc.poll() is not None:
                    raise RuntimeError("Native server exited")
                try:
                    if native.request(opener, "/health", timeout=3).get("status") == "ok":
                        break
                except (OSError, ValueError):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("Native startup timed out")
            report = evaluate(lambda p: native.request(opener, "/completion", p),
                              lambda op, q, proposal=None: calc.bridge(jar, op, q, proposal), seed)
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


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("bridge", type=Path)
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()
    if not 0 <= args.seed < 2**31-1:
        parser.error("Invalid seed")
    print(json.dumps(run(args.binary, args.bridge, args.seed), indent=2, allow_nan=False))
