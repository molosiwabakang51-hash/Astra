"""Synthetic-only scorecard regression tests; these never establish handset quality."""
import copy
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "scripts/quality/evaluate_phone_evidence.py"
spec = importlib.util.spec_from_file_location("astra_phone_evidence", SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def synthetic_record():
    cases = {item["id"]: {"status": "pass", "evidence": "synthetic-test-only"}
             for item in module.__dict__["json"].loads(
                 (ROOT / "scripts/quality/scorecard_cases.json").read_text(encoding="utf-8")
             )["cases"]}
    evidence = {
        "commit": "a" * 40,
        "android_version": "14",
        "model_sha256": "b" * 64,
        "airplane_mode": True,
        "wifi_disabled": True,
        "owner_signed_upgrade": True,
    }
    run = {"evidence": evidence, "cases": cases}
    return {"runs": [copy.deepcopy(run), copy.deepcopy(run)]}


class SameConfigurationGateTests(unittest.TestCase):
    def test_two_identical_synthetic_records_are_structurally_accepted(self):
        result = module.evaluate(synthetic_record())
        self.assertEqual("EVALUATED", result["status"])
        self.assertEqual([100, 100], result["scores"])
        self.assertFalse(result["stable_release"])

    def test_build_model_and_android_mismatch_each_withhold_score(self):
        for field, replacement in (
            ("commit", "c" * 40),
            ("model_sha256", "d" * 64),
            ("android_version", "15"),
        ):
            with self.subTest(field=field):
                record = synthetic_record()
                record["runs"][1]["evidence"][field] = replacement
                result = module.evaluate(record)
                self.assertIsNone(result["rating"])
                self.assertEqual("INCOMPLETE", result["status"])
                self.assertTrue(any(field in x and "mismatch" in x
                                    for x in result["errors"]))

    def test_missing_and_invalid_device_evidence_cannot_award_score(self):
        record = synthetic_record()
        record["runs"][0]["evidence"]["wifi_disabled"] = False
        self.assertIsNone(module.evaluate(record)["rating"])
        record = synthetic_record()
        record["runs"][1]["cases"]["F1"] = {
            "status": "unverified"
        }
        self.assertIsNone(module.evaluate(record)["rating"])
        record = synthetic_record()
        record["runs"][0]["cases"]["F4"] = {
            "status": "fail", "evidence": "synthetic-test-only"
        }
        self.assertIsNone(module.evaluate(record)["rating"])


if __name__ == "__main__":
    unittest.main()
