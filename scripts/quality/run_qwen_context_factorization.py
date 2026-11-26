"""Contextual fact extraction versus assisted composition diagnostic."""
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

SEED = 63821749
CAP = 768
RECOVERY_COMMIT = "db99d6a8acd78fdbacebe6f6fe7994842ed68448"
BOUNDARY = "\nCURRENT USER MESSAGE (answer this message, not the excerpts):\n"


def cases(seed):
    rng = random.Random(seed)
    values = rng.sample(range(101, 999), 24)
    topics = [("station renovation budget", "taxi fare"),
              ("office equipment allocation", "museum fee"),
              ("spring research reserve", "studio rent"),
              ("park restoration fund", "storage cost"),
              ("school laboratory budget", "ferry fare"),
              ("library extension allocation", "garden rent")]
    suite = []
    for i, (first, second) in enumerate(topics):
        a, b, c, d = values[i*4:i*4+4]
        f1, f2 = f"Actually, make it {b} coins.", f"No, make it {d} coins."
        history = [("user", f"The fictional {first} is {a} coins."),
                   ("assistant", "Noted."), ("user", f1),
                   ("user", f"The fictional {second} is {c} coins."),
                   ("assistant", "Noted."), ("user", f2),
                   ("user", "Dinner needs vegetables."),
                   ("user", "Actually, use spicy rice.")] + [
                       ("assistant", "Unrelated recent weather note. " + "Rain and clouds were discussed. "*7)
                       for _ in range(12)]
        op = "sum" if i % 2 == 0 else "difference"
        relation = "combined total" if op == "sum" else "absolute difference"
        q = f"Use the current {first} and {second}. What is their {relation} in coins? Reply with only the integer."
        subs = tuple(f"What is the current {topic} in coins? Reply with only the integer."
                     for topic in (first, second))
        suite.append(dict(case=f"dual_{i}", question=q, expected=combine(op, (b,d)),
                          history=history, required=(f1,f2), subquestions=subs,
                          operands=(b,d), operation=op))
        if i < 4:
            q = f"Use the current {first}. How many coins are allocated? Reply with only the integer."
            suite.append(dict(case=f"single_{i}", question=q, expected=b, history=history,
                              required=(f1,), subquestions=(q,), operands=(b,), operation="identity"))
    # Two control pairs reversed, and remaining dual order alternates by index.
    return suite[:2] + list(reversed(suite[2:4])) + suite[4:6] + list(reversed(suite[6:8])) + suite[8:]


def combine(operation, values):
    if operation == "identity" and len(values) == 1:
        return values[0]
    if operation == "sum" and len(values) == 2:
        return values[0] + values[1]
    if operation == "difference" and len(values) == 2:
        return abs(values[0] - values[1])
    raise ValueError("Unsupported operation or arity")


def scalar(text):
    # Strict bounded contract, no extracting numbers from prose or reasoning.
    import re
    if not isinstance(text, str) or not re.fullmatch(r"[+-]?[0-9]{1,9}", text.strip()):
        return None
    return int(text.strip())


def prepare(build, item):
    q = item["question"]
    prompt = build(q, item["history"])
    if not prompt.endswith(BOUNDARY + q) or len(prompt) > 3000:
        raise RuntimeError("Current question changed or context budget exceeded")
    if "Actually, use spicy rice." in prompt or not all(f in prompt for f in item["required"]):
        raise RuntimeError("Recovery evidence failed preflight")
    if "[Some linked corrections are omitted" in prompt:
        raise RuntimeError("This complete-evidence experiment cannot use incomplete evidence")
    head, _ = prompt.rsplit(BOUNDARY, 1)
    prompts = [head + BOUNDARY + sub for sub in item["subquestions"]]
    if any(len(p) > 3000 for p in prompts):
        raise RuntimeError("Subquestion exceeds production bound")
    if item["operation"] == "identity" and prompts != [prompt]:
        raise RuntimeError("Single-topic control changed")
    return prompt, prompts


def evaluate(seed, infer, build, suite=None):
    suite = cases(seed) if suite is None else suite
    if len({item["case"] for item in suite}) != len(suite):
        raise ValueError("Duplicate case IDs")
    prepared = {item["case"]: prepare(build, item) for item in suite}
    system = native.system_prompt()
    settings = native.PROFILES["qwen_think"]
    rows = []
    def call(prompt, call_seed):
        payload = dict(prompt=native.raw_prompt(system, prompt, "think"), n_predict=CAP,
                       seed=call_seed, cache_prompt=False, stream=False, ignore_eos=False,
                       repeat_penalty=1.0, presence_penalty=0.0, frequency_penalty=0.0, **settings)
        started = time.monotonic()
        reply = infer(payload)
        elapsed = round((time.monotonic() - started) * 1000)
        for key, value in dict(settings, seed=call_seed, n_predict=CAP).items():
            found = reply.get("generation_settings", {}).get(key)
            if type(found) not in (int,float) or not math.isclose(found, value, rel_tol=1e-6, abs_tol=1e-8):
                raise RuntimeError("Unverified native settings")
        stop, truncated, tokens = reply.get("stop_type"), reply.get("truncated"), reply.get("tokens_predicted")
        if stop not in ("eos","word","limit") or type(truncated) is not bool:
            raise RuntimeError("Unverified native completion")
        if type(tokens) is not int or not 0 <= tokens <= CAP + 8:
            raise RuntimeError("Unverified native token count")
        complete = stop in ("eos","word") and not truncated
        value = scalar(native.visible_answer(reply.get("content"))) if complete else None
        return value, dict(complete=complete, tokens=tokens, elapsed_ms=elapsed,
                           canonical=value is not None,
                           prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest())
    for i, item in enumerate(suite):
        case = item["case"]
        baseline, subprompts = prepared[case]
        seed0 = (seed + i*13) % (2**31-1)
        arms = ("baseline","factorized") if i%2 == 0 else ("factorized","baseline")
        for arm in arms:
            if arm == "baseline":
                value, record = call(baseline, seed0)
                records = [record]
                result = value
                operand_correct = None
                all_operands_correct = None
            else:
                values, records = [], []
                for j, prompt in enumerate(subprompts):
                    value, record = call(prompt, (seed0+j) % (2**31-1))
                    values.append(value)
                    records.append(record)
                operand_correct = sum(v == e for v,e in zip(values,item["operands"]))
                all_operands_correct = operand_correct == len(item["operands"])
                result = combine(item["operation"], values) if all(v is not None for v in values) else None
            rows.append(dict(case=case, arm=arm, correct=result == item["expected"],
                             complete=all(r["complete"] for r in records),
                             all_operands_correct=all_operands_correct, operand_correct=operand_correct,
                             operand_count=len(item["operands"]) if arm=="factorized" else None,
                             calls=records, tokens=sum(r["tokens"] for r in records),
                             elapsed_ms=sum(r["elapsed_ms"] for r in records)))
    def summary(group):
        return {arm: dict(attempted=sum(r["arm"]==arm for r in group),
                          correct=sum(r["correct"] for r in group if r["arm"]==arm),
                          complete=sum(r["complete"] for r in group if r["arm"]==arm),
                          calls=sum(len(r["calls"]) for r in group if r["arm"]==arm),
                          tokens=sum(r["tokens"] for r in group if r["arm"]==arm),
                          elapsed_ms=sum(r["elapsed_ms"] for r in group if r["arm"]==arm))
                for arm in ("baseline","factorized")}
    matched = dict(both_correct=0, baseline_only=0, candidate_only=0, neither=0)
    for item in suite:
        scores = {r["arm"]: r["correct"] for r in rows if r["case"]==item["case"]}
        a,b = scores["baseline"],scores["factorized"]
        matched["both_correct" if a and b else "baseline_only" if a else "candidate_only" if b else "neither"] += 1
    dual = [r for r in rows if r["arm"]=="factorized" and r["case"].startswith("dual_")]
    return dict(status="COMPLETE_CONTEXT_FACTORIZATION_DIAGNOSTIC",seed=seed,cap=CAP,
                model_sha256=native.MODEL_SHA256, recovery_commit=RECOVERY_COMMIT,
                system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                totals=summary(rows), matched=matched,
                by_domain={d:summary([r for r in rows if r["case"].startswith(d+"_")]) for d in ("dual","single")},
                extraction=dict(correct_operands=sum(r["operand_correct"] for r in dual),
                                attempted_operands=sum(r["operand_count"] for r in dual),
                                both_correct=sum(r["all_operands_correct"] for r in dual),
                                attempted_pairs=len(dual),
                                correct_result_with_wrong_operands=sum(r["correct"] and not r["all_operands_correct"] for r in dual)),
                cases=rows, official_ai_rating=None,
                scope="Synthetic complete recovered evidence; oracle operation and subquestion decomposition. Factorized track uses deterministic computation and additional calls, not unassisted neural reasoning. No raw replies retained.")


def run(binary, seed, build):
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
            report = evaluate(seed, lambda payload: native.request(opener, "/completion", payload), build)
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
    parser.add_argument("--candidate-context-jar", type=Path, required=True)
    args = parser.parse_args()
    if not 0 <= args.seed < 2**31 - 1:
        parser.error("Invalid seed")
    build = lambda q, h: native.recovery_prompt(args.candidate_context_jar, q, h)
    print(json.dumps(run(args.binary, args.seed, build), indent=2, allow_nan=False))
