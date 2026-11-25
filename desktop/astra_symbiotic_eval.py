"""Sequential two-model cooperation experiment using fixed synthetic questions.

The critic solves independently, then the primary adjudicates disagreements.
Only validated candidate letters cross model boundaries. Ground truth is used
after generation, never in model prompts or decision logic. No private inputs.
"""
import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

from astra_resource_plan import model_file, plan, probe

@dataclass(frozen=True)
class Question:
    id: str
    text: str
    expected: str

QUESTIONS = (
    Question("weighted_return", "A portfolio holds 30% in assets returning 10% and 70% returning 5%. What is its return? A: 6.5% B: 7.5% C: 5% D: 15%", "A"),
    Question("deduction", "Every analyst is a reader. No reader is a robot. Can an analyst be a robot under these rules? A: Yes B: No C: Sometimes D: Not enough information", "B"),
    Question("probability", "A bag has 4 red and 6 blue balls. Two are drawn without replacement. Probability both red? A: 16% B: 40% C: 13.33% D: 26.67%", "C"),
    Question("discounting", "A payment of 121 arrives in two years at a 10% annual discount rate. Present value? A: 110 B: 99 C: 121 D: 100", "D"),
    Question("conditional", "If a project is approved then it is audited. A project was not audited. What follows? A: It was approved B: It was not approved C: It was audited D: Nothing follows", "B"),
    Question("arithmetic", "A price falls 20% then rises 25%. Relative to the starting price, it is: A: unchanged B: 5% higher C: 5% lower D: 25% higher", "A"),
)
INSTRUCTION = "\nReturn a single line containing FINAL: followed by one option letter A, B, C or D. No explanation."
MAX_OUTPUT = 65536

def parse_answer(raw):
    if not isinstance(raw, str) or len(raw) > MAX_OUTPUT:
        return None
    # Refuse thought-containing streams or ambiguous duplicate answer markers.
    if re.search(r"</?think\b", raw, re.I):
        return None
    markers = re.findall(r"(?im)^[ \t]*FINAL:[ \t]*([A-D])[ \t]*\r?$", raw)
    if len(markers) != 1 or len(re.findall(r"(?im)^[ \t]*FINAL:", raw)) != 1:
        return None
    return markers[0].upper()

def identity(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def generate(binary, model, prompt, timeout):
    # This low-level runner is only used by the fixed synthetic suite.
    # Never pass owner chats through command-line arguments.
    name, size = model_file(model)
    admission = plan(probe(Path(model).parent), [(name, size)], context=1024)
    if not admission["all_admitted_individually"]:
        raise RuntimeError("Model admission refused.")
    command = [str(binary), "-m", str(model), "-p", prompt,
               "--conversation", "--single-turn", "--no-display-prompt",
               "--simple-io", "--color", "off", "--no-show-timings",
               "--reasoning", "off", "--cache-ram", "0", "--no-context-shift",
               "-c", "1024", "-n", "32", "-ngl", "0",
               "-t", str(admission["threads"]), "--temp", "0", "--seed", "42"]
    # Inherited LLAMA_ARG_* environment variables can override local settings.
    # Exclude all model/runtime-specific environment overrides.
    environment = {k: v for k, v in os.environ.items() if not k.upper().startswith("LLAMA_")}
    result = subprocess.run(command, stdin=subprocess.DEVNULL,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                            shell=False, timeout=timeout, check=False, env=environment)
    if result.returncode or len(result.stdout) > MAX_OUTPUT:
        raise RuntimeError("Local generation failed.")
    answer = parse_answer(result.stdout.decode("utf-8", errors="strict"))
    if answer is None:
        raise RuntimeError("No unambiguous synthetic final answer.")
    return answer

def evaluate(run, questions=QUESTIONS):
    rows = []
    for question in questions:
        started = time.monotonic()
        first = run("primary", question.text + INSTRUCTION)
        baseline_seconds = time.monotonic() - started
        second = run("critic", question.text + INSTRUCTION)
        if first not in ("A", "B", "C", "D") or second not in ("A", "B", "C", "D"):
            raise RuntimeError("Invalid candidate; no completed comparison.")
        if first == second:
            final = first
            passes = 2
        else:
            # Candidate letters are the sole shared working memory.
            # Do not pass generated prose, tool instructions or reference keys.
            arbitration = (question.text + "\nTwo independent candidate options: "
                           + first + " and " + second
                           + ". Solve the question again and choose the best option; either candidate may be wrong."
                           + INSTRUCTION)
            final = run("primary", arbitration)
            passes = 3
        if final not in ("A", "B", "C", "D"):
            raise RuntimeError("Invalid adjudication; no completed comparison.")
        rows.append({"id": question.id, "primary_correct": first == question.expected,
                     "critic_correct": second == question.expected,
                     "combined_correct": final == question.expected,
                     "agreement": first == second, "model_calls": passes,
                     "baseline_seconds": round(baseline_seconds, 3),
                     "combined_seconds": round(time.monotonic() - started, 3)})
    return rows

def report(rows):
    if len(rows) != len(QUESTIONS) or {r["id"] for r in rows} != {q.id for q in QUESTIONS}:
        raise ValueError("Incomplete or duplicated synthetic suite.")
    return {"primary_correct": sum(r["primary_correct"] for r in rows),
            "critic_correct": sum(r["critic_correct"] for r in rows),
            "combined_correct": sum(r["combined_correct"] for r in rows),
            "questions": len(rows),
            "model_calls": sum(r["model_calls"] for r in rows),
            "baseline_seconds": round(sum(r["baseline_seconds"] for r in rows), 3),
            "combined_seconds": round(sum(r["combined_seconds"] for r in rows), 3),
            "cases": rows,
            "note": "Small fixed multiple-choice experiment; not a general intelligence rating or proof of synergy."}

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary")
    parser.add_argument("primary")
    parser.add_argument("critic")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args(argv)
    try:
        binary, primary, critic = (Path(x).resolve() for x in (args.binary, args.primary, args.critic))
        if not binary.is_file() or binary.name.lower() not in ("llama-cli", "llama-cli.exe"):
            raise ValueError("Trusted local llama-cli executable required.")
        if not 30 <= args.timeout <= 300:
            raise ValueError("Timeout out of bounds.")
        model_file(primary)
        model_file(critic)
        if primary == critic or identity(primary) == identity(critic):
            raise ValueError("Distinct model weights required.")
        models = {"primary": primary, "critic": critic}
        rows = evaluate(lambda role, prompt: generate(binary, models[role], prompt, args.timeout))
        result = report(rows)
        result["primary_model"] = primary.name
        result["critic_model"] = critic.name
        print(json.dumps(result, indent=2))
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired):
        print("Evaluation incomplete or unsupported; no score.", file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
