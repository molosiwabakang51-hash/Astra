"""Exact, bounded quantitative verification for structured Astra tasks.

No model text is executed. Only explicit supported operations and decimal
strings are accepted. A result verifies arithmetic under the given inputs,
not whether the assumptions or interpretation are correct.
"""
from fractions import Fraction
from decimal import Decimal, localcontext
import re

NUMBER = re.compile(r"[+-]?[0-9]{1,18}(?:\.[0-9]{1,12})?\Z")
MAX_ITEMS = 50

def number(value):
    if not isinstance(value, str) or not NUMBER.fullmatch(value):
        raise ValueError("Expected bounded decimal string.")
    return Fraction(value)

def integer(value):
    if type(value) is not int or not 0 <= value <= 100:
        raise ValueError("Expected integer period count from 0 to 100.")
    return value

def exact_result(operation, inputs):
    if not isinstance(inputs, dict):
        raise ValueError("Expected structured inputs.")
    if operation == "weighted_return":
        if set(inputs) != {"weights", "returns"}:
            raise ValueError("Unexpected weighted-return inputs.")
        weights, returns = inputs["weights"], inputs["returns"]
        if not isinstance(weights, list) or not isinstance(returns, list) or not 1 <= len(weights) == len(returns) <= MAX_ITEMS:
            raise ValueError("Expected equal-length bounded lists.")
        w = [number(x) for x in weights]
        r = [number(x) for x in returns]
        if any(x < 0 for x in w) or sum(w) != 1:
            raise ValueError("Weights must be nonnegative and sum to one.")
        return sum((a*b for a,b in zip(w,r)), Fraction(0))
    if operation in ("present_value", "compound_growth"):
        if set(inputs) != {"amount", "rate", "periods"}:
            raise ValueError("Unexpected time-value inputs.")
        amount, rate = number(inputs["amount"]), number(inputs["rate"])
        periods = integer(inputs["periods"])
        if rate <= -1 or rate > 10:
            raise ValueError("Rate outside bounded domain.")
        factor = (1 + rate) ** periods
        return amount / factor if operation == "present_value" else amount * factor
    if operation == "percentage_change":
        if set(inputs) != {"initial", "final"}:
            raise ValueError("Unexpected percentage-change inputs.")
        initial, final = number(inputs["initial"]), number(inputs["final"])
        if initial <= 0:
            raise ValueError("Positive starting value required.")
        return (final - initial) / initial
    raise ValueError("Unsupported operation.")

def verify(operation, inputs, candidate, tolerance="0"):
    result = exact_result(operation, inputs)
    proposed, allowed = number(candidate), number(tolerance)
    if not 0 <= allowed <= Fraction(1, 100):
        raise ValueError("Tolerance must be between zero and 0.01 in output units.")
    with localcontext() as ctx:
        ctx.prec = 32
        approximation = str(Decimal(result.numerator) / Decimal(result.denominator))
    return {
        "operation": operation,
        "status": "matches" if abs(result - proposed) <= allowed else "mismatch",
        "exact_numerator": str(result.numerator),
        "exact_denominator": str(result.denominator),
        "decimal_approximation": approximation,
        "tolerance": tolerance,
        "units": "currency_amount" if operation in ("present_value", "compound_growth") else "decimal_ratio",
        "scope": "arithmetic_only_not_input_or_factual_validation",
    }
