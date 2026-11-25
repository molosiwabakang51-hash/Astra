"""Bounded in-memory financial conversation state.

Only successful deterministic calculations establish an anchor. Explicit
follow-ups modify one field; comparisons use exact rational arithmetic.
Unrelated requests invalidate the anchor. No chat, model output or assumptions
are persisted to disk.
"""
from copy import deepcopy
from decimal import Decimal, localcontext
from fractions import Fraction
import re

from astra_evidence import clean, MAX_QUERY
from astra_natural_language import route, ratio, NUM, PERIOD, SUFFIX
from astra_verified_finance import adjudicate

TIME_OPERATIONS = {"present_value", "compound_growth"}
# Explicit forms are intentionally anchored. A percentage without a rate
# context is never interpreted as a change in portfolio allocation.
RATE_CHANGE = re.compile(
    r"(?:(?:what (?:happens )?if (?:the )?(?:interest |discount )?rates? "
    r"(?:increase|decrease|rise|fall|change|go) to|(?:change|set) (?:the )?"
    r"(?:interest |discount )?rate to|what if|what about|and at|at)\s*)("
    + NUM + r")\s*%" + SUFFIX, re.I)
PERIOD_CHANGE = re.compile(
    r"(?:what if|what about|(?:change|set) (?:the )?(?:period|term) to|"
    r"what if (?:the )?(?:period|term) (?:is|changes to))\s+"
    + PERIOD + r"\s+years?" + SUFFIX, re.I)
AMOUNT_CHANGE = re.compile(
    r"(?:change|set) (?:the )?(?:amount|principal) to\s+(" + NUM + r")" + SUFFIX, re.I)
COMPARE = re.compile(r"(?:please )?compare (?:both results|the (?:two |last two )?results|"
                     r"with (?:the )?(?:previous|original)(?: result)?)" + SUFFIX, re.I)
RESET = re.compile(r"(?:reset|clear) (?:the )?(?:calculation|scenario|finance context)" + SUFFIX, re.I)
FOLLOWUP_HINT = re.compile(
    r"^(?:what (?:happens )?if|what about|and at|at\s+[+-]?\d|"
    r"(?:change|set) (?:the )?(?:rate|interest rate|discount rate|period|term|amount|principal)|"
    r"compare (?:both|the results|the last|the two|with))\b", re.I)


def decimal_value(value):
    with localcontext() as ctx:
        ctx.prec = 32
        return str(Decimal(value.numerator) / Decimal(value.denominator))


def fraction_from_result(result):
    value = result["result"]
    return Fraction(int(value["numerator"]), int(value["denominator"]))


class FinanceSession:
    def __init__(self, fallback=None):
        self.fallback = fallback
        self.reset()

    def reset(self):
        self._current = None
        self._previous = None
        self._original = None

    def _clarify(self, message):
        return {"status": "clarification_required", "clarification": message}

    def _calculate(self, calculation, *, changed=None):
        # Commit state only after complete successful calculation.
        request = deepcopy(calculation)
        try:
            result = adjudicate(request)
        except ValueError:
            return self._clarify("The proposed values are outside the supported calculation domain.")
        snapshot = {"calculation": request, "finance": result}
        if changed is None:
            self._original = deepcopy(snapshot)
            self._previous = None
        else:
            self._previous = self._current
        self._current = snapshot
        return {"status": "calculated", "scenario": deepcopy(snapshot),
                "changed_field": changed,
                "scope": "Annual compounding under explicitly supplied assumptions." if
                request["operation"] in TIME_OPERATIONS else "Exact arithmetic under supplied assumptions."}

    def _modify(self, field, value):
        if self._current is None or self._current["calculation"]["operation"] not in TIME_OPERATIONS:
            return self._clarify("Start with an explicit present-value or compound-growth calculation.")
        request = deepcopy(self._current["calculation"])
        request["inputs"][field] = value
        return self._calculate(request, changed=field)

    def _compare(self, original=False):
        prior = self._original if original else self._previous
        if self._current is None or prior is None or self._previous is None:
            return self._clarify("Complete a calculation and at least one valid scenario change before comparing.")
        old = fraction_from_result(prior["finance"])
        new = fraction_from_result(self._current["finance"])
        change = new - old
        relative = change / old if old > 0 else None
        return {
            "status": "compared",
            "comparison_base": "original" if original else "previous",
            "before": deepcopy(prior),
            "after": deepcopy(self._current),
            "absolute_change": {"numerator": str(change.numerator),
                                "denominator": str(change.denominator),
                                "decimal_approximation": decimal_value(change)},
            "relative_change": None if relative is None else {
                "numerator": str(relative.numerator), "denominator": str(relative.denominator),
                "decimal_approximation": decimal_value(relative), "units": "decimal_ratio"},
            "direction": "higher" if change > 0 else "lower" if change < 0 else "unchanged",
            "scope": "Exact scenario comparison, not investment advice or a forecast.",
        }

    def execute(self, text):
        text = clean(text, MAX_QUERY, "question")
        normalized = " ".join(text.split())
        if RESET.fullmatch(normalized):
            self.reset()
            return {"status": "finance_context_cleared"}
        if COMPARE.fullmatch(normalized):
            return self._compare(original="original" in normalized.casefold())
        rate = RATE_CHANGE.fullmatch(normalized)
        if rate:
            return self._modify("rate", ratio(rate[1]))
        periods = PERIOD_CHANGE.fullmatch(normalized)
        if periods:
            return self._modify("periods", int(periods[1]))
        amount = AMOUNT_CHANGE.fullmatch(normalized)
        if amount:
            return self._modify("amount", amount[1])
        if FOLLOWUP_HINT.search(normalized):
            return self._clarify("Specify one new annual rate, term in years, or amount. No change has been applied.")
        routed = route(text)
        if routed.status == "clarification_required":
            return self._clarify(routed.clarification)
        if routed.request["kind"] == "finance":
            return self._calculate(routed.request["calculation"])
        # Never apply an old financial anchor after changing topic.
        self.reset()
        if self.fallback is not None:
            return {"status": "delegated", "result": self.fallback(text)}
        return {"status": "outside_finance_session", "route": routed.request["kind"]}
