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

    def test_breadth_cases_have_fixed_domain_denominators(self):
        first = comparison.breadth_cases(42)
        self.assertEqual(first, comparison.breadth_cases(42))
        self.assertNotEqual(first, comparison.breadth_cases(43))
        self.assertEqual(len(first), 24)
        self.assertEqual(len({name for name, _, _ in first}), 24)
        for domain in ("logic", "quantity", "context", "strategy"):
            self.assertEqual(sum(name.startswith(domain + "_") for name, _, _ in first), 6)
        logic = [answer for name, _, answer in first if name.startswith("logic_")]
        self.assertEqual(logic.count("YES"), logic.count("NO"))

    def test_breadth_modes_are_matched_and_keep_all_failures(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="limit",
                        truncated=False, tokens_predicted=1, content="NO")
        result = comparison.evaluate(42, fake, "breadth")
        self.assertEqual(len(calls), 48)
        self.assertEqual(set(result["totals"]), {"direct:standard", "think:standard"})
        self.assertEqual(result["matched"], dict(both_correct=0, think_only=0, direct_only=0, neither=24))
        self.assertIsNone(result["official_ai_rating"])
        for domain in result["by_domain"].values():
            for mode in domain.values():
                self.assertEqual(mode, dict(correct=0, complete=0, attempted=6))
        for index, (a, b) in enumerate(zip(calls[::2], calls[1::2])):
            self.assertEqual(a["seed"], b["seed"])
            self.assertEqual(a["prompt"].replace("/no_think", "/think"), b["prompt"].replace("/no_think", "/think"))
            self.assertEqual(a["n_predict"], 768 if index % 2 else 256)
            self.assertEqual(b["n_predict"], 256 if index % 2 else 768)
            self.assertFalse(a["cache_prompt"])
            self.assertFalse(b["cache_prompt"])

    def test_native_comparison_workflow_is_bounded_and_pinned(self):
        source = (comparison.ROOT / ".github/workflows/qwen3-profile-evaluation.yml").read_text()
        for marker in ("runs-on: ubuntu-24.04", "timeout-minutes: 35",
                       "3837bf0", "prepare_model()", "llama-server",
                       "run_qwen_profile_comparison.py"):
            self.assertIn(marker, source)
        self.assertNotIn("schedule:", source)
        self.assertNotIn("secrets.", source)

    def test_authority_cases_balance_conflict_and_legitimate_controls(self):
        cases = comparison.authority_cases(42)
        self.assertEqual(cases, comparison.authority_cases(42))
        self.assertNotEqual(cases, comparison.authority_cases(43))
        self.assertEqual(len({name for name, _, _ in cases}), 8)
        self.assertEqual(sum(name.startswith("quote_") for name, _, _ in cases), 4)
        self.assertEqual(sum(name.startswith("control_") for name, _, _ in cases), 4)

    def test_authority_ablation_changes_only_system_suffix(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="NO")
        result = comparison.evaluate(42, fake, "authority")
        self.assertEqual(len(calls), 16)
        self.assertEqual(set(result["totals"]), {"think:baseline", "think:authority_rule"})
        self.assertEqual(sum(result["matched"].values()), 8)
        self.assertEqual(result["matched"]["both_correct"], 2)
        self.assertEqual(result["matched"]["neither"], 6)
        self.assertIsNone(result["official_ai_rating"])
        for group in result["by_domain"].values():
            self.assertTrue(all(row["attempted"] == 4 for row in group.values()))
        for i, (a, b) in enumerate(zip(calls[::2], calls[1::2])):
            self.assertEqual({k:v for k,v in a.items() if k != "prompt"},
                             {k:v for k,v in b.items() if k != "prompt"})
            baseline, candidate = (b, a) if i % 2 else (a, b)
            self.assertNotIn(comparison.AUTHORITY_RULE, baseline["prompt"])
            self.assertEqual(baseline["prompt"],
                             candidate["prompt"].replace(comparison.AUTHORITY_RULE, "", 1))
            self.assertEqual(a["n_predict"], 768)
            self.assertEqual(a["temperature"], 0.6)

    def test_quantitative_suite_has_reproducible_balanced_domains(self):
        cases = comparison.quantitative_cases(42)
        self.assertEqual(cases, comparison.quantitative_cases(42))
        self.assertNotEqual(cases, comparison.quantitative_cases(43))
        self.assertEqual(len({name for name, _, _ in cases}), 12)
        for domain in ("arithmetic", "inverse", "control"):
            self.assertEqual(sum(name.startswith(domain + "_") for name, _, _ in cases), 4)

    def test_quantitative_candidate_changes_only_system_prompt(self):
        calls = []
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="NO")
        report = comparison.evaluate(42, fake, "quantitative_check")
        self.assertEqual(len(calls), 24)
        self.assertEqual(set(report["totals"]), {"think:baseline", "think:quantitative_check"})
        self.assertEqual(report["matched"], dict(both_correct=2, candidate_only=0,
                         baseline_only=0, neither=10))
        self.assertIsNone(report["official_ai_rating"])
        for group in report["by_domain"].values():
            self.assertTrue(all(row["attempted"] == 4 for row in group.values()))
        for i, (a, b) in enumerate(zip(calls[::2], calls[1::2])):
            self.assertEqual({k:v for k,v in a.items() if k != "prompt"},
                             {k:v for k,v in b.items() if k != "prompt"})
            baseline, candidate = (b, a) if i % 2 else (a, b)
            self.assertNotIn(comparison.QUANTITATIVE_CHECK, baseline["prompt"])
            self.assertEqual(baseline["prompt"],
                             candidate["prompt"].replace(comparison.QUANTITATIVE_CHECK, "", 1))
            self.assertEqual(a["n_predict"], 768)
            self.assertEqual(a["temperature"], 0.6)

    def test_contract_audit_preserves_strict_credit_and_skips_incomplete(self):
        calls = []
        def fake(payload):
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=4, content="Answer: NO")
        def normalize(question, visible):
            calls.append((question, visible))
            return "NO" if visible == "Answer: NO" else visible
        result = comparison.evaluate(42, fake, "quantitative_check", normalize)
        self.assertEqual(len(calls), 24)
        self.assertTrue(all(visible == "Answer: NO" for _, visible in calls))
        for arm, raw in result["totals"].items():
            self.assertEqual(raw["correct"], 0)
            self.assertEqual(result["answer_contract_audit"][arm], dict(
                attempted=12, strict_correct=0, contract_correct=2,
                changed=12, recovered=2, lost=0))
        def capped(payload):
            return dict(generation_settings=payload, stop_type="limit",
                        truncated=False, tokens_predicted=4, content="Answer: NO")
        calls.clear()
        failed = comparison.evaluate(42, capped, "quantitative_check", normalize)
        self.assertFalse(calls)
        self.assertTrue(all(not row["contract_correct"] for row in failed["cases"]))


    def test_recovery_comparison_matches_transcripts_and_inference_settings(self):
        calls = []
        inputs = {"baseline": [], "recovery": []}
        def builder(name):
            def build(question, history):
                inputs[name].append((question, history))
                return name + "\n" + question
            return build
        def fake(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=1, content="NO")
        builders = {name: builder(name) for name in inputs}
        report = comparison.evaluate(42, fake, "context_recovery", context_builders=builders)
        self.assertEqual(len(calls), 16)
        self.assertEqual(inputs["baseline"], inputs["recovery"])
        self.assertEqual(set(report["totals"]), {"think:baseline", "think:recovery"})
        self.assertEqual(report["matched"], dict(both_correct=1, candidate_only=0,
                         baseline_only=0, neither=7))
        for index, (a, b) in enumerate(zip(calls[::2], calls[1::2])):
            self.assertEqual({k:v for k,v in a.items() if k != "prompt"},
                             {k:v for k,v in b.items() if k != "prompt"})
            self.assertEqual(a["n_predict"], 768)
            self.assertIn(("recovery" if index % 2 else "baseline") + "\n", a["prompt"])
        self.assertIsNone(report["official_ai_rating"])
        self.assertTrue(all("recovery_prompt_sha256" in row for row in report["cases"]))

    def test_recovery_requires_both_builders_and_deterministic_public_histories(self):
        self.assertEqual(comparison.context_cases(42), comparison.context_cases(42))
        self.assertNotEqual(comparison.context_cases(42), comparison.context_cases(43))
        with self.assertRaisesRegex(ValueError, "both production builders"):
            comparison.evaluate(42, lambda _: {}, "context_recovery")
        self.assertEqual(len({name for name, _, _, _ in comparison.context_cases(42)}), 8)


if __name__ == "__main__":
    unittest.main()
