"""Local Astra desktop entry point, using explicit request kinds and opt-in models.

No model is needed for calculation or reviewed evidence search. This entry
point is intentionally a small structured CLI, not an unrestricted chat UI.
"""
import argparse
from contextlib import ExitStack
import json
from pathlib import Path
import sys

from astra_evidence import Ledger
from astra_hybrid_evidence import HybridEvidence
from astra_local_answer import LocalAnswerRunner
from astra_local_finance import LocalProposer
from astra_orchestrator import Orchestrator
from astra_verified_finance import read_request


def execute_request(request, *, ledger_path=None, approve_models=False,
                    binary=None, primary=None, critic=None, timeout=180):
    if type(request) is not dict:
        raise ValueError("Structured request object required.")
    kind = request.get("kind")
    needs_model = kind == "answer" or (kind == "finance" and request.get("compare_models") is True)
    if not needs_model and (approve_models or any(x is not None for x in (binary, primary, critic))):
        raise ValueError("Model configuration not applicable to this task.")
    if needs_model and (approve_models is not True or not binary or not primary):
        raise ValueError("Explicit local model approval and paths required.")
    if request.get("use_vector") is True:
        raise ValueError("Use the Python embedding adapter interface for vector retrieval.")
    if kind == "answer" and request.get("review") is True and not critic:
        raise ValueError("Independent review requires a critic model.")
    if kind == "finance" and ledger_path is not None:
        raise ValueError("Finance calculation does not need an evidence database.")
    with ExitStack() as stack:
        ledger = None
        retrieval = None
        runner = None
        finance = None
        if kind in ("retrieve", "answer"):
            if ledger_path is None or not Path(ledger_path).is_file():
                raise ValueError("Existing local reviewed evidence database required.")
            ledger = stack.enter_context(Ledger(ledger_path))
            retrieval = HybridEvidence(ledger)
        if needs_model:
            if kind == "answer":
                runner = LocalAnswerRunner(binary, primary, critic, timeout=timeout, approved=True)
            else:
                if not critic:
                    raise ValueError("Independent finance comparison requires a critic.")
                try:
                    finance = LocalProposer(binary, primary, critic, timeout)
                except (ValueError, OSError, RuntimeError):
                    def finance(role, prompt):
                        raise RuntimeError("Local finance models unavailable.")
        return Orchestrator(ledger, retrieval, runner, finance).execute(
            request, approve_models=approve_models)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request")
    parser.add_argument("--ledger")
    parser.add_argument("--approve-models", action="store_true")
    parser.add_argument("--binary")
    parser.add_argument("--primary")
    parser.add_argument("--critic")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args(argv)
    try:
        output = execute_request(read_request(args.request), ledger_path=args.ledger,
                                 approve_models=args.approve_models, binary=args.binary,
                                 primary=args.primary, critic=args.critic, timeout=args.timeout)
    except (ValueError, OSError, RuntimeError, TypeError, RecursionError):
        print("Invalid or unavailable local task; no answer.", file=sys.stderr)
        return 1
    print(json.dumps(output, ensure_ascii=True, indent=2, allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
