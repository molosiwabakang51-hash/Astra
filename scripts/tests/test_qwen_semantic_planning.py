import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_semantic_planning as probe


class SemanticPlanningTests(unittest.TestCase):
    def test_balanced_cases_and_known_values(self):
        for seed in range(50):
            rows = probe.cases(seed)
            self.assertEqual(len({r[0] for r in rows}), 16)
            self.assertEqual(sum(r[2] is None for r in rows), 8)
            self.assertEqual(rows, probe.cases(seed))
            answers = {k: a for k, _, a in rows}
            self.assertIsNotNone(answers["rate_known"])
            self.assertEqual(answers["unit_known"], answers["share_known"])

    def test_prompt_boundary_preserved(self):
        original = 'Instructions.\nQUESTION: "A quoted task."'
        candidate = probe.candidate_prompt(original)
        self.assertTrue(candidate.endswith('\nQUESTION: "A quoted task."'))
        self.assertIn(probe.CHECK.strip(), candidate)
        with self.assertRaises(ValueError):
            probe.candidate_prompt("No boundary")

    def test_paired_scoring_distinguishes_wrong_plan_from_missing(self):
        suite = [("known", "Compute 2+3", "5"), ("missing", "Missing value", None)]
        calls = []
        def invoke(op, question, proposal=None):
            if op == "plan":
                return "Instructions.\nQUESTION: " + question
            return ("5", "unused") if proposal == "CALC: 2+3" else None
        def fake(payload):
            calls.append(payload)
            answer = "NONE" if "Missing value" in payload["prompt"] and probe.CHECK.strip() in payload["prompt"] else "CALC: 2+3"
            return dict(content=answer, stop_type="eos", truncated=False,
                        tokens_predicted=8, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Test system"):
            report = probe.evaluate(fake, invoke, 10, suite)
        self.assertEqual(len(calls), 4)
        self.assertEqual(calls[0]["seed"], calls[1]["seed"])
        self.assertEqual(calls[2]["seed"], calls[3]["seed"])
        self.assertEqual(report["totals"]["baseline"]["unsuitable_accepted"], 1)
        self.assertEqual(report["totals"]["sufficiency"]["unsuitable_accepted"], 0)
        self.assertEqual(report["totals"]["sufficiency"]["correct"], 1)
        self.assertIsNone(report["official_ai_rating"])
        self.assertNotIn("CALC: 2+3", str(report))

    def test_invalid_native_metadata_fails_closed(self):
        with patch.object(probe.native, "system_prompt", return_value="Test system"):
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: {}, lambda *_: "Instructions.\nQUESTION: test",
                               10, [("known", "test", "5")])
