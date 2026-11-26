"""Synthetic evaluator checks, not measured intelligence."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_correction_warning as probe


class CorrectionWarningTests(unittest.TestCase):
    def test_fixture_pairing_and_order(self):
        suite = probe.cases(17)
        self.assertEqual(len(suite), 8)
        self.assertEqual(len({r[0] for r in suite}), 8)
        self.assertEqual(suite, probe.cases(17))
        for prefix in ("omitted_", "control_"):
            indices = [i for i, r in enumerate(suite) if r[0].startswith(prefix)]
            self.assertEqual([i % 2 for i in indices].count(0), 2)
        for case, fixture, expected in suite:
            baseline = probe.build_question(case, fixture, "baseline")
            candidate = probe.build_question(case, fixture, "warning")
            if case.startswith("omitted_"):
                self.assertEqual(expected, "NO")
                self.assertEqual(baseline.replace(probe.GAP, probe.TAIL), candidate)
            else:
                self.assertEqual(expected, "YES")
                self.assertEqual(baseline, candidate)
            self.assertTrue(candidate.endswith("Reply YES or NO only."))

    def test_warning_matches_production_literal(self):
        source = probe.native.SYSTEM_PATH.read_text(encoding="utf-8")
        for notice in (probe.GAP, probe.TAIL):
            self.assertIn(notice.rstrip("\n"), source)

    def test_matched_evaluation_and_failure_accounting(self):
        requests = []
        def infer(payload):
            requests.append(payload)
            answer = "NO" if probe.TAIL in payload["prompt"] else "YES"
            return dict(content=answer, stop_type="eos", truncated=False,
                        tokens_predicted=5, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(17, infer)
        self.assertEqual(len(requests), 16)
        for i in range(0, len(requests), 2):
            self.assertEqual(requests[i]["seed"], requests[i+1]["seed"])
            self.assertEqual(requests[i]["n_predict"], requests[i+1]["n_predict"])
        self.assertEqual(result["totals"]["baseline"]["correct"], 4)
        self.assertEqual(result["totals"]["warning"]["correct"], 8)
        self.assertEqual(result["matched"]["candidate_only"], 4)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("Previous user:", str(result))

    def test_unverified_metadata_refuses_scoring(self):
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            with self.assertRaises(RuntimeError):
                probe.evaluate(17, lambda _: {})
