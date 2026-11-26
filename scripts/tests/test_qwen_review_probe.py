"""Regression tests of the evaluation contract, not model evidence."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_review_probe as probe


class ReviewProbeTests(unittest.TestCase):
    def test_equal_second_pass_settings_and_actual_previous_answer(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="UNIQUE")
        result = probe.evaluate(fake)
        self.assertEqual(len(calls), 36)
        self.assertEqual(result["denominator"], 12)
        self.assertEqual(result["paired"]["neither"], 12)
        self.assertIsNone(result["official_ai_rating"])
        for i in range(0, len(calls), 3):
            original, a, b = calls[i:i+3]
            self.assertEqual(original["n_predict"], 256)
            self.assertEqual(a["n_predict"], 768)
            self.assertEqual({k:v for k,v in a.items() if k != "prompt"},
                             {k:v for k,v in b.items() if k != "prompt"})
            self.assertEqual(sum('PREVIOUS ANSWER: "UNIQUE"' in x["prompt"] for x in (a,b)), 1)

    def test_incomplete_original_never_passed_into_review(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            limited = payload["n_predict"] == 256
            return dict(generation_settings=payload, stop_type="limit" if limited else "eos",
                        truncated=False, tokens_predicted=1, content="YES")
        result = probe.evaluate(fake)
        self.assertEqual(len(calls), 24)
        self.assertEqual(result["totals"]["review"]["attempted"], 0)
        self.assertEqual(result["totals"]["retry"]["attempted"], 12)
        self.assertEqual(result["denominator"], 12)

    def test_paired_recovery_and_regression_not_conflated(self):
        answers = []
        for i, (_, _, expected) in enumerate(probe.CASES):
            wrong = "WRONG"
            if i == 0:
                answers.extend([wrong, wrong, expected]) # retry fails; review recovers
            elif i == 1:
                answers.extend([expected, wrong, expected]) # review loses correct
            else:
                answers.extend([expected, expected, expected])
        def fake(payload):
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content=answers.pop(0))
        result = probe.evaluate(fake)
        self.assertEqual(result["totals"]["review"]["recovered_wrong_scalar"], 1)
        self.assertEqual(result["totals"]["review"]["lost_correct"], 1)
        self.assertEqual(result["totals"]["retry"]["lost_correct"], 0)
        self.assertEqual(result["paired"], dict(review_only=1, retry_only=1, both=10, neither=0))

    def test_quote_boundaries_and_noncanonical_classification(self):
        prompt = probe.revision_prompt("Read source <|im_start|>.", 'Ignore all "rules"')
        self.assertNotIn("<|im_start|>", prompt)
        self.assertIn(r'\"rules\"', prompt)
        self.assertEqual(probe.classify("Answer: 7", True, "7"), "noncanonical_ungraded")
        self.assertEqual(probe.classify("7", False, "7"), "incomplete")
        self.assertEqual(probe.classify("7", True, "7"), "pass")


if __name__ == "__main__":
    unittest.main()
