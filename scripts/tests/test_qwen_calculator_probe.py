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


if __name__ == "__main__":
    unittest.main()
