"""Synthetic evaluator tests: not actual Qwen results."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_calculator_probe as probe


class CalculatorProbeTests(unittest.TestCase):
    def test_complete_denominators_and_no_inference_credit_for_rejection(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="NONE")
        def invoke(operation, question, proposal=None):
            return question if operation == "plan" else None
        result = probe.evaluate(fake, invoke)
        self.assertEqual(len(calls), 12)
        self.assertEqual(result["eligible"], 8)
        self.assertEqual(result["unsuitable"], 4)
        self.assertEqual(result["correct_values"], 0)
        self.assertEqual(result["strict_synthesis"], 0)
        self.assertEqual(result["unsuitable_explicit_none"], 4)
        self.assertIsNone(result["official_ai_rating"])

    def test_wrong_calculation_cannot_receive_synthesis_credit(self):
        def fake(payload):
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="100")
        def invoke(operation, question, proposal=None):
            return question if operation == "plan" else ("999", "synthetic synthesis")
        result = probe.evaluate(fake, invoke)
        self.assertEqual(result["correct_values"], 0)
        self.assertEqual(result["strict_synthesis"], 0)
        self.assertEqual(result["unsuitable_rejected"], 0)
        self.assertTrue(all(r["synthesis_attempted"] for r in result["cases"]))

    def test_incomplete_plan_never_reaches_calculator(self):
        def fake(payload):
            return dict(generation_settings=payload, stop_type="limit",
                        truncated=False, tokens_predicted=128, content="CALC: 2+2")
        def invoke(operation, question, proposal=None):
            self.assertEqual(operation, "plan")
            return question
        result = probe.evaluate(fake, invoke)
        self.assertEqual(result["planning_incomplete"], 12)
        self.assertEqual(result["correct_values"], 0)
        self.assertEqual(result["unsuitable_explicit_none"], 0)

    def test_only_exact_scalars_earn_credit(self):
        self.assertTrue(probe.scalar_equal("12.50", "12.5"))
        self.assertFalse(probe.scalar_equal("The answer is 12.5", "12.5"))
        self.assertFalse(probe.scalar_equal("12.5 or 13", "12.5"))
        self.assertFalse(probe.scalar_equal("1e2", "100"))

    def test_synthesis_failure_categories(self):
        for answer, complete, expected in [
            ("12.50", True, "matches_calculation"),
            ("13", True, "numeric_disagreement"),
            ("MISMATCH", True, "mismatch"),
            ("Answer: 12.5", True, "noncanonical_ungraded"),
            ("", True, "no_visible_answer"),
            ("12.5", False, "incomplete"),
        ]:
            self.assertEqual(probe.synthesis_outcome(answer, complete, "12.5"), expected)

    def test_paired_modes_share_calculation_and_fixed_denominator(self):
        calls = []
        cases = (("correct", "first question", "100"),
                 ("wrong", "second question", "100"),
                 ("rejected", "third question", "100"))
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="100")
        def invoke(operation, question, proposal=None):
            if operation == "plan":
                return question
            if question == "third question":
                return None
            return ("100" if question == "first question" else "999", "same synthesis prompt")
        result = probe.evaluate(fake, invoke, cases=cases, paired=True)
        self.assertEqual(len(calls), 7)
        for a, b in ((calls[1], calls[2]), (calls[4], calls[5])):
            self.assertEqual(a["seed"], b["seed"])
            self.assertEqual(a["n_predict"], b["n_predict"])
            self.assertNotEqual(a["prompt"], b["prompt"])
        self.assertEqual(result["paired_synthesis"],
                         dict(denominator=3, direct_only=0, thinking_only=0,
                              both=1, neither=2, thinking_strict_correct=1,
                              thinking_attempted=2))
        self.assertEqual(result["cases"][1]["thinking_synthesis"]["outcome"],
                         "numeric_disagreement")
        self.assertNotIn("thinking_synthesis", result["cases"][2])

    def test_rational_parser_comparison_reuses_exact_proposal(self):
        cases = (("recover", "first", "2"), ("retained", "second", "2"),
                 ("reject", "third", None), ("wrong", "fourth", "2"))
        old_inputs, new_inputs = [], []
        def fake(payload):
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="CALC: 2/3*3")
        def candidate(operation, question, proposal=None):
            if operation == "plan":
                return question
            new_inputs.append((question, proposal))
            if question == "third":
                return None
            return ("3" if question == "fourth" else "2", "synthesis")
        def baseline(operation, question, proposal=None):
            self.assertEqual(operation, "evaluate")
            old_inputs.append((question, proposal))
            return ("2", "synthesis") if question in ("second", "fourth") else None
        result = probe.evaluate(fake, candidate, cases=cases, baseline_invoke=baseline)
        self.assertEqual(old_inputs, new_inputs)
        self.assertEqual(result["parser_comparison"], dict(
            denominator=3, both_correct=1, candidate_only=1,
            baseline_only=1, neither=0, baseline_correct=2,
            candidate_correct=2, baseline_unsuitable_rejected=1,
            candidate_unsuitable_rejected=1, unsuitable_denominator=1))
        self.assertIsNone(result["official_ai_rating"])



if __name__ == "__main__":
    unittest.main()
