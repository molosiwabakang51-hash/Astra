"""Public developmental inverse-word-problem comparison, not an independent rating."""
import argparse
import base64
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
from run_qwen_calculator_probe import scalar_equal

SEED = 90276143
CAP = 768


def cases(seed):
    rng = random.Random(seed)
    x, a, b, c, d = (rng.randint(11, 25), rng.randint(3, 7),
                     rng.randint(8, 14), rng.randint(2, 5), rng.randint(6, 10))
    return [
        ("inverse_balance", f"A wallet receives {b} coins and spends {c} coins. It now contains {x+b-c} coins. How many coins did it initially contain?", str(x)),
        ("inverse_cost", f"A bill of {a*x+b} coins covers {a} identical items and a delivery fee of {b} coins. What is the cost of one item?", str(x)),
        ("inverse_number", f"A number multiplied by {a} and then decreased by {b} equals {a*x-b}. What is that number?", str(x)),
        ("inverse_inventory", f"A warehouse starts with full crates of {a} items each. After selling {c} full crates, {(x-c)*a} items remain. How many crates were present initially?", str(x)),
        ("inverse_share", f"An unknown amount of liquid is divided equally among {a} vessels. Each receives {x} litres. How many litres were there initially?", str(a*x)),
        ("inverse_comparison", f"Ada has {b} more coins than Bea. Together they have {2*x+b} coins. How many coins does Bea have?", str(x)),
        ("control_forward", f"Buy {a} books costing {x} coins each and pay {d} coins in delivery. What is the total cost?", str(a*x+d)),
        ("control_decimal", f"A delivery costs 1.5 coins per item plus {c} coins. The total is {1.5*x+c} coins. How many items were delivered?", str(x)),
        ("unsuitable_missing", f"A shop sells {a} items at an unspecified price. What is the revenue?", None),
        ("unsuitable_nonlinear", f"A rectangle has an area of {a*x} square units. Its side lengths are not given. What is its perimeter?", None),
        ("unsuitable_recurring", "Three identical items cost 10 coins in total. What is the exact terminating decimal cost of one item?", None),
    ]


def bridge(jar, operation, question, proposal=None):
    values = [question] if proposal is None else [question, proposal]
    data = "\n".join(base64.b64encode(v.encode()).decode("ascii") for v in values) + "\n"
    result = subprocess.run(["java", "-jar", str(jar), operation], input=data,
                            text=True, capture_output=True, check=True, timeout=15)
    lines = result.stdout.splitlines()
    if operation == "plan" and len(lines) == 1:
        return base64.b64decode(lines[0], validate=True).decode("utf-8")
    if operation == "evaluate" and lines == ["REJECT"]:
        return None
    if operation == "evaluate" and len(lines) == 2 and lines[0] == "ACCEPT":
        return base64.b64decode(lines[1], validate=True).decode("utf-8")
    raise RuntimeError("Unexpected equation bridge response")


def evaluate(infer, invoke, suite=None, seed=SEED):
    suite = cases(seed) if suite is None else suite
    system = native.system_prompt()
    settings = native.PROFILES["qwen_think"]
    rows = []
    for i, (name, question, expected) in enumerate(suite):
        eligible = expected is not None
        for arm in (("unassisted", "equation") if i % 2 == 0 else ("equation", "unassisted")):
            prompt = (question + " Reply with only the exact number, or NONE if no unique exact terminating decimal can be determined."
                      if arm == "unassisted" else invoke("plan", question))
            call_seed = seed + 13*i
            payload = dict(prompt=native.raw_prompt(system, prompt, "think"),
                           n_predict=CAP, seed=call_seed, cache_prompt=False, stream=False,
                           ignore_eos=False, repeat_penalty=1.0, presence_penalty=0.0,
                           frequency_penalty=0.0, **settings)
            started = time.monotonic()
            reply = infer(payload)
            elapsed = round((time.monotonic()-started)*1000)
            actual = reply.get("generation_settings", {})
            for key, value in dict(settings, seed=call_seed, n_predict=CAP).items():
                found = actual.get(key)
                if type(found) not in (float, int) or not math.isclose(found, value, rel_tol=1e-6, abs_tol=1e-8):
                    raise RuntimeError("Unverified native configuration")
            stop, truncated, tokens = reply.get("stop_type"), reply.get("truncated"), reply.get("tokens_predicted")
            if stop not in ("eos", "limit", "word") or type(truncated) is not bool:
                raise RuntimeError("Unverified stop outcome")
            if type(tokens) is not int or not 0 <= tokens <= CAP+8:
                raise RuntimeError("Unverified token count")
            complete = stop in ("eos", "word") and not truncated
            answer = native.visible_answer(reply.get("content"))
            computed = (invoke("evaluate", question, answer)
                        if complete and arm == "equation" else None)
            value = computed if arm == "equation" else answer
            rows.append(dict(case=name, arm=arm, eligible=eligible, complete=complete,
                             accepted_equation=computed is not None,
                             explicit_none=complete and answer == "NONE",
                             correct=bool(eligible and complete and value is not None and scalar_equal(value, expected)),
                             unsuitable_numeric=(not eligible and complete and value is not None
                                                 and scalar_equal(value, value)),
                             tokens=tokens, elapsed_ms=elapsed,
                             prompt_sha256=hashlib.sha256(prompt.encode()).hexdigest()))
    totals = {}
    for arm in ("unassisted", "equation"):
        group = [r for r in rows if r["arm"] == arm]
        eligible = [r for r in group if r["eligible"]]
        unsuitable = [r for r in group if not r["eligible"]]
        totals[arm] = dict(eligible=len(eligible), unsuitable=len(unsuitable),
                           correct=sum(r["correct"] for r in eligible),
                           complete=sum(r["complete"] for r in group),
                           accepted_equation=sum(r["accepted_equation"] for r in eligible),
                           eligible_false_abstention=sum(r["explicit_none"] for r in eligible),
                           unsuitable_numeric=sum(r["unsuitable_numeric"] for r in unsuitable),
                           unsuitable_explicit_none=sum(r["explicit_none"] for r in unsuitable),
                           tokens=sum(r["tokens"] for r in group),
                           elapsed_ms=sum(r["elapsed_ms"] for r in group))
    matched = dict(both=0, unassisted_only=0, equation_only=0, neither=0)
    for name, _, expected in suite:
        if expected is None:
            continue
        pair = {r["arm"]: r["correct"] for r in rows if r["case"] == name}
        a, b = pair["unassisted"], pair["equation"]
        matched["both" if a and b else "unassisted_only" if a else "equation_only" if b else "neither"] += 1
    return dict(status="COMPLETE_INVERSE_CPU_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256,
                system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                totals=totals, matched=matched, cases=rows, official_ai_rating=None,
                scope="Public synthetic word problems. Assisted exact value does not certify semantic equation fidelity. No production activation or handset assessment.")


def run(binary, jar):
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


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("bridge", type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.binary, args.bridge), indent=2, allow_nan=False))
