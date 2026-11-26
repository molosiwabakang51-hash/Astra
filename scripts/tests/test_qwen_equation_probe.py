import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_equation_probe as probe


class EquationProbeTests(unittest.TestCase):
    def test_fresh_suite_is_reproducible_and_balanced(self):
        suite = probe.cases(42)
        self.assertEqual(suite, probe.cases(42))
        self.assertNotEqual(suite, probe.cases(43))
        self.assertEqual(len({name for name, _, _ in suite}), 11)
        self.assertEqual(sum(expected is not None for _, _, expected in suite), 8)

    def test_matched_comparison_does_not_reward_unsafe_answers(self):
        suite = [("inverse", "Question 5 and 10", "5"),
                 ("missing", "Question 10", None)]
        calls, checked = [], []
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos", truncated=False,
                        tokens_predicted=7, content="EQ: 5*x=10" if "Plan " in payload["prompt"] else "5")
        def invoke(operation, question, proposal=None):
            if operation == "plan":
                return "Plan " + question
            checked.append((question, proposal))
            return "5"
        result = probe.evaluate(infer, invoke, suite, seed=42)
        self.assertEqual(len(calls), 4)
        self.assertEqual(len(checked), 2)
        for a, b in zip(calls[::2], calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k != "prompt"},
                             {k:v for k,v in b.items() if k != "prompt"})
        self.assertEqual(result["matched"], dict(both=1, unassisted_only=0, equation_only=0, neither=0))
        for arm in result["totals"].values():
            self.assertEqual(arm["correct"], 1)
            self.assertEqual(arm["unsuitable_numeric"], 1)
        self.assertIsNone(result["official_ai_rating"])

    def test_truncated_proposals_never_reach_solver(self):
        def infer(payload):
            return dict(generation_settings=payload, stop_type="limit", truncated=True,
                        tokens_predicted=768, content="EQ: 5*x=10")
        def invoke(operation, question, proposal=None):
            self.assertEqual(operation, "plan")
            return question
        result = probe.evaluate(infer, invoke, [("inverse", "Question", "2")], seed=42)
        self.assertEqual(result["matched"]["neither"], 1)
        self.assertEqual(result["totals"]["equation"]["complete"], 0)


if __name__ == "__main__":
    unittest.main()
