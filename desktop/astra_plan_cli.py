"""Local task planning and execution with explicit, digest-bound approval."""
import argparse
from contextlib import ExitStack
import json
from pathlib import Path
import sys

from astra_evidence import Ledger
from astra_hybrid_evidence import HybridEvidence
from astra_local_answer import LocalAnswerRunner
from astra_orchestrator import Orchestrator
from astra_task_plan import propose_plan, execute_plan, validate_plan, approval_digest
from astra_verified_finance import read_request


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("propose", "inspect", "execute"))
    parser.add_argument("request", help="Local JSON file containing objective or exact plan.")
    parser.add_argument("--approved-digest")
    parser.add_argument("--ledger")
    parser.add_argument("--approve-models", action="store_true")
    parser.add_argument("--synthesize", action="store_true")
    parser.add_argument("--binary")
    parser.add_argument("--primary")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args(argv)
    try:
        raw = read_request(args.request)
        if args.mode == "inspect":
            if args.approved_digest or args.ledger or args.approve_models or args.synthesize or args.binary or args.primary:
                raise ValueError("Inspection is read-only and does not need runtime options.")
            plan = validate_plan(raw)
            output = {"status": "awaiting_explicit_plan_approval", "plan": plan,
                      "approval_digest": approval_digest(plan)}
        else:
            needs_model = args.mode == "propose" or args.synthesize
            if needs_model:
                if not args.approve_models or not args.binary or not args.primary:
                    raise ValueError("Explicit local model approval and paths required.")
            elif args.approve_models or args.binary or args.primary:
                raise ValueError("Model settings are not applicable.")
            if args.mode == "propose":
                if args.approved_digest or args.ledger or args.synthesize:
                    raise ValueError("Proposal mode cannot execute or synthesize a plan.")
                if type(raw) is not dict or set(raw) != {"objective"}:
                    raise ValueError("Objective-only proposal request required.")
                runner = LocalAnswerRunner(args.binary, args.primary,
                                           timeout=args.timeout, approved=True)
                output = propose_plan(raw["objective"], runner, approved=True)
            else:
                # Reject altered or unapproved plans before opening a database
                # or configuring the optional local model runner.
                plan = validate_plan(raw)
                if args.approved_digest != approval_digest(plan):
                    raise ValueError("Exact plan approval digest required.")
                retrieval_needed = any(step["kind"] == "retrieve" for step in plan["steps"])
                if retrieval_needed and (not args.ledger or not Path(args.ledger).is_file()):
                    raise ValueError("Existing reviewed evidence ledger required.")
                if not retrieval_needed and args.ledger:
                    raise ValueError("Ledger not needed for finance-only plan.")
                with ExitStack() as stack:
                    ledger = stack.enter_context(Ledger(args.ledger)) if retrieval_needed else None
                    retrieval = HybridEvidence(ledger) if ledger is not None else None
                    runner = LocalAnswerRunner(args.binary, args.primary, timeout=args.timeout,
                                               approved=True) if needs_model else None
                    output = execute_plan(plan, Orchestrator(ledger, retrieval, runner),
                                          approved_digest=args.approved_digest,
                                          synthesize=args.synthesize, approve_models=args.approve_models)
        print(json.dumps(output, ensure_ascii=True, indent=2, allow_nan=False))
        return 0
    except (OSError, ValueError, RuntimeError, TypeError, RecursionError):
        print("Invalid, unapproved or unavailable local plan; no execution result.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
