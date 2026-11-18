"""Validate scorecard structure only. Never grant device intelligence points in CI."""
import json
import unittest
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "scripts/quality/scorecard_cases.json"
DOC = ROOT / "docs/ai-quality-evaluation.md"

class ScorecardContractTest(unittest.TestCase):
    def test_manifest_is_exactly_one_hundred_points(self):
        data = json.loads(MANIFEST.read_text(encoding="utf-8"))
        cases = data["cases"]
        self.assertEqual(len(cases), 30)
        self.assertEqual(sum(case["points"] for case in cases), 100)
        self.assertEqual(len({case["id"] for case in cases}), 30)
        for prefix, points in (("A", 5), ("B", 4), ("C", 3),
                               ("D", 3), ("E", 3), ("F", 2)):
            self.assertEqual(
                {case["id"] for case in cases if case["id"].startswith(prefix)},
                {f"{prefix}{i}" for i in range(1, 6)}
            )
            self.assertTrue(all(case["points"] == points for case in cases
                                if case["id"].startswith(prefix)))
        self.assertTrue(all(case["requires_real_device"] is True for case in cases))
        self.assertEqual({case["id"] for case in cases if case["mandatory_gate"]},
                         {"F1", "F4", "F5"})
        self.assertIn("must never auto-award", data["scoring"])

    def test_every_case_is_documented_and_unverified_is_not_pass(self):
        document = DOC.read_text(encoding="utf-8")
        cases = json.loads(MANIFEST.read_text(encoding="utf-8"))["cases"]
        for case in cases:
            with self.subTest(case=case["id"]):
                self.assertIn(f"**{case['id']} (", document)
        self.assertIn("unverified = no score", document)
        self.assertIn("two complete runs", document)
        for domain in ("Natural conversation", "Factual accuracy", "Reasoning",
                       "Multi-turn context", "Language", "Tool correctness",
                       "Startup and latency", "Safety and privacy", "Handset usability"):
            self.assertIn(domain, document)

if __name__ == "__main__":
    unittest.main()
