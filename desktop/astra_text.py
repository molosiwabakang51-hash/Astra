"""Natural-language desktop entry point.

Reads one bounded UTF-8 request from stdin or a local file, so the user's
question need not appear in command-line arguments. No implicit model use.
"""
import argparse
import json
from pathlib import Path
import sys

from astra_desktop import execute_request
from astra_natural_language import route

MAX_INPUT_BYTES = 4096


def read_text(stream):
    raw = stream.read(MAX_INPUT_BYTES + 1)
    if len(raw) > MAX_INPUT_BYTES:
        raise ValueError("Request exceeds input limit.")
    return raw.decode("utf-8", errors="strict")


def execute(text, *, ledger_path=None, approve_models=False, review=False,
            binary=None, primary=None, critic=None, timeout=180):
    if type(approve_models) is not bool or type(review) is not bool:
        raise ValueError("Invalid approval or review setting.")
    if not approve_models and (review or any(x is not None for x in (binary, primary, critic))):
        raise ValueError("Model settings require explicit approval.")
    if review and not critic:
        raise ValueError("Independent review requires a critic.")
    routed = route(text, review=review)
    if routed.status != "routed":
        return {"status": routed.status, "clarification": routed.clarification}
    request = routed.request
    if request["kind"] == "finance":
        # Even an approved model configuration does not waste inference on
        # deterministic arithmetic.
        output = {"route":"finance", "result":execute_request(request)}
        if routed.currency:
            output["currency"] = routed.currency
        return output
    if request["kind"] == "retrieve" or not approve_models:
        request = {"kind":"retrieve","question":request["question"]}
        return {"route":"retrieve","result":execute_request(request,ledger_path=ledger_path)}
    return {"route":"answer","result":execute_request(request,ledger_path=ledger_path,
            approve_models=True,binary=binary,primary=primary,critic=critic,timeout=timeout)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-file")
    parser.add_argument("--ledger")
    parser.add_argument("--approve-models", action="store_true")
    parser.add_argument("--review", action="store_true")
    parser.add_argument("--binary")
    parser.add_argument("--primary")
    parser.add_argument("--critic")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args(argv)
    try:
        if args.input_file:
            with Path(args.input_file).open("rb") as stream:
                text = read_text(stream)
        else:
            text = read_text(sys.stdin.buffer)
        result = execute(text, ledger_path=args.ledger, approve_models=args.approve_models,
                         review=args.review,binary=args.binary,primary=args.primary,
                         critic=args.critic,timeout=args.timeout)
    except (ValueError, OSError, RuntimeError, TypeError, RecursionError):
        print("Invalid or unavailable local request; no answer.", file=sys.stderr)
        return 1
    print(json.dumps(result,ensure_ascii=True,indent=2,allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
