"""No synthetic JVM or CI output may masquerade as an earned handset score."""
import importlib.util
import unittest
from pathlib import Path

PATH = Path(__file__).resolve().parents[1] / "quality/evaluate_phone_evidence.py"
spec = importlib.util.spec_from_file_location("astra_phone_evidence", PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def record(status="pass"):
    manifest = __import__("json").loads(module.MANIFEST.read_text(encoding="utf-8"))
    cases = {c["id"]: {"status": status, "evidence": "owner-local-test-001"} for c in manifest["cases"]}
    evidence = {"commit": "abc123", "android_version": "14",
                "model_sha256": "a" * 64, "airplane_mode": True,
                "wifi_disabled": True, "owner_signed_upgrade": True}
    return {"runs": [{"cases": {k: dict(v) for k, v in cases.items()}, "evidence": dict(evidence)}
                     for _ in range(2)]}


class PhoneEvidenceTests(unittest.TestCase):
    def test_no_runs_never_awards_rating(self):
        self.assertIsNone(module.evaluate({})["rating"])

    def test_one_run_never_awards_rating(self):
        r = record()
        r["runs"].pop()
        self.assertIsNone(module.evaluate(r)["rating"])

    def test_unverified_case_never_awards_rating(self):
        r = record()
        r["runs"][0]["cases"]["D2"]["status"] = "unverified"
        self.assertIsNone(module.evaluate(r)["rating"])

    def test_missing_evidence_never_awards_rating(self):
        r = record()
        del r["runs"][0]["cases"]["A1"]["evidence"]
        self.assertIsNone(module.evaluate(r)["rating"])

    def test_failed_mandatory_gate_never_awards_rating(self):
        r = record()
        r["runs"][1]["cases"]["F4"]["status"] = "fail"
        self.assertIsNone(module.evaluate(r)["rating"])

    def test_missing_offline_verification_never_awards_rating(self):
        r = record()
        r["runs"][0]["evidence"]["wifi_disabled"] = False
        self.assertIsNone(module.evaluate(r)["rating"])

    def test_malformed_sha_never_awards_rating(self):
        r = record()
        r["runs"][0]["evidence"]["model_sha256"] = "not-a-checksum"
        self.assertIsNone(module.evaluate(r)["rating"])

    def test_duplicate_or_unknown_case_never_awards_rating(self):
        r = record()
        r["runs"][0]["cases"]["X1"] = {"status": "pass", "evidence": "local"}
        self.assertIsNone(module.evaluate(r)["rating"])

    def test_two_complete_runs_report_lower_score_without_stable_claim(self):
        r = record()
        r["runs"][1]["cases"]["A1"]["status"] = "fail"
        result = module.evaluate(r)
        self.assertEqual(result["scores"], [100, 95])
        self.assertEqual(result["rating"], 9.5)
        self.assertTrue(result["campaign_target_met"])
        self.assertFalse(result["stable_release"])

    def test_earned_score_requires_both_runs_above_target(self):
        r = record()
        for case in ("A1", "A2", "A3", "A4"):
            r["runs"][1]["cases"][case]["status"] = "fail"
        result = module.evaluate(r)
        self.assertEqual(result["scores"], [100, 80])
        self.assertFalse(result["campaign_target_met"])


if __name__ == "__main__":
    unittest.main()
