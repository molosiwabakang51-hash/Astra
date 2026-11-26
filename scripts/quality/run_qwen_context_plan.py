"""Separate contextual operand selection from exact assisted arithmetic; host-only."""
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

SEED = 97341821
CAP = 768
CANDIDATE_COMMIT = "db99d6a8acd78fdbacebe6f6fe7994842ed68448"
BOUNDARY = "\nCURRENT USER MESSAGE (answer this message, not the excerpts):\n"
PLAN = "Instead of calculating the final answer, select the two current integer values from the owner excerpts and the operation requested in the question. Output only SUM a b or DIFF a b (absolute difference), using the two exact integers, or NONE if either value or the operation is not established. Do not use superseded values or assistant guesses."
OWNER_LINE = re.compile(r'^Previous user: "(.*)"$')
INTEGER = re.compile(r'(?<![\w.,+-])[0-9]{1,9}(?![\w.,])')
PLAN_LINE = re.compile(r'(SUM|DIFF) ([0-9]{1,9}) ([0-9]{1,9})')


def cases(seed):
    rng = random.Random(seed)
    values = rng.sample(range(101, 999), 16)
    topics = [("clinic renovation budget", "bus fare"),
              ("college equipment allocation", "gallery cost"),
              ("autumn research reserve", "office rent"),
              ("bridge restoration fund", "locker fee")]
    suite = []
    for i, (first, second) in enumerate(topics):
        a, b, c, d = values[i*4:i*4+4]
        f1, f2 = f"Actually, make it {b} coins.", f"No, make it {d} coins."
        original = [("user", f"The fictional {first} is {a} coins."),
                    ("assistant", "Noted."), ("user", f1),
                    ("user", f"The fictional {second} is {c} coins."),
                    ("assistant", "Noted.")]
        filler = [("assistant", "Unrelated weather discussion. " + "Rain and clouds. "*12)
                  for _ in range(12)]
        op = "SUM" if i % 2 == 0 else "DIFF"
        ask = "combined total" if op == "SUM" else "absolute difference"
        q = f"Use the current {first} and {second}. What is their {ask} in coins? Reply with only the integer, or UNKNOWN if not established."
        answer = str(b+d if op == "SUM" else abs(b-d))
        suite.append((f"dual_{i}", q, answer, original + [("user", f2)] + filler,
                      (f1, f2), (op, b, d)))
        unavailable = "Actually, I do not know the current amount."
        suite.append((f"missing_{i}", q, "UNKNOWN", original + [("user", unavailable)] + filler,
                      (f1, unavailable), None))
        single = f"Use the current {first}. How many coins are allocated? Reply with only the integer."
        suite.append((f"single_{i}", single, str(b), original + [("user", f2)] + filler,
                      (f1,), None))
    return suite


def checked_plan(reply, prompt):
    """Check provenance and arithmetic only, NOT semantic correctness."""
    if reply == "NONE":
        return "abstained", None, None
    match = PLAN_LINE.fullmatch(reply)
    if match is None or len(prompt) > 3000 or BOUNDARY not in prompt:
        return "invalid", None, None
    head, _ = prompt.rsplit(BOUNDARY, 1)
    if "[Some linked corrections are omitted" in head:
        return "rejected", None, None
    allowed = set()
    for line in head.splitlines():
        quote = OWNER_LINE.fullmatch(line)
        if quote:
            allowed.update(int(n) for n in INTEGER.findall(quote.group(1)))
    op, a, b = match.group(1), int(match.group(2)), int(match.group(3))
    if a not in allowed or b not in allowed:
        return "rejected", None, None
    return "computed", str(a+b if op == "SUM" else abs(a-b)), (op, a, b)


def prepare(seed, builders, suite):
    if set(builders) != {"recovery"}:
        raise ValueError("Pinned compiled Kotlin recovery builder required")
    prepared = {}
    for case, question, expected, history, required, oracle in suite:
        prompt = builders["recovery"](question, history)
        if not prompt.endswith(BOUNDARY + question) or len(prompt) > 3000:
            raise RuntimeError("Current question changed or context budget exceeded")
        if not all(fragment in prompt for fragment in required):
            raise RuntimeError("Required evidence missing")
        candidate = prompt
        if not case.startswith("single_"):
            head, current = prompt.rsplit(BOUNDARY, 1)
            suffix = "Reply with only the integer, or UNKNOWN if not established."
            if not current.endswith(suffix):
                raise RuntimeError("Unexpected baseline output contract")
            candidate = head + BOUNDARY + current[:-len(suffix)] + PLAN
        if len(candidate) > 3000:
            raise RuntimeError("Candidate prompt exceeds production bound")
        prepared[case] = {"baseline": prompt, "assisted": candidate}
    return prepared


def evaluate(seed, infer, builders, suite=None):
    suite = cases(seed) if suite is None else suite
    if len({row[0] for row in suite}) != len(suite):
        raise ValueError("Duplicate case IDs")
    prepared = prepare(seed, builders, suite)
    system = native.system_prompt()
    settings = native.PROFILES["qwen_think"]
    rows = []
    for i, (case, question, expected, history, required, oracle) in enumerate(suite):
        arms = ("baseline", "assisted") if i % 2 == 0 else ("assisted", "baseline")
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
            plan_status, plan_correct = None, None
            if arm == "assisted" and not case.startswith("single_"):
                plan_status, computed, selected = checked_plan(answer, prepared[case]["baseline"]) if complete else ("incomplete", None, None)
                plan_correct = (selected is not None and oracle is not None and selected[0] == oracle[0] and sorted(selected[1:]) == sorted(oracle[1:]))
                correct = complete and ((plan_status == "abstained" and oracle is None) or (plan_status == "computed" and plan_correct and computed == expected))
            else:
                correct = complete and answer.casefold() == expected.casefold()
            rows.append(dict(case=case, arm=arm, correct=correct, complete=complete, tokens=tokens,
                             elapsed_ms=elapsed, plan_status=plan_status, semantic_plan_correct=plan_correct,
                             required_retained=sum(fragment in q for fragment in required),
                             required_count=len(required), tail_notice="including a later correction" in q,
                             prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest()))
    def summary(group):
        return {arm: dict(attempted=sum(r["arm"] == arm for r in group),
                          correct=sum(r["correct"] for r in group if r["arm"] == arm),
                          complete=sum(r["complete"] for r in group if r["arm"] == arm),
                          tokens=sum(r["tokens"] for r in group if r["arm"] == arm),
                          elapsed_ms=sum(r["elapsed_ms"] for r in group if r["arm"] == arm))
                for arm in ("baseline", "assisted")}
    matched = dict(both_correct=0, baseline_only=0, candidate_only=0, neither=0)
    for case, _, _, _, _, _ in suite:
        a = {r["arm"]: r["correct"] for r in rows if r["case"] == case}
        matched["both_correct" if a["baseline"] and a["assisted"] else "baseline_only" if a["baseline"]
                else "candidate_only" if a["assisted"] else "neither"] += 1
    return dict(status="COMPLETE_CONTEXT_PLAN_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256, system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                recovery_commit=CANDIDATE_COMMIT, plan_sha256=hashlib.sha256(PLAN.encode()).hexdigest(), totals=summary(rows),
                by_domain={d: summary([r for r in rows if r["case"].startswith(d + "_")]) for d in ("dual", "missing", "single")},
                matched=matched, cases=rows, official_ai_rating=None,
                scope="Public synthetic histories through pinned Kotlin builder; oracle eligibility, model selects operation and operands; exact host arithmetic with source-presence gate, not semantic verification. Assisted score separate; no production router, no independent assessment or saved reasoning traces.")


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
    parser.add_argument("--candidate-context-jar", type=Path, required=True)
    args = parser.parse_args()
    if not 0 <= args.seed < 2**31 - 1:
        parser.error("Invalid seed")
    builders = {"recovery": lambda q, h: native.recovery_prompt(args.candidate_context_jar, q, h)}
    print(json.dumps(run(args.binary, args.seed, builders), indent=2, allow_nan=False))
