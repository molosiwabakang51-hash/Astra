"""Isolate explicitly disjoint named correction boundaries using exact production builders."""
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

SEED = 76519342
CAP = 768
BASELINE_COMMIT = "b17b1fe69ceba9981ca8b30039a17dc99c88dae2"
CANDIDATE_COMMIT = "e6288481a958131d11e62146f8768900106825e4"
SUFFIX = " Reply with only the integer."
BOUNDARY = "\nCURRENT USER MESSAGE (answer this message, not the excerpts):\n"


def cases(seed):
    rng = random.Random(seed)
    values = rng.sample(range(101, 999), 16)
    topics = [
        ("aquarium renovation budget", "ferry passenger limit"),
        ("observatory equipment reserve", "library visitor count"),
        ("bridge maintenance allocation", "theatre seating capacity"),
        ("garden restoration fund", "warehouse parcel inventory"),
    ]
    suite = []
    for i,(first,second) in enumerate(topics):
        a,b,c,d=values[i*4:i*4+4]
        first_anchor=f"My {first} is {a}."
        first_change=f"Actually, make it {b}."
        second_anchor=f"Actually, my {second} is {c}."
        second_change=f"No, make it {d}."
        tail=[("assistant",f"Unrelated weather note {j}. "+"X"*210) for j in range(15)]
        history=[("user",first_anchor),("user",first_change),
                 ("user",second_anchor),("user",second_change)]+tail
        suite.append((f"boundary_{i}",f"What was my {first}?"+SUFFIX,str(b),
                      history,(first_anchor,first_change)))
        suite.append((f"later_{i}",f"What was my {second}?"+SUFFIX,str(d),
                      history,(second_anchor,second_change)))
        same=f"Actually, my {first} is {c}."
        control=[("user",first_anchor),("user",first_change),
                 ("user",same),("user",second_change)]+tail
        suite.append((f"control_{i}",f"What was my {first}?"+SUFFIX,str(d),
                      control,(same,second_change)))
    # Alternate arm order within all three domains.
    return suite


def distractors(case,history):
    if case.startswith("boundary_"):
        return (history[2][1],history[3][1])
    if case.startswith("later_"):
        return (history[0][1],history[1][1])
    return ()


def prepare(seed,builders,suite):
    if set(builders)!={"baseline","recovery"}:
        raise ValueError("Both pinned compiled Kotlin builders required")
    prepared={}
    distinguished=0
    for case,question,expected,history,required in suite:
        prompts={arm:build(question,history) for arm,build in builders.items()}
        bad=distractors(case,history)
        for arm,prompt in prompts.items():
            if not prompt.endswith(BOUNDARY+question) or len(prompt)>3000:
                raise RuntimeError("Current question changed or context bound exceeded")
            head=prompt.rsplit(BOUNDARY,1)[0]
            if arm=="recovery" and not all(fragment in head for fragment in required):
                raise RuntimeError("Candidate lost required owner evidence")
            if arm=="recovery" and any(fragment in head for fragment in bad):
                raise RuntimeError("Candidate retained disjoint correction chain")
        if case.startswith("control_") and prompts["baseline"]!=prompts["recovery"]:
            raise RuntimeError("Same-topic control prompt changed")
        if case.startswith("boundary_"):
            old=prompts["baseline"].rsplit(BOUNDARY,1)[0]
            distinguished+=any(fragment in old for fragment in bad)
        prepared[case]=prompts
    if not distinguished:
        raise RuntimeError("No boundary case exercises the original defect")
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
                             elapsed_ms=elapsed, required_retained=sum(fragment in q.rsplit(BOUNDARY,1)[0] for fragment in required),
                             disjoint_fragments_retained=sum(f in q.rsplit(BOUNDARY,1)[0] for f in distractors(case,history)),
                             disjoint_fragment_count=len(distractors(case,history)),
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
    return dict(status="COMPLETE_NAMED_CORRECTION_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256, system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                baseline_commit=BASELINE_COMMIT, candidate_commit=CANDIDATE_COMMIT, totals=summary(rows),
                by_domain={d: summary([r for r in rows if r["case"].startswith(d + "_")]) for d in ("boundary", "later", "control")},
                matched=matched, cases=rows, official_ai_rating=None,
                scope="Public synthetic exact pre/post #501 Kotlin recovery; earlier-topic contamination, later-topic recall and unchanged same-topic controls. Same model/profile/seed/cap and exact current question. Distinct randomized values, no raw replies retained. Not independent assessment or live Android inference.")


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
