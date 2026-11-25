"""Generated synthetic transfer checks with exact answer keys.

A locally chosen 128-bit seed generates new numerical parameters and option
orders. Freeze model/runtime configuration before selecting the seed. The
templates are public; this is a transfer check, not an independent population
or a secret benchmark. No seed or answer key enters model prompts.
"""
from dataclasses import dataclass
from fractions import Fraction
import hashlib
import math
import random
import re
import time

from astra_symbiotic_eval import parse_answer, INSTRUCTION

VERSION = "generated-transfer-v1"
SEED = re.compile(r"[0-9a-f]{32}\Z")
LETTERS = "ABCD"


@dataclass(frozen=True)
class Case:
    id: str
    family: str
    text: str
    expected: str


def fraction_text(value):
    value = Fraction(value)
    return str(value.numerator) if value.denominator == 1 else str(value)


def generate(seed):
    if not isinstance(seed, str) or not SEED.fullmatch(seed):
        raise ValueError("Use a lowercase 128-bit hexadecimal seed.")
    rng = random.Random(int(seed, 16))
    cases = []

    def add(family, question, correct, alternatives, render=fraction_text):
        options = [Fraction(correct)]
        for value in alternatives:
            value = Fraction(value)
            if value not in options:
                options.append(value)
        if len(options) != 4:
            raise ValueError("Generated options are not distinct.")
        rng.shuffle(options)
        text = question + " " + " ".join(
            f"{letter}: {render(value)}" for letter, value in zip(LETTERS, options))
        cases.append(Case(f"g{len(cases)+1:02d}", family, text,
                          LETTERS[options.index(Fraction(correct))]))

    for _ in range(3):
        principal = rng.randrange(10, 101) * 100
        rate = rng.randrange(2, 15)
        years = rng.randrange(2, 7)
        correct = Fraction(principal * rate * years, 100)
        add("simple_interest",
            f"A loan of {principal} earns {rate}% simple annual interest for {years} years. Total interest only?",
            correct, (correct + 1, correct * 2, correct / 2))

        weight = rng.randrange(2, 9) * 10
        first, second = rng.randrange(-9, 0), rng.randrange(1, 15)
        correct = Fraction(weight*first + (100-weight)*second, 100)
        add("weighted_return",
            f"An asset with weight {weight}% returns {first}%; the other with weight {100-weight}% returns {second}%. Portfolio return in percent?",
            correct, (correct + 1, correct - 1, correct + 2))

        red, blue = rng.randrange(2, 9), rng.randrange(2, 9)
        total = red + blue
        correct = Fraction(red*(red-1), total*(total-1))
        add("conditional_probability",
            f"A bag contains {red} red balls and {blue} blue balls. Draw two without replacement. What is the exact probability both are red?",
            correct, (Fraction(red*red,total*total), Fraction(red,total),
                      Fraction(red-1,total-1)))

        base = rng.randrange(10, 100)
        rate = rng.randrange(2, 15)
        # Choose a future payment that yields an exact integer PV.
        present = base * 10000
        future = base * (100+rate)**2
        add("present_value",
            f"A payment of {future} arrives in two years. Annual discount rate {rate}%, compounded annually. What is its present value?",
            present, (present + base, present - base, future))

    rng.shuffle(cases)
    return tuple(cases)


def evaluate(seed, runner, *, approved=False, clock=time.monotonic):
    if approved is not True or not callable(runner):
        raise ValueError("Explicit local evaluation approval required.")
    cases = generate(seed)
    rows = []
    calls = 0

    def invoke(role, question):
        nonlocal calls
        calls += 1
        answer = parse_answer(runner(role, question))
        if answer is None:
            raise ValueError("Incomplete or malformed evaluation response.")
        return answer

    def arm(case, cooperate):
        started = clock()
        first = invoke("primary", case.text + INSTRUCTION)
        second = invoke("critic" if cooperate else "primary",
                        case.text + "\nSolve independently and check the calculation." + INSTRUCTION)
        final = invoke("primary", case.text + "\nCandidate letters: " + first + ", " + second
                       + ". Recalculate; either may be wrong." + INSTRUCTION)
        seconds = clock() - started
        if not math.isfinite(seconds) or seconds < 0:
            raise ValueError("Invalid measured duration.")
        return {"first_correct": first == case.expected, "final_correct": final == case.expected,
                "seconds": round(seconds, 3), "calls": 3}

    for index, case in enumerate(cases):
        row = {"id": case.id, "family": case.family}
        modes = ("self_review", "cooperation") if index % 2 == 0 else ("cooperation", "self_review")
        for mode in modes:
            row[mode] = arm(case, mode == "cooperation")
        rows.append(row)
    families = {}
    for family in sorted({row["family"] for row in rows}):
        group = [row for row in rows if row["family"] == family]
        families[family] = {
            "cases": len(group),
            "self_review_correct": sum(row["self_review"]["final_correct"] for row in group),
            "cooperation_correct": sum(row["cooperation"]["final_correct"] for row in group),
        }
    return {
        "suite": VERSION,
        "seed_commitment": hashlib.sha256(bytes.fromhex(seed)).hexdigest(),
        "cases": rows, "questions": len(rows), "model_calls": calls,
        "self_review_correct": sum(row["self_review"]["final_correct"] for row in rows),
        "cooperation_correct": sum(row["cooperation"]["final_correct"] for row in rows),
        "cooperation_wins": sum(row["cooperation"]["final_correct"] and not row["self_review"]["final_correct"] for row in rows),
        "cooperation_losses": sum(row["self_review"]["final_correct"] and not row["cooperation"]["final_correct"] for row in rows),
        "families": families,
        "deployment_allowed": False,
        "scope": "Generated synthetic transfer probes; public templates, equal calls not equal compute, no statistical significance or general intelligence claim.",
    }
