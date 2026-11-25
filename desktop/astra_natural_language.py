"""Conservative English natural-language routing for Astra Desktop.

Deterministic calculation templates are anchored and validate all inputs.
Unrecognised text becomes a reviewed-evidence question, not an executable
command. Ambiguous calculation-like requests never invoke a guessed formula.
"""
from dataclasses import dataclass
from decimal import Decimal
import re

from astra_evidence import clean, MAX_QUERY
from astra_verified_finance import validated_request

NUM = r"[+-]?[0-9]{1,12}(?:\.[0-9]{1,8})?"
RATE = r"(" + NUM + r")\s*%"
AMOUNT = r"(" + NUM + r")"
PERIOD = r"([0-9]{1,3})"
PREFIX = r"(?:please\s+)?(?:(?:calculate|compute|find|what is|what's)\s+(?:the\s+)?)?"
SUFFIX = r"\s*[?.!]?"
CHANGE = re.compile(PREFIX + r"percentage change from\s+" + AMOUNT + r"\s+to\s+" + AMOUNT + SUFFIX, re.I)
TIME_VALUE = re.compile(PREFIX + r"(present value|future value|compound growth)\s+of\s+" + AMOUNT +
                        r"\s+at\s+" + RATE + r"\s+(?:annually\s+)?(?:for|over)\s+" + PERIOD +
                        r"\s+years?" + SUFFIX, re.I)
WEIGHTED_PREFIX = re.compile(PREFIX + r"weighted (?:portfolio )?return\s*(?:of|:)\s*", re.I)
PAIR = re.compile(RATE + r"\s+(?:invested\s+)?(?:at|returning|earning)\s+" + RATE, re.I)
RETRIEVE = re.compile(r"^(?:please\s+)?(?:search|find|retrieve|look up)\s+(?:my\s+)?(?:notes|evidence|knowledge|references)\s+(?:on|about|for)\s+(.+)$", re.I)
CALC_HINT = re.compile(r"^(?:please\s+)?(?:calculate|compute|solve)\b|\b(?:present value|future value|compound growth|weighted (?:portfolio )?return|percentage change)\b", re.I)
DEFINITION = re.compile(r"^(?:please\s+)?(?:explain|define|describe|teach me|what is meant by|what does)\b|^what is (?:a|an|the meaning of)\b", re.I)


@dataclass(frozen=True)
class Route:
    status: str
    request: dict | None = None
    clarification: str | None = None


def ratio(percent):
    value = Decimal(percent) / Decimal(100)
    return format(value, "f")


def finance(operation, inputs):
    request = {"operation": operation, "inputs": inputs}
    try:
        validated_request(request)
    except ValueError:
        return Route("clarification_required", clarification="The supplied values are outside the supported calculation domain.")
    return Route("routed", {"kind": "finance", "calculation": request})


def route(text, *, review=False, use_vector=False):
    text = clean(text, MAX_QUERY, "question")
    if type(review) is not bool or type(use_vector) is not bool:
        raise ValueError("Invalid routing options.")
    # Collapse whitespace only for matching; preserve original text for search.
    normalized = " ".join(text.split())
    match = CHANGE.fullmatch(normalized)
    if match:
        return finance("percentage_change", {"initial": match[1], "final": match[2]})
    match = TIME_VALUE.fullmatch(normalized)
    if match:
        op = "present_value" if match[1].lower() == "present value" else "compound_growth"
        return finance(op, {"amount": match[2], "rate": ratio(match[3]), "periods": int(match[4])})
    prefix = WEIGHTED_PREFIX.match(normalized)
    if prefix:
        remainder = normalized[prefix.end():].rstrip("?.!").strip()
        pieces = re.split(r"\s*,\s*|\s*;\s*|\s+and\s+", remainder, flags=re.I)
        pairs = [PAIR.fullmatch(piece) for piece in pieces]
        if 1 <= len(pairs) <= 20 and all(pairs):
            return finance("weighted_return", {"weights": [ratio(p[1]) for p in pairs],
                                                "returns": [ratio(p[2]) for p in pairs]})
        return Route("clarification_required", clarification="Specify each allocation and return, such as 30% at 10% and 70% at 5%.")
    match = RETRIEVE.fullmatch(normalized)
    if match:
        return Route("routed", {"kind": "retrieve", "question": match[1], "use_vector": use_vector})
    if CALC_HINT.search(normalized) and not DEFINITION.search(normalized):
        return Route("clarification_required", clarification="Specify the calculation and its inputs unambiguously. Supported calculations are percentage change, annual compound growth, present value and weighted returns.")
    return Route("routed", {"kind": "answer", "question": text, "review": review, "use_vector": use_vector})


def execute_text(orchestrator, text, *, approve_models=False, approve_embeddings=False,
                 review=False, use_vector=False, embed=None, model_sha=None):
    routed = route(text, review=review, use_vector=use_vector)
    if routed.status != "routed":
        return {"status": routed.status, "clarification": routed.clarification}
    # Without model consent return reviewed evidence, never silently run AI.
    request = routed.request
    if request["kind"] == "answer" and approve_models is not True:
        request = {"kind": "retrieve", "question": request["question"], "use_vector": use_vector}
    result = orchestrator.execute(request, approve_models=approve_models,
                                  approve_embeddings=approve_embeddings, embed=embed,
                                  model_sha=model_sha)
    return {"route": request["kind"], "result": result}
