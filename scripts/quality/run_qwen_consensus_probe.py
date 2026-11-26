"""Matched three-attempt consensus versus first Qwen thinking response.
Public synthetic scalar tasks only; no answer key in the selector or prompts.
"""
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

SEED = 68129371
CAP = 768


def cases(seed):
    rng = random.Random(seed)
    a, b, c = rng.sample(["Varn", "Jex", "Tilm", "Gev", "Pold"], 3)
    n, k, m = rng.randint(5, 9), rng.randint(2, 4), rng.randint(12, 19)
    return [
        ("logic_chain", f"Whenever {a} is active, {b} is active. Whenever {c} is active, {b} is inactive. If {a} is active, can {c} be active under these rules? Reply YES or NO only.", "NO"),
        ("logic_existential", f"At least one {a} is a {b}. Every {b} is a {c}. Must some {a} be a {c}? Reply YES or NO only.", "YES"),
        ("logic_sufficiency", f"A permit is necessary but not sufficient for access. A person has no permit. Can this person have access under the rule? Reply YES or NO only.", "NO"),
        ("quantity_reverse", f"After {k} coins are removed from a purse and its remaining contents are doubled, it contains {2*(m-k)} coins. How many coins were initially in it? Reply with only the integer.", str(m)),
        ("quantity_recurrence", f"A counter begins at {m}. Exactly {k} times, add {n} and then double the counter. What is its final count? Reply with only the integer.", str((m+2*n)*2**k-2*n)),
        ("quantity_net", f"A seller buys {n} units for {m} coins each, sells all except {k} for {m+k} coins each, and pays a single fee of {k} coins. What is the net cash received minus all costs? Reply with only the integer.", str((n-k)*(m+k)-n*m-k)),
        ("context_correction", f"A first note assigns {m} units to the east room. A second note corrects the east room to {n} units. The third note assigns {k} units to the west room. What is the total assigned to both rooms? Reply with only the integer.", str(n+k)),
        ("context_temporal", f"At 08:00 the gate was closed. At 09:00 it opened. At 11:00 it closed again. Was it open at 10:00? Reply YES or NO only.", "YES"),
        ("context_missing", f"A sealed container holds {n} objects. No information about their weights is given. Can the exact total weight be determined from this statement alone? Reply YES or NO only.", "NO"),
        ("strategy_capacity", f"A truck can carry at most {k} crates per trip. There are {n*k+1} crates and every crate must be moved. What is the minimum number of trips? Reply with only the integer.", str(n+1)),
        ("strategy_constraint", f"A job needs {n} workers and {m} coins. There are {n-1} workers and {m+k} coins. Are both resource requirements met? Reply YES or NO only.", "NO"),
        ("strategy_alternative", f"A shipment is accepted if it satisfies either rule A or rule B. It satisfies rule A and fails rule B. Is it accepted? Reply YES or NO only.", "YES"),
    ]


def bridge(jar, question, attempts):
    def encode(value):
        return base64.b64encode(value.encode()).decode("ascii")
    lines = [encode(question)]
    for attempt in attempts:
        lines += ["1" if attempt["complete"] else "0", encode(attempt["visible"])]
    result = subprocess.run(["java", "-jar", str(jar)], input="\n".join(lines)+"\n",
                            text=True, capture_output=True, check=True, timeout=15)
    if result.stdout.strip() not in ("0", "1"):
        raise RuntimeError("Invalid consensus bridge selection")
    return int(result.stdout.strip())


def evaluate(seed, infer, select, suite=None):
    suite = cases(seed) if suite is None else suite
    system = native.system_prompt()
    settings = native.PROFILES["qwen_think"]
    rows = []
    for index, (case, question, expected) in enumerate(suite):
        attempts = []
        for retry in range(3):
            call_seed = (seed + index*13 + retry*104729) % (2**31-1)
            payload = dict(prompt=native.raw_prompt(system, question, "think"),
                           n_predict=CAP, seed=call_seed, cache_prompt=False, stream=False,
                           ignore_eos=False, repeat_penalty=1.0, presence_penalty=0.0,
                           frequency_penalty=0.0, **settings)
            started = time.monotonic()
            reply = infer(payload)
            elapsed = round((time.monotonic()-started)*1000)
            actual = reply.get("generation_settings", {})
            for key, value in dict(settings, seed=call_seed, n_predict=CAP).items():
                found = actual.get(key)
                if type(found) not in (int, float) or not math.isclose(found, value, rel_tol=1e-6, abs_tol=1e-8):
                    raise RuntimeError("Unverified native settings")
            stop, truncated, tokens = reply.get("stop_type"), reply.get("truncated"), reply.get("tokens_predicted")
            if stop not in ("eos", "limit", "word") or type(truncated) is not bool:
                raise RuntimeError("Unverified native completion")
            if type(tokens) is not int or not 0 <= tokens <= CAP+8:
                raise RuntimeError("Unverified native token count")
            visible = native.visible_answer(reply.get("content"))
            complete = stop in ("eos", "word") and not truncated
            attempts.append(dict(visible=visible, complete=complete, tokens=tokens, elapsed_ms=elapsed))
        # No expected value or grading result reaches the selector.
        selected = select(question, [{"visible": a["visible"], "complete": a["complete"]} for a in attempts])
        if type(selected) is not int or selected not in (0, 1):
            raise RuntimeError("Invalid consensus selection")
        def correct(a):
            return a["complete"] and a["visible"].rstrip(".!").casefold() == expected.casefold()
        scores = [correct(a) for a in attempts]
        rows.append(dict(case=case, baseline_correct=scores[0], candidate_correct=scores[selected],
                         changed=selected != 0, any_attempt_correct=any(scores),
                         complete_calls=sum(a["complete"] for a in attempts),
                         baseline_tokens=attempts[0]["tokens"],
                         candidate_tokens=sum(a["tokens"] for a in attempts),
                         baseline_ms=attempts[0]["elapsed_ms"],
                         candidate_ms=sum(a["elapsed_ms"] for a in attempts)))
    def summarize(group):
        return dict(attempted=len(group), baseline_correct=sum(r["baseline_correct"] for r in group),
                    candidate_correct=sum(r["candidate_correct"] for r in group),
                    changed=sum(r["changed"] for r in group),
                    candidate_only=sum(r["candidate_correct"] and not r["baseline_correct"] for r in group),
                    baseline_only=sum(r["baseline_correct"] and not r["candidate_correct"] for r in group),
                    any_attempt_correct=sum(r["any_attempt_correct"] for r in group),
                    complete_calls=sum(r["complete_calls"] for r in group),
                    baseline_tokens=sum(r["baseline_tokens"] for r in group),
                    candidate_tokens=sum(r["candidate_tokens"] for r in group),
                    baseline_ms=sum(r["baseline_ms"] for r in group),
                    candidate_ms=sum(r["candidate_ms"] for r in group))
    return dict(status="COMPLETE_CONSENSUS_CPU_DIAGNOSTIC", seed=seed, cap=CAP,
                model_sha256=native.MODEL_SHA256,
                system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                totals=summarize(rows), by_domain={d:summarize([r for r in rows if r["case"].startswith(d+"_")])
                    for d in ("logic", "quantity", "context", "strategy")},
                cases=rows, official_ai_rating=None,
                scope="Public developmental scalar tasks; three independent seeded calls, not independent models. Agreement is not proof. Host-only candidate.")


def run(binary, seed, jar):
    if not native.check_model(native.MODEL_PATH):
        raise RuntimeError("Unverified or unavailable Qwen3 1.7B GGUF")
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
                    raise RuntimeError("Native server exited during startup")
                try:
                    if native.request(opener, "/health", timeout=3).get("status") == "ok":
                        break
                except (OSError, ValueError):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("Native server startup timed out")
            result = evaluate(seed, lambda payload: native.request(opener, "/completion", payload), lambda q, a: bridge(jar, q, a))
            if not native.check_model(native.MODEL_PATH):
                raise RuntimeError("Model identity changed during comparison")
            return result
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
    parser.add_argument("bridge", type=Path)
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()
    if not 0 <= args.seed < 2**31-1:
        parser.error("invalid seed")
    print(json.dumps(run(args.binary, args.seed, args.bridge), indent=2, allow_nan=False))
