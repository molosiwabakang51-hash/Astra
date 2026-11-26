"""Actual Qwen calculator selection and synthesis probe, not an intelligence rating.

The production Kotlin parser and prompts are compiled into a local host bridge.
Only public synthetic tasks, pinned GGUF and loopback native inference are used.
No generated text or private reasoning is exported.
"""
import argparse
import base64
from decimal import Decimal, InvalidOperation
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request

import run_qwen_profile_comparison as native

SEED = 81426791

# Expected values are evaluator-only and never passed to the model.
CASES = (
    ("total", "There are 13 packs costing 7 coins each and a fee of 9 coins. What is the total? Reply only with the number.", "100"),
    ("remaining", "A store has 18 packs of 6 objects. It sells 5 packs. How many objects remain? Reply only with the number.", "78"),
    ("decimal", "Buy 4 units at 2.75 coins each and add a fee of 1.50 coins. What is the total? Reply only with the number.", "12.5"),
    ("division", "Share 35 litres equally among 4 containers. How many litres per container? Reply only with the number.", "8.75"),
    ("difference", "Revenue is 125 coins and expenses are 83 coins plus 17 coins. What is the profit? Reply only with the number.", "25"),
    ("precedence", "A counter starts at 12, increases by 3 and is then multiplied by 5. What is the final count? Reply only with the number.", "75"),
    ("negative", "A balance of 14 coins decreases by 21 coins. What is the balance? Reply only with the number.", "-7"),
    ("ratio", "A runner covers 24 kilometres in 3 hours at constant speed. What is the speed in kilometres per hour? Reply only with the number.", "8"),
    ("missing", "A person buys 17 objects. The price per object is not stated. What is the total cost?", None),
    ("unsupported", "Which of the two novels has the more convincing narrator? No text or other information is provided.", None),
    ("repeating", "Give the exact terminating decimal for 2 divided by 3.", None),
    ("ambiguous", "Add 1,25 and 5. The comma notation is not defined.", None),
)


def bridge(jar, operation, question, proposal=None):
    values = [question] if proposal is None else [question, proposal]
    data = "\n".join(base64.b64encode(v.encode()).decode("ascii") for v in values) + "\n"
    result = subprocess.run(["java", "-jar", str(jar), operation], input=data,
                            text=True, capture_output=True, check=True, timeout=15)
    lines = result.stdout.splitlines()
    def decode(line):
        return base64.b64decode(line, validate=True).decode("utf-8")
    if operation == "plan" and len(lines) == 1:
        return decode(lines[0])
    if operation == "evaluate" and lines == ["REJECT"]:
        return None
    if operation == "evaluate" and len(lines) == 3 and lines[0] == "ACCEPT":
        return decode(lines[1]), decode(lines[2])
    raise RuntimeError("Invalid production parser bridge response")


def scalar_equal(answer, expected):
    # Only a complete standalone decimal earns strict answer credit.
    if expected is None or not re.fullmatch(r"[+-]?[0-9]{1,40}(?:\.[0-9]{1,12})?", answer):
        return False
    try:
        return Decimal(answer) == Decimal(expected)
    except InvalidOperation:
        return False


def evaluate(infer, invoke):
    system = native.system_prompt()
    rows = []
    def generate(prompt, cap, seed):
        settings = native.PROFILES["qwen_direct"]
        payload = dict(prompt=native.raw_prompt(system, prompt, "direct"),
                       n_predict=cap, seed=seed, cache_prompt=False, stream=False,
                       ignore_eos=False, repeat_penalty=1.0, presence_penalty=0.0,
                       frequency_penalty=0.0, **settings)
        started = time.monotonic()
        reply = infer(payload)
        elapsed = round((time.monotonic() - started)*1000)
        actual = reply.get("generation_settings", {})
        for key, value in dict(settings, seed=seed, n_predict=cap).items():
            found = actual.get(key)
            if type(found) not in (float, int) or not math.isclose(found, value, rel_tol=1e-6, abs_tol=1e-8):
                raise RuntimeError("Unverified native settings")
        stop, truncated, tokens = reply.get("stop_type"), reply.get("truncated"), reply.get("tokens_predicted")
        if stop not in ("eos", "limit", "word") or type(truncated) is not bool:
            raise RuntimeError("Unverified stop state")
        if type(tokens) is not int or not 0 <= tokens <= cap+8:
            raise RuntimeError("Unverified token count")
        complete = stop in ("eos", "word") and not truncated
        return native.visible_answer(reply.get("content")), complete, tokens, elapsed

    for i, (case, question, expected) in enumerate(CASES):
        seed = SEED + i*13
        plan, complete, tokens, elapsed = generate(invoke("plan", question), 128, seed)
        parsed = invoke("evaluate", question, plan) if complete else None
        eligible = expected is not None
        valid = parsed is not None
        correct = valid and eligible and scalar_equal(parsed[0], expected)
        row = dict(case=case, eligible=eligible, planning_complete=complete,
                   explicit_none=complete and plan == "NONE", parser_accepted=valid,
                   computed_value_correct=bool(correct), synthesis_attempted=False,
                   synthesis_complete=False, synthesis_strict_correct=False,
                   planning_tokens=tokens, planning_ms=elapsed,
                   synthesis_tokens=0, synthesis_ms=0)
        # Test actual application behavior: synthesize even a syntactically
        # valid but semantically wrong calculation, and count it as a failure.
        if valid:
            answer, done, used, duration = generate(parsed[1], 768, seed+1)
            row.update(synthesis_attempted=True, synthesis_complete=done,
                       synthesis_strict_correct=bool(correct and done and scalar_equal(answer.rstrip(".!"), expected)),
                       synthesis_tokens=used, synthesis_ms=duration)
        rows.append(row)
    positive = [r for r in rows if r["eligible"]]
    negative = [r for r in rows if not r["eligible"]]
    return dict(status="COMPLETE_CALCULATOR_CPU_DIAGNOSTIC", seed=SEED,
                model_sha256=native.MODEL_SHA256,
                system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                eligible=len(positive), unsuitable=len(negative),
                correct_values=sum(r["computed_value_correct"] for r in positive),
                strict_synthesis=sum(r["synthesis_strict_correct"] for r in positive),
                unsuitable_rejected=sum(not r["parser_accepted"] for r in negative),
                unsuitable_explicit_none=sum(r["explicit_none"] for r in negative),
                planning_incomplete=sum(not r["planning_complete"] for r in rows),
                cases=rows, official_ai_rating=None,
                scope="Public developmental CPU probe using production Kotlin calculator, not handset or independent assessment.")


def run(binary, jar):
    if not native.check_model(native.MODEL_PATH):
        raise RuntimeError("Unverified Qwen model")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    command = [str(binary.resolve()), "-m", str(native.MODEL_PATH),
               "--host", "127.0.0.1", "--port", str(native.PORT),
               "--ctx-size", str(native.CONTEXT), "--parallel", "1", "--threads", "4",
               "--batch-size", "256", "--ubatch-size", "256",
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
            result = evaluate(lambda payload: native.request(opener, "/completion", payload),
                              lambda operation, question, proposal=None: bridge(jar, operation, question, proposal))
            if not native.check_model(native.MODEL_PATH):
                raise RuntimeError("Model identity changed")
            return result
        finally:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("bridge", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(run(args.binary, args.bridge), indent=2, allow_nan=False))
        return 0
    except (OSError, ValueError, RuntimeError, KeyError, subprocess.SubprocessError):
        print("Calculator CPU probe incomplete; no score awarded.")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
