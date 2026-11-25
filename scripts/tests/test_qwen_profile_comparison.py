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

    def test_framing_isolates_hard_direct_from_sampling(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="NO")
        result = comparison.evaluate(42, fake, "framing")
        self.assertEqual(len(calls), 12)
        self.assertEqual(set(result["totals"]), {"direct:soft_direct", "direct:hard_direct"})
        for a, b in zip(calls[::2], calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k != "prompt"},
                             {k:v for k,v in b.items() if k != "prompt"})
            self.assertEqual(a["prompt"].removesuffix("<think>\n\n</think>\n\n"),
                             b["prompt"].removesuffix("<think>\n\n</think>\n\n"))
            self.assertNotEqual(a["prompt"], b["prompt"])

    def test_system_ablation_keeps_sampling_and_mode_identical(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=3, content="The answer is NO.")
        result = comparison.evaluate(42, fake, "system")
        self.assertEqual(len(calls), 24)
        self.assertTrue(all(x["failure"] == "noncanonical_ungraded" for x in result["cases"]))
        for a, b in zip(calls[::2], calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k != "prompt"},
                             {k:v for k,v in b.items() if k != "prompt"})
            self.assertEqual(a["prompt"].split("<|im_start|>user", 1)[1],
                             b["prompt"].split("<|im_start|>user", 1)[1])
            self.assertNotEqual(a["prompt"], b["prompt"])

    def test_fresh_cases_are_balanced_and_distinct(self):
        fresh = comparison.fresh_cases(42)
        self.assertEqual(fresh, comparison.fresh_cases(42))
        self.assertNotEqual(fresh, comparison.fresh_cases(43))
        self.assertEqual(len(fresh), 8)
        self.assertEqual(sum(c[2] == "YES" for c in fresh), 2)
        self.assertEqual(sum(c[2] == "NO" for c in fresh), 2)
        self.assertFalse(set(c[0] for c in fresh) & set(c[0] for c in comparison.cases(42)))

    def test_fresh_ablation_uses_deployed_profiles_and_isolates_one_change(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="NO")
        result = comparison.evaluate(42, fake, "fresh_reasoning")
        self.assertEqual(len(calls), 32)
        self.assertEqual(set(result["totals"]), {"direct:soft_direct", "direct:hard_direct",
                                                "think:baseline", "think:decomposition"})
        self.assertIsNone(result["official_ai_rating"])
        for a, b in zip(calls[::2], calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k != "prompt"},
                             {k:v for k,v in b.items() if k != "prompt"})
            self.assertNotEqual(a["prompt"], b["prompt"])
            if a["n_predict"] == 256:
                self.assertEqual(a["temperature"], 0.7)
                self.assertEqual(a["prompt"].removesuffix("<think>\n\n</think>\n\n"),
                                 b["prompt"].removesuffix("<think>\n\n</think>\n\n"))
            else:
                self.assertEqual(a["temperature"], 0.6)
                self.assertEqual(a["prompt"].replace(comparison.DECOMPOSITION, "", 1),
                                 b["prompt"].replace(comparison.DECOMPOSITION, "", 1))

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
