#!/usr/bin/env python3
"""Experimental real Qwen3 1.7B CPU sampling comparison; not a handset rating.

Only synthetic problems, the pinned GGUF, and a loopback llama-server are used.
Compare the prior and Qwen-recommended profiles on identical prompts and seeds.
No tool-assisted answers, private inputs, paid API or saved reasoning traces.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import re
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from prepare_offline_android import MODEL_PATH, MODEL_SHA256, check_model

CONTEXT = 4096
PORT = 18765
PROFILES = {
    "legacy": {"temperature": 0.3, "top_p": 0.95, "top_k": 40, "min_p": 0.05},
    "qwen_direct": {"temperature": 0.7, "top_p": 0.8, "top_k": 20, "min_p": 0.0},
    "qwen_think": {"temperature": 0.6, "top_p": 0.95, "top_k": 20, "min_p": 0.0},
}
CAPS = {"direct": 256, "think": 768}
SYSTEM_PATH = ROOT / "android/app/src/main/java/com/astra/agent/AstraConversationContext.kt"


def system_prompt():
    """Extract only the literal production system prompt; refuse source drift."""
    source = SYSTEM_PATH.read_text(encoding="utf-8")
    block = source.split("fun systemPrompt(): String =", 1)[1].split("\n\n    /**", 1)[0]
    parts = re.findall(r'"(?:[^"\\]|\\.)*"', block)
    if not parts or re.sub(r'"(?:[^"\\]|\\.)*"|\s|\+', "", block):
        raise ValueError("Production prompt is no longer a literal concatenation")
    text = "".join(json.loads(part) for part in parts)
    if not 100 <= len(text) <= 4000:
        raise ValueError("Unexpected production prompt size")
    return text


def cases(seed):
    rng = random.Random(seed)
    a, b, c = rng.sample(["Neral", "Vekto", "Solan", "Fexil", "Dorin", "Pavro"], 3)
    units, cost, fixed = rng.randrange(4, 15), rng.randrange(3, 13), rng.randrange(2, 17)
    start, increment, repeats = rng.randrange(2, 10), rng.randrange(2, 7), rng.randrange(3, 6)
    return [
        ("deduction", f"Every {a} is a {b}. No {b} is a {c}. Can any {a} be a {c} under only these rules? Reply YES or NO only.", "NO"),
        ("non_entailment", f"Every {a} is a {b}. Some {b} are {c}. Does it necessarily follow that some {a} are {c}? Reply YES or NO only.", "NO"),
        ("quantitative", f"A workshop makes {units} objects for {cost} coins each, then pays a fixed fee of {fixed} coins. What is the total cost? Reply with only the integer.", str(units * cost + fixed)),
        ("ordering", f"Three parcels {a}, {b}, {c} must be dispatched in order. {a} must be after {b}; {c} must be before {b}. Which parcel is last? Reply only with its name.", a),
        ("program_trace", f"A variable x starts at {start}. A loop runs exactly {repeats} times, adding {increment} to x each time. What is x after the loop? Reply with only the integer.", str(start + increment * repeats)),
        ("uncertainty", f"A sealed box contains either a blue or a red object. No one has inspected it and no probabilities are given. Can its actual color be determined from this information alone? Reply YES or NO only.", "NO"),
    ]


def raw_prompt(system, question, mode):
    if mode not in CAPS:
        raise ValueError("Unknown mode")
    # Match the Android soft switch before assistant generation. This is raw
    # ChatML in the CPU server, not a claim to execute the Android JNI path.
    suffix = "/think" if mode == "think" else "/no_think"
    return ("<|im_start|>system\n" + system + "<|im_end|>\n"
            "<|im_start|>user\n" + question + "\n" + suffix + "<|im_end|>\n"
            "<|im_start|>assistant\n")


def visible_answer(raw):
    # Conservative: no reasoning text may be reported or scored.
    if not isinstance(raw, str):
        return ""
    text = re.sub(r"<think>.*?</think>", "", raw, flags=re.I | re.S)
    if re.search(r"</think>", text, re.I):
        text = re.split(r"</think>", text, flags=re.I)[-1]
    if re.search(r"<think>|</?thi?n?k?$", text, re.I):
        return ""
    return text.strip()


def request(opener, path, payload=None, timeout=180):
    if path not in ("/health", "/completion"):
        raise ValueError("Only loopback inference endpoints allowed")
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}{path}", data=data,
                                 headers={"Content-Type": "application/json"})
    with opener.open(req, timeout=timeout) as response:
        body = response.read(1_000_001)
    if len(body) > 1_000_000:
        raise RuntimeError("Unexpected inference response size")
    return json.loads(body)


def evaluate(seed, infer):
    system = system_prompt()
    rows = []
    for index, (case, question, expected) in enumerate(cases(seed)):
        for mode in ("direct", "think"):
            names = ["legacy", "qwen_" + mode]
            if index % 2:
                names.reverse()
            for name in names:
                settings = PROFILES[name]
                call_seed = (seed + index * 13) % (2**31 - 1)
                payload = dict(prompt=raw_prompt(system, question, mode),
                               n_predict=CAPS[mode], seed=call_seed,
                               cache_prompt=False, stream=False, ignore_eos=False,
                               repeat_penalty=1.0, presence_penalty=0.0,
                               frequency_penalty=0.0, **settings)
                t = time.monotonic()
                reply = infer(payload)
                elapsed = round((time.monotonic() - t) * 1000)
                actual = reply.get("generation_settings", {})
                for key, value in dict(settings, seed=call_seed, n_predict=CAPS[mode]).items():
                    found = actual.get(key)
                    if type(found) not in (float, int) or not math.isclose(found, value, rel_tol=1e-6, abs_tol=1e-8):
                        raise RuntimeError("Native server did not confirm requested sampling configuration")
                stop = reply.get("stop_type")
                if stop not in ("eos", "limit", "word") or type(reply.get("truncated")) is not bool:
                    raise RuntimeError("Unverified native completion outcome")
                tokens = reply.get("tokens_predicted")
                if type(tokens) is not int or not 0 <= tokens <= CAPS[mode] + 8:
                    raise RuntimeError("Invalid native token count")
                visible = visible_answer(reply.get("content"))
                complete = stop in ("eos", "word") and not reply["truncated"]
                passed = complete and visible.rstrip(".!").casefold() == expected.casefold()
                rows.append(dict(case=case, mode=mode, profile=name, seed=call_seed,
                                 passed=passed, complete=complete, stop=stop,
                                 generated_tokens=tokens, elapsed_ms=elapsed))
    totals = {}
    for mode in CAPS:
        for profile in ("legacy", "qwen_" + mode):
            group = [x for x in rows if x["mode"] == mode and x["profile"] == profile]
            totals[mode + ":" + profile] = dict(
                correct=sum(x["passed"] for x in group),
                complete=sum(x["complete"] for x in group),
                attempted=len(group), elapsed_ms=sum(x["elapsed_ms"] for x in group),
                generated_tokens=sum(x["generated_tokens"] for x in group))
    return dict(status="COMPLETE_CPU_DIAGNOSTIC", seed=seed, model_sha256=MODEL_SHA256,
                context_tokens=CONTEXT, caps=CAPS,
                system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                totals=totals, cases=rows, official_ai_rating=None,
                scope="Six synthetic single-turn problems per mode; CPU server, not Android JNI or independent held-out review.")


def run(binary, seed):
    if not check_model(MODEL_PATH):
        raise RuntimeError("Unverified or unavailable Qwen3 1.7B GGUF")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    command = [str(binary.resolve()), "-m", str(MODEL_PATH), "--host", "127.0.0.1",
               "--port", str(PORT), "--ctx-size", str(CONTEXT), "--parallel", "1",
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
                    if request(opener, "/health", timeout=3).get("status") == "ok":
                        break
                except (OSError, ValueError):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("Native server startup timed out")
            result = evaluate(seed, lambda payload: request(opener, "/completion", payload))
            if not check_model(MODEL_PATH):
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--seed", type=int)
    args = parser.parse_args()
    seed = args.seed if args.seed is not None else secrets.randbelow(2**31 - 1)
    if not 0 <= seed < 2**31 - 1:
        parser.error("seed outside allowed range")
    try:
        print(json.dumps(run(args.binary, seed), indent=2, allow_nan=False))
        return 0
    except (OSError, ValueError, RuntimeError, KeyError, IndexError):
        print("Native CPU comparison incomplete; no score awarded.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
