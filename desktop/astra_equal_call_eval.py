"""Fixed synthetic, equal-call comparison of self-review and model cooperation.

Each arm receives three bounded sequential inference calls per question.
Both candidate letters are supplied to the same primary adjudicator. No
answer key enters generation prompts. Equal calls are not equal FLOPs.
"""
from dataclasses import dataclass
import time
import math
from astra_symbiotic_eval import parse_answer, INSTRUCTION

@dataclass(frozen=True)
class Item:
    id: str
    text: str
    expected: str

ITEMS = (
    Item("m01", "A loan of 2000 earns 5% simple annual interest for three years. Total interest? A: 100 B: 300 C: 315.25 D: 2300", "B"),
    Item("m02", "A price rises 25% and then falls 20%. Relative to the original it is: A: 5% higher B: 5% lower C: unchanged D: 10% higher", "C"),
    Item("m03", "Two independent events have probabilities 0.4 and 0.5. Probability both occur? A: 0.9 B: 0.1 C: 0.45 D: 0.2", "D"),
    Item("m04", "All Z are Y, and no Y are X. Can any Z be X? A: No B: Yes C: Only some D: Not determined", "A"),
    Item("m05", "A bond pays 110 in one year. Annual discount rate is 10%. Present value? A: 99 B: 100 C: 110 D: 121", "B"),
    Item("m06", "An account earns 10% then loses 10% of its new balance. Net change? A: 0% B: +1% C: -1% D: -10%", "C"),
    Item("m07", "Exactly one of three equally likely choices wins. You select two different choices. Probability of selecting the winner? A: 1/3 B: 1/2 C: 1 D: 2/3", "D"),
    Item("m08", "If P then Q. Q is true. Is P necessarily true? A: No B: Yes C: P must be false D: Q must be false", "A"),
    Item("m09", "A portfolio is 40% in an asset returning -5% and 60% in one returning 10%. Portfolio return? A: 5% B: 4% C: 2% D: 7%", "B"),
    Item("m10", "A bag contains 2 red and 3 blue balls. Two are drawn without replacement. Probability both red? A: 0.16 B: 0.2 C: 0.1 D: 0.4", "C"),
    Item("m11", "Three consecutive integers sum to 42. Largest? A: 13 B: 14 C: 16 D: 15", "D"),
    Item("m12", "A payment of 121 is received in two years. Annual compounding discount rate is 10%. Present value? A: 100 B: 110 C: 121 D: 99", "A"),
)


def evaluate(runner, *, approved=False, clock=time.monotonic):
    if approved is not True or not callable(runner):
        raise ValueError("Explicit local benchmark approval required.")
    def run(role, prompt):
        answer = parse_answer(runner(role, prompt))
        if answer is None:
            raise ValueError("Incomplete or malformed benchmark answer.")
        return answer
    def arm(item, cooperative):
        started = clock()
        first = run("primary", item.text + INSTRUCTION)
        second = run("critic" if cooperative else "primary",
                     item.text + "\nSolve independently, checking your reasoning." + INSTRUCTION)
        final = run("primary", item.text + "\nCandidate letters: " + first + ", " + second
                    + ". Recalculate independently; either candidate can be wrong." + INSTRUCTION)
        elapsed = clock() - started
        if not math.isfinite(elapsed) or elapsed < 0:
            raise ValueError("Invalid benchmark timing.")
        return {"first_correct": first == item.expected, "final_correct": final == item.expected,
                "seconds": round(elapsed, 3), "calls": 3}
    rows = []
    for i, item in enumerate(ITEMS):
        # Alternate which arm runs first to reduce simple warm-up order bias.
        order = ("self_review", "cooperation") if i % 2 == 0 else ("cooperation", "self_review")
        row = {"id": item.id}
        for name in order:
            row[name] = arm(item, name == "cooperation")
        rows.append(row)
    return {
        "cases": rows, "questions": len(rows),
        "self_review_correct": sum(r["self_review"]["final_correct"] for r in rows),
        "cooperation_correct": sum(r["cooperation"]["final_correct"] for r in rows),
        "cooperation_wins": sum(r["cooperation"]["final_correct"] and not r["self_review"]["final_correct"] for r in rows),
        "cooperation_losses": sum(r["self_review"]["final_correct"] and not r["cooperation"]["final_correct"] for r in rows),
        "self_review_calls": 3 * len(rows), "cooperation_calls": 3 * len(rows),
        "self_review_seconds": round(sum(r["self_review"]["seconds"] for r in rows), 3),
        "cooperation_seconds": round(sum(r["cooperation"]["seconds"] for r in rows), 3),
        "scope": "Fixed synthetic multiple-choice development probes. Equal call counts do not establish equal compute, general intelligence, or statistical significance.",
    }


def main(argv=None):
    import argparse
    import json
    import sys
    from astra_local_answer import LocalAnswerRunner
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary")
    parser.add_argument("primary")
    parser.add_argument("critic")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--approve-models", action="store_true")
    args = parser.parse_args(argv)
    try:
        runner = LocalAnswerRunner(args.binary, args.primary, args.critic,
                                   timeout=args.timeout, approved=args.approve_models)
        result = evaluate(runner, approved=args.approve_models)
        print(json.dumps(result, indent=2, allow_nan=False))
        return 0
    except (OSError, ValueError, RuntimeError, RecursionError):
        print("Benchmark incomplete; no score.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    import sys
    sys.exit(main())
