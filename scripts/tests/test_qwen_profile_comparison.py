"""Synthetic contracts for real-model CPU comparison; never award a model score."""
import importlib.util
from pathlib import Path
import unittest

FILE = Path(__file__).resolve().parents[1] / "quality/run_qwen_profile_comparison.py"
spec = importlib.util.spec_from_file_location("qwen_comparison", FILE)
comparison = importlib.util.module_from_spec(spec)
spec.loader.exec_module(comparison)


class QwenProfileComparisonTests(unittest.TestCase):
    def test_generated_problems_reproducible_and_broad(self):
        self.assertEqual(comparison.cases(42), comparison.cases(42))
        self.assertNotEqual(comparison.cases(42), comparison.cases(43))
        self.assertEqual(len({x[0] for x in comparison.cases(42)}), 6)
        self.assertIn("You are Astra", comparison.system_prompt())

    def test_same_questions_and_seeds_but_distinct_profiles(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="NO")
        result = comparison.evaluate(42, fake)
        self.assertEqual(len(calls), 24)
        self.assertEqual(len(result["cases"]), 24)
        self.assertIsNone(result["official_ai_rating"])
        for i in range(0, len(calls), 2):
            a, b = calls[i:i + 2]
            self.assertEqual(a["prompt"], b["prompt"])
            self.assertEqual(a["seed"], b["seed"])
            self.assertEqual(a["n_predict"], b["n_predict"])
            self.assertNotEqual(a["temperature"], b["temperature"])
            self.assertFalse(a["cache_prompt"])

    def test_limit_and_truncation_do_not_earn_credit(self):
        def fake(payload):
            return dict(generation_settings=payload, stop_type="limit",
                        truncated=False, tokens_predicted=1, content="NO")
        result = comparison.evaluate(42, fake)
        self.assertFalse(any(x["passed"] for x in result["cases"]))

    def test_server_settings_must_match_and_thought_is_not_reported(self):
        def fake(payload):
            return dict(generation_settings=dict(payload, temperature=0.123),
                        stop_type="eos", truncated=False, tokens_predicted=1, content="NO")
        with self.assertRaisesRegex(RuntimeError, "configuration"):
            comparison.evaluate(42, fake)
        self.assertEqual(comparison.visible_answer("<think>private reasoning</think>NO"), "NO")
        self.assertEqual(comparison.visible_answer("<think>unfinished"), "")
        self.assertEqual(comparison.visible_answer("private reasoning</think>NO"), "NO")

    def test_native_comparison_workflow_is_bounded_and_pinned(self):
        source = (comparison.ROOT / ".github/workflows/qwen3-profile-evaluation.yml").read_text()
        for marker in ("runs-on: ubuntu-24.04", "timeout-minutes: 35",
                       "3837bf0", "prepare_model()", "llama-server",
                       "run_qwen_profile_comparison.py"):
            self.assertIn(marker, source)
        self.assertNotIn("schedule:", source)
        self.assertNotIn("secrets.", source)


if __name__ == "__main__":
    unittest.main()
