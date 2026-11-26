"""Synthetic regression tests for the pre/post truncated-evidence diagnostic."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_truncated_evidence as probe


class TruncatedEvidenceTests(unittest.TestCase):
    @staticmethod
    def builders(suite):
        data = {(q, tuple(h)): (case, required) for case, q, _, h, required in suite}
        def build(q, h, candidate):
            case, required = data[(q, tuple(h))]
            if case.startswith("control_"):
                return 'Previous user: "Actually, ' + required[0] + '"\n' + q
            notice = ""
            if candidate:
                notice = (probe.TAIL if case.startswith("latest_") else probe.GAP) + "]\n"
            return notice + 'Previous user: "Actually, background material"\n' + q
        return {"baseline": lambda q, h: build(q, h, False),
                "recovery": lambda q, h: build(q, h, True)}

    def test_seeded_domain_balance_and_fixture_provenance(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}), 12)
            for domain in ("latest_", "intermediate_", "control_"):
                rows = [(i, r) for i, r in enumerate(suite) if r[0].startswith(domain)]
                self.assertEqual(len(rows), 4)
                self.assertEqual(sum(i % 2 == 0 for i, _ in rows), 2)
            for case, q, expected, history, fragments in suite:
                self.assertTrue(q.endswith("Reply YES or NO only."))
                self.assertTrue(any(role == "user" and fragments[0] in text for role, text in history))
                self.assertEqual(expected, "YES" if case.startswith("control_") else "NO")

    def test_production_warning_literals_share_detection_prefix(self):
        # The latest notice uses a comma, not the intermediate notice's period.
        # The detector must recognize both exact production literals.
        production = probe.native.SYSTEM_PATH.read_text(encoding="utf-8")
        intermediate = "[Some linked corrections are omitted. If the result depends on missing steps, ask for clarification.]"
        latest = "[Some linked corrections are omitted, including a later correction. Do not assume the latest value; ask for clarification.]"
        for notice in (intermediate, latest):
            self.assertIn(notice, production)
            self.assertIn(probe.GAP, notice)
        self.assertNotIn(probe.TAIL, intermediate)
        self.assertIn(probe.TAIL, latest)

    def test_preflight_rejects_false_warnings_and_missing_fixture_distinction(self):
        suite = probe.cases(19)
        good = self.builders(suite)
        self.assertEqual(len(probe.prepare(19, good, suite)), 12)
        for bad in (
            {"baseline": good["baseline"], "recovery": good["baseline"]},
            {"baseline": good["recovery"], "recovery": good["recovery"]},
            {"baseline": good["baseline"],
             "recovery": lambda q, h: good["recovery"](q, h).replace(probe.TAIL, probe.GAP)},
            {"baseline": good["baseline"],
             "recovery": lambda q, h: "Changed.\n" + good["recovery"](q, h)},
        ):
            with self.assertRaises(RuntimeError):
                probe.prepare(19, bad, suite)

    def test_scoring_pairing_and_metadata_failure(self):
        suite = probe.cases(19)
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(content="NO", stop_type="eos", truncated=False,
                        tokens_predicted=1, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer, self.builders(suite), suite)
            with self.assertRaises(RuntimeError):
                probe.evaluate(19, lambda _: {}, self.builders(suite), suite)
        self.assertEqual(len(calls), 24)
        for i in range(0, len(calls), 2):
            self.assertEqual(calls[i]["seed"], calls[i+1]["seed"])
            self.assertEqual(calls[i]["n_predict"], calls[i+1]["n_predict"])
        self.assertEqual(result["matched"]["both_correct"], 8)
        self.assertEqual(result["matched"]["neither"], 4)
        self.assertEqual(result["totals"]["recovery"]["complete"], 12)
        self.assertEqual(sum(row["tail_notice"] for row in result["cases"]), 4)
        self.assertEqual(sum(row["gap_notice"] for row in result["cases"]), 8)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("background material", str(result))
