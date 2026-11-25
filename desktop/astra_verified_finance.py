"""Bounded finance adjudication: model proposals are not mathematical authority.

Accept explicit structured inputs. Exact arithmetic determines the result;
optional independent numerical proposals are evaluated against it. No model
text becomes an instruction, operation, input value, or persistent fact.
"""
import argparse
import json
from pathlib import Path
import sys
from decimal import Decimal, localcontext

from astra_quantitative_verify import exact_result, number, verify

MAX_REQUEST_BYTES = 32768
ROLES = ("primary", "critic")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON field.")
        result[key] = value
    return result


def invalid_constant(value):
    raise ValueError("Non-finite JSON constant.")


def read_request(path):
    with Path(path).open("rb") as source:
        raw = source.read(MAX_REQUEST_BYTES + 1)
    if len(raw) > MAX_REQUEST_BYTES:
        raise ValueError("Request exceeds the local size limit.")
    return json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object,
                      parse_constant=invalid_constant)


def validated_request(request):
    if type(request) is not dict:
        raise ValueError("Request must be a JSON object.")
    if not {"operation", "inputs"} <= request.keys() or not request.keys() <= {
        "operation", "inputs", "tolerance", "candidates"
    }:
        raise ValueError("Unexpected or missing request fields.")
    operation = request["operation"]
    if not isinstance(operation, str):
        raise ValueError("Operation must be a supported name.")
    result = exact_result(operation, request["inputs"])
    tolerance = request.get("tolerance", "0")
    allowed = number(tolerance)
    if not 0 <= allowed <= number("0.01"):
        raise ValueError("Tolerance outside allowed domain.")
    candidates = request.get("candidates", {})
    if type(candidates) is not dict or not candidates.keys() <= set(ROLES):
        raise ValueError("Only primary and critic proposals are accepted.")
    return operation, result, tolerance, candidates


def adjudicate(request):
    operation, exact, tolerance, candidates = validated_request(request)
    outcomes = {}
    for role in ROLES:
        if role not in candidates:
            outcomes[role] = "not_supplied"
            continue
        try:
            result = verify(operation, request["inputs"], candidates[role], tolerance)
            outcomes[role] = result["status"]
        except ValueError:
            # Never return rejected arbitrary model text or attempt to fix it
            # by extracting a convenient number from generated prose.
            outcomes[role] = "invalid_proposal"

    if all(outcomes[r] == "not_supplied" for r in ROLES):
        consensus = "no_model_proposals"
    elif all(outcomes[r] in ("matches", "mismatch") for r in ROLES):
        consensus = ("same_numeric_proposal" if number(candidates["primary"]) ==
                     number(candidates["critic"]) else "different_numeric_proposals")
    else:
        consensus = "incomplete_model_comparison"

    with localcontext() as ctx:
        ctx.prec = 32
        decimal = str(Decimal(exact.numerator) / Decimal(exact.denominator))
    units = ("currency_amount" if operation in ("present_value", "compound_growth")
             else "decimal_ratio")
    return {
        "operation": operation,
        "authority": "deterministic_exact_arithmetic",
        "result": {
            "numerator": str(exact.numerator),
            "denominator": str(exact.denominator),
            "decimal_approximation": decimal,
            "units": units,
        },
        "proposal_assessment": outcomes,
        "proposal_agreement": consensus,
        "tolerance": tolerance,
        "scope": "Arithmetic under supplied assumptions only; inputs and interpretation are not independently verified.",
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request", help="Path to a local structured JSON request.")
    args = parser.parse_args(argv)
    try:
        result = adjudicate(read_request(args.request))
    except (OSError, ValueError, TypeError, RecursionError):
        print("Invalid or unreadable structured finance request; no result.", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
