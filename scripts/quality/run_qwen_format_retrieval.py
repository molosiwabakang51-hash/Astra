"""Pinned production retrieval comparison with irrelevant output-format history."""
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

SEED = 84271635
CAP = 768
BASELINE_COMMIT = "a971f45532f36771de4c5c022e051463ed38218c"
CANDIDATE_COMMIT = "d355f2cc543230e424283696aad4d1aae27b2b66"
DISTRACTOR = "The reply format uses only integer or UNKNOWN if not established."
DISTRACTOR_CORRECTION = "Actually, use the exact decimal."
SUFFIX = " Reply with only the integer, or UNKNOWN if not established."
BOUNDARY = "\nCURRENT USER MESSAGE (answer this message, not the excerpts):\n"


def cases(seed):
    rng = random.Random(seed)
    values = rng.sample(range(101, 999), 8)
    topics = ("greenhouse maintenance budget", "observatory equipment allocation",
              "library restoration fund", "museum research reserve")
    suite = []
    for i, topic in enumerate(topics):
        a,b = values[i*2:i*2+2]
        correction = f"Actually, make it {b} coins."
        history = [("user", f"My fictional {topic} is {a} coins."),
                   ("user", correction), ("user", DISTRACTOR),
                   ("user", DISTRACTOR_CORRECTION)] + [
                      ("assistant", f"Unrelated weather note {j}. " + "X"*210)
                      for j in range(15)]
        question = f"What was my {topic}?"
        suite.append((f"formatted_{i}", question+SUFFIX, str(b), history, (correction,)))
        # Use a separate control asking about the exact same owner fact,
        # with no formatting instruction. This is an unchanged-prompt control.
        suite.append((f"control_{i}", question, str(b), history, (correction,)))
    return suite[:2]+list(reversed(suite[2:4]))+suite[4:6]+list(reversed(suite[6:8]))


def prepare(seed, builders, suite):
    if set(builders) != {"baseline", "recovery"}:
        raise ValueError("Both pinned compiled Kotlin builders required")
    prepared={}
    for case,question,expected,history,required in suite:
        prompts={arm:build(question,history) for arm,build in builders.items()}
        for arm,prompt in prompts.items():
            if not prompt.endswith(BOUNDARY+question) or len(prompt)>3000:
                raise RuntimeError("Current question changed or context bound exceeded")
            head=prompt.rsplit(BOUNDARY,1)[0]
            if arm=="recovery":
                if not all(f in head for f in required):
                    raise RuntimeError("Candidate lost relevant owner correction")
                if DISTRACTOR in head or DISTRACTOR_CORRECTION in head:
                    raise RuntimeError("Candidate retained formatting distraction")
        if case.startswith("control_") and prompts["baseline"]!=prompts["recovery"]:
            raise RuntimeError("Unformatted control prompt changed")
        if case.startswith("formatted_"):
            head=prompts["baseline"].rsplit(BOUNDARY,1)[0]
            if DISTRACTOR not in head and DISTRACTOR_CORRECTION not in head:
                raise RuntimeError("Formatted baseline fixture not distinguishing")
            # Compare candidate context with an independent call for the same
            # semantic question without its output contract.
            plain=question.removesuffix(SUFFIX)
            plain_head=builders["recovery"](plain,history).rsplit(BOUNDARY,1)[0]
            if prompts["recovery"].rsplit(BOUNDARY,1)[0]!=plain_head:
                raise RuntimeError("Candidate retrieval is not format invariant")
        prepared[case]=prompts
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
                             distractor_retained=DISTRACTOR in q.rsplit(BOUNDARY,1)[0],
                             distractor_correction_retained=DISTRACTOR_CORRECTION in q.rsplit(BOUNDARY,1)[0],
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
    return dict(status="COMPLETE_FORMAT_RETRIEVAL_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256, system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                baseline_commit=BASELINE_COMMIT, candidate_commit=CANDIDATE_COMMIT, totals=summary(rows),
                by_domain={d: summary([r for r in rows if r["case"].startswith(d + "_")]) for d in ("formatted", "control")},
                matched=matched, cases=rows, official_ai_rating=None,
                scope="Public synthetic pre/post #494 compiled Kotlin builders; unchanged unformatted controls and exact current question, same model/profile/seed/cap. Candidate retrieval must be invariant to supported terminal format. Strict scalar scoring also applies to unformatted controls for paired comparison; not a broad quality assessment. No raw replies retained.")


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
