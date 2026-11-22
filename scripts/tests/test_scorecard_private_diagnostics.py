"""Scorecard diagnostics must never echo private or malformed evidence keys."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "scripts/quality/evaluate_phone_evidence.py"
spec = importlib.util.spec_from_file_location("astra_phone_scorecard", MODULE)
scorecard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scorecard)


def valid_record():
    cases = json.loads((ROOT / "scripts/quality/scorecard_cases.json").read_text())["cases"]
    results = {case["id"]: {"status": "pass", "evidence": "test-1"} for case in cases}
    meta = {"commit": "a" * 40, "android_version": "14",
            "model_sha256": "b" * 64, "airplane_mode": True,
            "wifi_disabled": True, "owner_signed_upgrade": True}
    return {"runs": [{"evidence": copy.deepcopy(meta), "cases": copy.deepcopy(results)}
                     for _ in range(2)]}


class PrivateDiagnosticTests(unittest.TestCase):
    def test_valid_owner_attested_record_is_not_a_stable_release(self):
        result = scorecard.evaluate(valid_record())
        self.assertEqual(result["status"], "EVALUATED")
        self.assertFalse(result["stable_release"])

    def test_unknown_case_key_cannot_leak_private_text(self):
        record = valid_record()
        secret = "PRIVATE_CHAT_DO_NOT_PRINT_8844"
        record["runs"][0]["cases"][secret] = {"status": "invalid"}
        result = scorecard.evaluate(record)
        self.assertEqual(result["status"], "INCOMPLETE")
        self.assertIsNone(result["rating"])
        self.assertNotIn(secret, json.dumps(result))

    def test_missing_case_does_not_leak_extra_case_keys(self):
        record = valid_record()
        record["runs"][1]["cases"].pop("A1")
        record["runs"][1]["cases"]["PRIVATE_SECRET_9977"] = "bad"
        result = scorecard.evaluate(record)
        self.assertEqual(result["status"], "INCOMPLETE")
        self.assertNotIn("PRIVATE_SECRET_9977", json.dumps(result))

    def test_mandatory_gate_failure_never_awards_rating(self):
        record = valid_record()
        record["runs"][0]["cases"]["F1"]["status"] = "fail"
        result = scorecard.evaluate(record)
        self.assertIsNone(result["rating"])
        self.assertEqual(result["status"], "INCOMPLETE")


if __name__ == "__main__":
    unittest.main()
