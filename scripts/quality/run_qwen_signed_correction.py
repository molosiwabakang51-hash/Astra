"""Isolate signed numerical owner correction recovery using pinned Kotlin builders."""
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

SEED = 18573926
CAP = 768
BASELINE_COMMIT = "e2459ff9c449cf42b7a299963eb22e3afcac51af"
CANDIDATE_COMMIT = "fd022de93f41dbaa5c6e141d9431e76808e582c7"
SUFFIX = " Reply with only the integer."
BOUNDARY = "\nCURRENT USER MESSAGE (answer this message, not the excerpts):\n"


def cases(seed):
    rng=random.Random(seed)
    values=rng.sample(range(101,999),8)
    topics=("coastal trial balance","winter stock adjustment",
            "harbour account balance","research reserve adjustment")
    suite=[]
    for i,topic in enumerate(topics):
        initial,magnitude=values[2*i:2*i+2]
        sign=("-", "+", "−", "-")[i]
        value=magnitude if sign=="+" else -magnitude
        anchor=f"My fictional {topic} is {initial} coins."
        signed=f"No, {sign}{magnitude}."
        unsigned=f"No, {magnitude}."
        delta=7+i*3
        relative=f"Actually, increase it by {delta} coins."
        tail=[("assistant",f"Unrelated weather note {j}. "+"X"*210) for j in range(15)]
        prefix=[("user",anchor),("assistant","Noted.")]
        q=f"What was my {topic}?"+SUFFIX
        suite.extend([
            (f"signed_{i}",q,str(value),prefix+[("user",signed)]+tail,(anchor,signed)),
            (f"relative_{i}",q,str(value+delta),prefix+[("user",signed),("user",relative)]+tail,
             (anchor,signed,relative)),
            (f"control_{i}",q,str(magnitude),prefix+[("user",unsigned)]+tail,(anchor,unsigned)),
        ])
    return suite


def prepare(seed,builders,suite):
    if set(builders)!={"baseline","recovery"}:
        raise ValueError("Both pinned compiled Kotlin builders required")
    prepared={}
    for case,question,expected,history,required in suite:
        prompts={arm:build(question,history) for arm,build in builders.items()}
        for arm,prompt in prompts.items():
            if not prompt.endswith(BOUNDARY+question) or len(prompt)>3000:
                raise RuntimeError("Current question changed or context bound exceeded")
            head=prompt.rsplit(BOUNDARY,1)[0]
            if required[0] not in head:
                raise RuntimeError("Original premise absent")
            if "[Some linked corrections are omitted" in head:
                raise RuntimeError("Fixture requires complete excerpts")
            if arm=="recovery" and not all(fragment in head for fragment in required):
                raise RuntimeError("Candidate lost required signed evidence")
        if case.startswith("control_"):
            if prompts["baseline"]!=prompts["recovery"]:
                raise RuntimeError("Unsigned control prompt changed")
        else:
            if required[1] in prompts["baseline"].rsplit(BOUNDARY,1)[0]:
                raise RuntimeError("Signed fixture does not distinguish original defect")
        prepared[case]=prompts
    return prepared


def integer(text):
    # Permit mathematical Unicode minus, but no prose, units, or extraction
    # of numbers from a longer or incomplete answer.
    if not isinstance(text,str) or not re.fullmatch(r"[+−-]?[0-9]{1,9}",text.strip()):
        return None
    return int(text.strip().replace("−","-"))


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
            correct = complete and integer(answer) == int(expected)
            rows.append(dict(case=case, arm=arm, correct=correct, complete=complete, tokens=tokens,
                             elapsed_ms=elapsed, required_retained=sum(fragment in q.rsplit(BOUNDARY,1)[0] for fragment in required),
                             correction_retained=required[1] in q.rsplit(BOUNDARY,1)[0],
                             all_corrections_retained=all(f in q.rsplit(BOUNDARY,1)[0] for f in required[1:]),
                             superseded_value_answer=complete and integer(answer)==int(required[0].split(' is ')[1].split()[0]),
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
    return dict(status="COMPLETE_SIGNED_CORRECTION_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256, system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                baseline_commit=BASELINE_COMMIT, candidate_commit=CANDIDATE_COMMIT, totals=summary(rows),
                by_domain={d: summary([r for r in rows if r["case"].startswith(d + "_")]) for d in ("signed", "relative", "control")},
                matched=matched, cases=rows, official_ai_rating=None,
                scope="Public synthetic exact baseline versus #509 candidate Kotlin recovery. Signed replacement, subsequent relative adjustment and unchanged unsigned controls. Same model/profile/seed/cap and current question. Strict bounded integer grading with ASCII or Unicode minus. No raw replies retained; not independent assessment or live Android inference.")


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
