"""Paired Qwen3 program-state diagnostic; public synthetic data, not held-out."""
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

SEED = 97261531
CAP = 1792
HINT = ("\nIn the thinking section, maintain the current value of every variable, "
        "apply assignments in their stated order, and recheck the requested final state. "
        "Keep any intermediate states out of the final answer.")


def cases(seed):
    rng = random.Random(seed)
    a, b = rng.sample(range(4, 12), 2)
    n = rng.randint(3, 5)
    fmt = " Reply with only the integer."
    x, y = a, b
    for _ in range(n):
        x += y
        y = x - y
    sequential = x + y
    x, y = a, b
    for _ in range(n):
        x, y = x + y, x - y
    simultaneous = x + y
    x = a
    for _ in range(n):
        x = x // 2 if x % 2 == 0 else x * 3 + 1
    conditional = x
    x = a
    for i in range(1, n + 1):
        for j in range(1, i + 1):
            x += j
    nested = x
    x, count = a, 0
    while x < a + b:
        x += 3
        count += 1
    return [
        ("trace_sequential", f"Start x={a}, y={b}. Repeat {n} times: set x to x+y, then set y to the NEW x minus the old y. What is x+y afterward?" + fmt, str(sequential)),
        ("trace_simultaneous", f"Start x={a}, y={b}. Repeat {n} times: simultaneously set x to old x+old y and y to old x-old y. What is x+y afterward?" + fmt, str(simultaneous)),
        ("trace_conditional", f"Start x={a}. Repeat {n} times: if x is even, halve it; otherwise triple it and add 1. What is x afterward?" + fmt, str(conditional)),
        ("trace_nested", f"Start x={a}. For i=1 through {n} inclusive, for j=1 through i inclusive, add j to x. What is x afterward?" + fmt, str(nested)),
        ("trace_stop", f"Start x={a} and count=0. While x is less than {a+b}, add 3 to x and increment count by 1. What is count at termination?" + fmt, str(count)),
        ("trace_swap", f"Start x={a}, y={b}. Swap x and y exactly {2*n+1} times. Then subtract y from x. What is x afterward?" + fmt, str(b-a)),
        ("trace_overwrite", f"Start x={a}, y={b}. Set x to y. Then set y to x+{n}. Finally set x to x+y. What is x afterward?" + fmt, str(2*b+n)),
        ("trace_queue", f"A queue initially contains integers {a}, {b} in that order. Append {a+b}, remove the first element, append {n}, then remove the first element. What is now the first element?" + fmt, str(a+b)),
        ("control_arithmetic", f"What is {a} plus {b}?" + fmt, str(a+b)),
        ("control_missing", f"A variable is initialized to an unspecified integer and then increased by {n}. Can its final value be determined uniquely? Reply YES or NO only.", "NO"),
        ("control_logic", "All sealed boxes are labelled. This box is sealed. Must it be labelled? Reply YES or NO only.", "YES"),
        ("control_copy", f"A label displays {b}. What integer does the label display?" + fmt, str(b)),
    ]


def evaluate(seed, infer, suite=None):
    suite = cases(seed) if suite is None else suite
    if len({row[0] for row in suite}) != len(suite):
        raise ValueError("Duplicate case IDs")
    system = native.system_prompt()
    settings = native.PROFILES["qwen_think"]
    rows = []
    for i, (case, question, expected) in enumerate(suite):
        arms = ("baseline", "trace") if i % 2 == 0 else ("trace", "baseline")
        for arm in arms:
            # Oracle grouping isolates the hypothesis; NOT a production router.
            q = question + (HINT if arm == "trace" and case.startswith("trace_") else "")
            call_seed = (seed + i * 13) % (2**31 - 1)
            payload = dict(prompt=native.raw_prompt(system, q, "think"), n_predict=CAP,
                           seed=call_seed, cache_prompt=False, stream=False, ignore_eos=False,
                           repeat_penalty=1.0, presence_penalty=0.0, frequency_penalty=0.0, **settings)
            started = time.monotonic()
            reply = infer(payload)
            elapsed = round((time.monotonic() - started) * 1000)
            for key, value in dict(settings, seed=call_seed, n_predict=CAP).items():
                found = reply.get("generation_settings", {}).get(key)
                if type(found) not in (int, float) or not math.isclose(found, value, rel_tol=1e-6, abs_tol=1e-8):
                    raise RuntimeError("Unverified native settings")
            stop, truncated, tokens = reply.get("stop_type"), reply.get("truncated"), reply.get("tokens_predicted")
            if stop not in ("eos", "word", "limit") or type(truncated) is not bool:
                raise RuntimeError("Unverified native completion")
            if type(tokens) is not int or not 0 <= tokens <= CAP + 8:
                raise RuntimeError("Unverified native token count")
            complete = stop in ("eos", "word") and not truncated
            answer = native.visible_answer(reply.get("content"))
            correct = complete and answer.casefold() == expected.casefold()
            rows.append(dict(case=case, arm=arm, correct=correct, complete=complete, tokens=tokens,
                             elapsed_ms=elapsed, prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest()))
    def summary(group):
        return {arm: dict(attempted=sum(r["arm"] == arm for r in group),
                          correct=sum(r["correct"] for r in group if r["arm"] == arm),
                          complete=sum(r["complete"] for r in group if r["arm"] == arm),
                          tokens=sum(r["tokens"] for r in group if r["arm"] == arm),
                          elapsed_ms=sum(r["elapsed_ms"] for r in group if r["arm"] == arm))
                for arm in ("baseline", "trace")}
    matched = dict(both_correct=0, baseline_only=0, candidate_only=0, neither=0)
    for case, _, _ in suite:
        a = {r["arm"]: r["correct"] for r in rows if r["case"] == case}
        matched["both_correct" if a["baseline"] and a["trace"] else "baseline_only" if a["baseline"]
                else "candidate_only" if a["trace"] else "neither"] += 1
    return dict(status="COMPLETE_PROGRAM_STATE_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256, system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                hint_sha256=hashlib.sha256(HINT.encode()).hexdigest(), totals=summary(rows),
                by_domain={d: summary([r for r in rows if r["case"].startswith(d + "_")]) for d in ("trace", "control")},
                matched=matched, cases=rows, official_ai_rating=None,
                scope="Public synthetic developmental tasks; oracle eligibility only. Same seed, model and cap; no live routing or saved reasoning traces.")


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
