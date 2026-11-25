"""Interactive local session for bounded financial follow-ups.

Questions and scenario state are not saved. Other requests are delegated
to the existing text interface with separately approved runtime settings.
"""
import argparse
import json
import sys
from astra_finance_session import FinanceSession
from astra_text import execute, MAX_INPUT_BYTES


def interact(session, source, output, errors):
    while True:
        raw = source.readline(MAX_INPUT_BYTES + 1)
        if not raw:
            return 0
        if len(raw) > MAX_INPUT_BYTES:
            # Exit rather than interpreting the remaining bytes as another turn.
            errors.write("Input exceeds session limit; session ended.\n")
            session.reset()
            return 1
        try:
            text = raw.decode("utf-8").strip()
            if text.casefold() in ("quit", "exit"):
                session.reset()
                return 0
            if not text:
                continue
            result = session.execute(text)
            output.write(json.dumps(result, ensure_ascii=True, allow_nan=False) + "\n")
            output.flush()
        except (ValueError, OSError, RuntimeError, TypeError, RecursionError):
            errors.write("Invalid or unavailable local request; no answer.\n")
            errors.flush()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ledger")
    parser.add_argument("--approve-models", action="store_true")
    parser.add_argument("--review", action="store_true")
    parser.add_argument("--binary")
    parser.add_argument("--primary")
    parser.add_argument("--critic")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args(argv)
    if (not args.approve_models and (args.review or any(x is not None for x in
            (args.binary, args.primary, args.critic)))) or (args.review and not args.critic):
        parser.error("Model settings require explicit approval and a critic for review.")
    session = FinanceSession(lambda text: execute(
        text, ledger_path=args.ledger, approve_models=args.approve_models,
        review=args.review, binary=args.binary, primary=args.primary,
        critic=args.critic, timeout=args.timeout))
    try:
        return interact(session, sys.stdin.buffer, sys.stdout, sys.stderr)
    finally:
        session.reset()


if __name__ == "__main__":
    sys.exit(main())
