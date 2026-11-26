"""Paired compiled Kotlin recovery diagnostic; public synthetic histories, not held-out."""
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

SEED = 27844193
CAP = 768
BASELINE_COMMIT = "b81b5eb5157d5de0ae60536638bd702f91e2d8a6"
CANDIDATE_COMMIT = "a6a6b8d0dfa147abdb27db2ce629dd415edead96"


def cases(seed):
    rng = random.Random(seed)
    values = rng.sample(range(101, 999), 16)
    topics = [("museum renovation budget", "ferry fare"),
              ("school equipment allocation", "garden cost"),
              ("winter research reserve", "archive rent"),
              ("library restoration fund", "workshop fee")]
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
                       ("assistant", "Unrelated recent weather note. " + "X"*210)
                       for _ in range(12)]
        q = f"Use the current {first} and {second}. What is their combined total in coins? Reply with only the integer."
        suite.append((f"dual_{i}", q, str(b+d), history, (f1, f2)))
        q = f"Use the current {first}. How many coins are allocated? Reply with only the integer."
        suite.append((f"single_{i}", q, str(b), history, (f1,)))
    # Alternate arm order within both domains.
    return suite[:2] + list(reversed(suite[2:4])) + suite[4:6] + list(reversed(suite[6:8]))


def prepare(seed, builders, suite):
    if set(builders) != {"baseline", "recovery"}:
        raise ValueError("Both compiled Kotlin recovery builders are required")
    prepared = {}
    for case, question, expected, history, required in suite:
        prompts = {arm: build(question, history) for arm, build in builders.items()}
        for arm, prompt in prompts.items():
            if not prompt.endswith(question) or len(prompt) > 3000:
                raise RuntimeError("Current question changed or context budget exceeded")
            if "Actually, use spicy rice." in prompt:
                raise RuntimeError("Unrelated correction admitted")
            if arm == "recovery" and not all(fragment in prompt for fragment in required):
                raise RuntimeError("Candidate failed required correction retention")
        if case.startswith("dual_") and all(fragment in prompts["baseline"] for fragment in required):
            raise RuntimeError("Dual fixture fails to distinguish baseline")
        if case.startswith("single_") and prompts["baseline"] != prompts["recovery"]:
            raise RuntimeError("Single-topic control changed")
        prepared[case] = prompts
    return prepared


def evaluate(seed, infer, builders, suite=None):
    suite = cases(seed) if suite is None else suite
    if len({row[0] for row in suite}) != len(suite):
        raise ValueError("Duplicate case IDs")
    prepared = prepare(seed, builders, suite)
    system = native.system_prompt()
    settings = native.PROFILES["qwen_think"]
    rows = []
    for i, (case, question, expected, history, required) in enumerate(suite):
        arms = ("baseline", "recovery") if i % 2 == 0 else ("recovery", "baseline")
        for arm in arms:
            q = prepared[case][arm]
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
                             elapsed_ms=elapsed, required_retained=sum(fragment in q for fragment in required),
                             required_count=len(required), tail_notice="including a later correction" in q,
                             prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest()))
    def summary(group):
        return {arm: dict(attempted=sum(r["arm"] == arm for r in group),
                          correct=sum(r["correct"] for r in group if r["arm"] == arm),
                          complete=sum(r["complete"] for r in group if r["arm"] == arm),
                          tokens=sum(r["tokens"] for r in group if r["arm"] == arm),
                          elapsed_ms=sum(r["elapsed_ms"] for r in group if r["arm"] == arm))
                for arm in ("baseline", "recovery")}
    matched = dict(both_correct=0, baseline_only=0, candidate_only=0, neither=0)
    for case, _, _, _, _ in suite:
        a = {r["arm"]: r["correct"] for r in rows if r["case"] == case}
        matched["both_correct" if a["baseline"] and a["recovery"] else "baseline_only" if a["baseline"]
                else "candidate_only" if a["recovery"] else "neither"] += 1
    return dict(status="COMPLETE_TWO_TOPIC_RECOVERY_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256, system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                baseline_commit=BASELINE_COMMIT, candidate_commit=CANDIDATE_COMMIT, totals=summary(rows),
                by_domain={d: summary([r for r in rows if r["case"].startswith(d + "_")]) for d in ("dual", "single")},
                matched=matched, cases=rows, official_ai_rating=None,
                scope="Public synthetic restored histories through exact compiled Kotlin builders, same model, seed and cap. Not live Android JNI or independent held-out assessment. No saved reasoning traces.")


def run(binary, seed, builders):
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
            report = evaluate(seed, lambda payload: native.request(opener, "/completion", payload), builders)
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
    parser.add_argument("--baseline-context-jar", type=Path, required=True)
    parser.add_argument("--candidate-context-jar", type=Path, required=True)
    args = parser.parse_args()
    if not 0 <= args.seed < 2**31 - 1:
        parser.error("Invalid seed")
    builders = {"baseline": lambda q, h: native.recovery_prompt(args.baseline_context_jar, q, h),
                "recovery": lambda q, h: native.recovery_prompt(args.candidate_context_jar, q, h)}
    print(json.dumps(run(args.binary, args.seed, builders), indent=2, allow_nan=False))
