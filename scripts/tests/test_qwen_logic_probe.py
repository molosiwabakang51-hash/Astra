"""Synthetic evaluator tests; no model accuracy claim."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_logic_probe as probe


class LogicProbeTests(unittest.TestCase):
    def test_strict_plan_syntax_and_known_symbols(self):
        self.assertEqual(probe.parse("FORM: A -> B; A => B", {"A", "B"}),
                         (("A -> B", "A"), "B"))
        for bad in ("FORM: A; C => B", "FORM: A;;B => A",
                    "FORM: A => B\nFORM: B => A", "Answer: FORM: A => B",
                    "FORM: A => B.", "FORM: A => B => A", "NONE"):
            self.assertIsNone(probe.parse(bad, {"A", "B"}))
        self.assertIsNone(probe.parse("FORM: A => A", set()))

    def test_matched_pair_does_not_reward_accidental_verdict(self):
        cases = (("valid", "A = p; B = q.", "Argument", ("A",), "A", "ENTAILED"),
                 ("unsuitable", "No mapping supplied.", "Unknown", None, None, None))
        calls = []
        verifier_calls = []
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=10,
                        content="FORM: B => B" if len(calls) <= 2 else "NONE")
        def verify(question):
            verifier_calls.append(question)
            if question.startswith("Premises: B. Conclusion: B."):
                return "ENTAILED"
            return "COUNTEREXAMPLE"
        result = probe.evaluate(infer, verify, cases)
        self.assertEqual(len(calls), 4)
        self.assertEqual(len(verifier_calls), 4)
        for mode in ("direct", "think"):
            totals = result["totals"][mode]
            self.assertEqual(totals["syntax_accepted"], 1)
            self.assertEqual(totals["verdict_correct"], 1)
            self.assertEqual(totals["faithful_and_correct"], 0)
            self.assertEqual(totals["unsuitable_explicit_none"], 1)
        self.assertEqual(result["matched"], dict(both=0, direct_only=0, thinking_only=0, neither=1))
        self.assertIsNone(result["official_ai_rating"])
        for a, b in zip(calls[::2], calls[1::2]):
            self.assertEqual(a["n_predict"], b["n_predict"])
            self.assertEqual(a["seed"], b["seed"])

    def test_incomplete_formalization_cannot_be_verified(self):
        def infer(payload):
            return dict(generation_settings=payload, stop_type="limit",
                        truncated=False, tokens_predicted=768, content="FORM: A => A")
        def verify(question):
            self.fail("Incomplete generation must not reach verifier")
        result = probe.evaluate(infer, verify)
        self.assertEqual(len(result["cases"]), 20)
        self.assertTrue(all(not row["accepted"] for row in result["cases"]))
        self.assertEqual(result["matched"]["neither"], 7)

    def test_no_reference_formula_or_verdict_in_planning_prompt(self):
        for _, mapping, argument, _, _, _ in probe.CASES:
            prompt = probe.planning_prompt(mapping, argument)
            self.assertIn(mapping, prompt)
            self.assertIn(argument, prompt)
            self.assertNotIn("COUNTEREXAMPLE", prompt)
            self.assertNotIn("INCONSISTENT_PREMISES", prompt)

    def test_grammar_comparison_changes_only_constraint_and_scores_meaning(self):
        cases = (("valid", "A = p; B = q.", "Argument", ("A",), "A", "ENTAILED"),
                 ("unsuitable", "A = p; B = q.", "Unclear", None, None, None))
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=8,
                        content="FORM: B => B" if len(calls) <= 2 else "NONE")
        def verify(question):
            return "ENTAILED" if question.startswith("Premises: B. Conclusion: B.") else "COUNTEREXAMPLE"
        result = probe.evaluate(infer, verify, cases, experiment="grammar")
        self.assertEqual(len(calls), 4)
        self.assertEqual(set(result["totals"]), {"direct", "grammar"})
        for a, b in zip(calls[::2], calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k != "grammar"},
                             {k:v for k,v in b.items() if k != "grammar"})
            self.assertEqual(sum("grammar" in x for x in (a, b)), 1)
            self.assertEqual(next(x["grammar"] for x in (a, b) if "grammar" in x), probe.GRAMMAR)
        self.assertEqual(result["totals"]["grammar"]["verdict_correct"], 1)
        self.assertEqual(result["totals"]["grammar"]["faithful_and_correct"], 0)
        self.assertEqual(result["matched"], dict(both=0, direct_only=0, grammar_only=0, neither=1))
        self.assertIsNone(result["official_ai_rating"])

    def test_grammar_canary_fails_closed(self):
        seen = []
        def infer(payload):
            seen.append(payload)
            return dict(content=probe.GRAMMAR_CANARY, stop_type="eos", truncated=False)
        probe.grammar_canary(infer)
        self.assertNotIn(probe.GRAMMAR_CANARY, seen[0]["prompt"])
        self.assertIn(probe.GRAMMAR_CANARY, seen[0]["grammar"])
        for reply in (dict(content="NO", stop_type="eos", truncated=False),
                      dict(content=probe.GRAMMAR_CANARY, stop_type="limit", truncated=False),
                      dict(content=probe.GRAMMAR_CANARY, stop_type="eos", truncated=True)):
            with self.assertRaisesRegex(RuntimeError, "canary failed"):
                probe.grammar_canary(lambda _: reply)

    def test_grammar_does_not_hide_reasoning_or_accept_unknown_symbols(self):
        def infer(payload):
            return dict(generation_settings=payload, stop_type="eos",
                        truncated=False, tokens_predicted=9,
                        content="<think>ignored</think>FORM: A => A" if "grammar" in payload else "NONE")
        def verify(question):
            self.fail("Noncanonical constrained response must not reach verifier")
        result = probe.evaluate(infer, verify, cases=probe.CASES[:1], experiment="grammar")
        self.assertEqual(result["totals"]["grammar"]["syntax_accepted"], 0)
        self.assertIsNone(probe.parse("FORM: Z => Z", {"A"}))


if __name__ == "__main__":
    unittest.main()
