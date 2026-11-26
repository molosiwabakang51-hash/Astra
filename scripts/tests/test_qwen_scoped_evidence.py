"""Tests for scoped synthetic recovery evidence; not model quality evidence."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_scoped_evidence as probe


class ScopedEvidenceTests(unittest.TestCase):
    def test_seeded_balanced_cases_and_controls(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}), 12)
            for domain in ("omitted_", "unrelated_", "control_"):
                rows = [(i, r) for i, r in enumerate(suite) if r[0].startswith(domain)]
                self.assertEqual(len(rows), 4)
                self.assertEqual(sum(i % 2 == 0 for i, _ in rows), 2)
            for case, fixture, expected in suite:
                quoted, affected, question = fixture
                base = probe.build_question(case, fixture, "baseline")
                candidate = probe.build_question(case, fixture, "scoped")
                self.assertIn(quoted, base)
                self.assertIn(quoted, candidate)
                self.assertTrue(base.endswith(question))
                self.assertTrue(candidate.endswith(question))
                if case.startswith("control_"):
                    self.assertIsNone(affected)
                    self.assertEqual(base, candidate)
                else:
                    self.assertIn(probe.TAIL, base)
                    self.assertNotIn(probe.TAIL, candidate)
                    self.assertIn("later correction to " + affected, candidate)
                    self.assertIn("not included in these excerpts", candidate)
                    if case.startswith("unrelated_"):
                        self.assertNotIn(affected, question)
                    else:
                        self.assertIn(expected, ("UNKNOWN", "NO"))

    def test_same_evidence_scoped_independently_of_requested_topic(self):
        suite = probe.cases(19)
        for i in range(0, len(suite), 3):
            a, b = suite[i:i+2]
            self.assertEqual(a[1][:2], b[1][:2])
            self.assertNotEqual(a[1][2], b[1][2])
            self.assertEqual(probe.build_question(a[0], a[1], "scoped").split(probe.BOUNDARY)[0],
                             probe.build_question(b[0], b[1], "scoped").split(probe.BOUNDARY)[0])

    def test_paired_metrics_and_unverified_metadata(self):
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(content="NO", stop_type="eos", truncated=False,
                        tokens_predicted=1, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer)
            with self.assertRaises(RuntimeError):
                probe.evaluate(19, lambda _: {})
        self.assertEqual(len(calls), 24)
        for i in range(0, len(calls), 2):
            self.assertEqual(calls[i]["seed"], calls[i+1]["seed"])
            self.assertEqual(calls[i]["n_predict"], calls[i+1]["n_predict"])
        self.assertEqual(result["totals"]["baseline"]["correct"], 2)
        self.assertEqual(result["totals"]["scoped"]["correct"], 2)
        self.assertEqual(result["matched"]["both_correct"], 2)
        self.assertEqual(result["matched"]["neither"], 10)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("Previous user:", str(result))
