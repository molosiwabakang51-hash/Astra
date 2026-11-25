"""Budgeted local evidence-grounded question answering.

Fast mode permits only the primary model; balanced mode permits one optional
critic; deep mode adds bounded revision and comparison. Repeated questions share only a session-local latency profile.
This interface deliberately does not imply measured quality improvement.
"""
import argparse
import json
from pathlib import Path
import sys

from astra_compute_budget import BudgetedRunner, LatencyProfile, Policy
from astra_evidence import Ledger
from astra_hybrid_evidence import HybridEvidence
from astra_local_answer import LocalAnswerRunner
from astra_orchestrator import Orchestrator
from astra_recursive_revision import revise


def answer(question, ledger, retrieval, runner, policy, profile, *, approved=False):
    wrapped = BudgetedRunner(runner, policy, profile, approved=approved)
    if policy.mode == "deep":
        result = revise(question, ledger, retrieval, wrapped, approved=approved)
    else:
        result = Orchestrator(ledger, retrieval, wrapped).execute(
            {"kind": "answer", "question": question, "review": policy.mode == "balanced"},
            approve_models=approved)
    result["compute"] = wrapped.report()
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ledger", required=True)
    parser.add_argument("--binary", required=True)
    parser.add_argument("--primary", required=True)
    parser.add_argument("--critic")
    parser.add_argument("--mode", choices=("fast", "balanced", "deep"), default="fast")
    parser.add_argument("--budget", type=int, default=180)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--approve-models", action="store_true")
    args = parser.parse_args(argv)
    if not args.approve_models or (args.mode in ("balanced", "deep") and not args.critic):
        parser.error("Explicit approval required; balanced and deep modes require a distinct critic.")
    try:
        policy = Policy(args.mode, args.budget)
        if not Path(args.ledger).is_file():
            raise ValueError("Existing reviewed evidence ledger required.")
        with Ledger(args.ledger) as ledger:
            retrieval = HybridEvidence(ledger)
            runner = LocalAnswerRunner(args.binary, args.primary, args.critic,
                                       timeout=args.timeout, approved=True)
            profile = LatencyProfile()
            while True:
                line = sys.stdin.buffer.readline(4097)
                if not line:
                    return 0
                if len(line) > 4096:
                    raise ValueError("Input too large.")
                question = line.decode("utf-8", errors="strict").strip()
                if question.lower() in ("exit", "quit"):
                    return 0
                if not question:
                    continue
                result = answer(question, ledger, retrieval, runner, policy, profile, approved=True)
                print(json.dumps(result, ensure_ascii=True, allow_nan=False), flush=True)
    except (OSError, ValueError, RuntimeError, TypeError, RecursionError):
        print("Invalid or unavailable local adaptive answer request.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
