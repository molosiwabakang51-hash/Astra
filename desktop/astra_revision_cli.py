"""Run the bounded evidence-grounded revision pilot on trusted local models."""
import argparse
import json
from pathlib import Path
import sys

from astra_evidence import Ledger
from astra_hybrid_evidence import HybridEvidence
from astra_local_answer import LocalAnswerRunner
from astra_recursive_revision import revise


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--question-file", required=True)
    parser.add_argument("--ledger", required=True)
    parser.add_argument("--binary", required=True)
    parser.add_argument("--primary", required=True)
    parser.add_argument("--critic", required=True)
    parser.add_argument("--approve-models", action="store_true")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args(argv)
    if not args.approve_models:
        parser.error("Explicit local model approval required.")
    try:
        if not Path(args.ledger).is_file():
            raise ValueError("Existing reviewed evidence ledger required.")
        with Path(args.question_file).open("rb") as source:
            raw = source.read(4097)
        if len(raw) > 4096:
            raise ValueError("Question exceeds input limit.")
        question = raw.decode("utf-8", errors="strict")
        with Ledger(args.ledger) as ledger:
            runner = LocalAnswerRunner(args.binary, args.primary, args.critic,
                                       timeout=args.timeout, approved=True)
            result = revise(question, ledger, HybridEvidence(ledger), runner, approved=True)
        print(json.dumps(result, ensure_ascii=True, indent=2, allow_nan=False))
        return 0
    except (OSError, ValueError, RuntimeError, TypeError, RecursionError):
        print("Invalid or unavailable revision request; no draft.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
