"""Regression tests for evidence-based phone score reporting."""
import unittest
from scripts.score_phone_quality import evaluate, WEIGHTS, REQUIRED_METADATA


def full_run():
    metadata = {key: "verified" for key in REQUIRED_METADATA}
    metadata["airplane_mode"] = True
    metadata["wifi_disabled"] = True
    return {"metadata": metadata, "cases": {
        f"{domain}{n}": "pass"
        for domain, (count, _) in WEIGHTS.items()
        for n in range(1, count + 1)
    }}


class PhoneQualityScoreTest(unittest.TestCase):
    def test_two_complete_runs_can_reach_target(self):
        report = evaluate({"runs": [full_run(), full_run()]})
        self.assertTrue(report["target_8_5_verified"])
        self.assertEqual(report["runs"][0]["points"], 100)

    def test_missing_case_never_gets_score(self):
        run = full_run()
        del run["cases"]["A1"]
        result = evaluate({"runs": [run]})["runs"][0]
        self.assertIsNone(result["points"])
        self.assertIn("A1", result["missing_cases"])

    def test_unverified_gate_never_gets_score(self):
        run = full_run()
        run["cases"]["F1"] = "unverified"
        self.assertIsNone(evaluate({"runs": [run]})["runs"][0]["points"])

    def test_failed_mandatory_gate_blocks_target(self):
        run = full_run()
        run["cases"]["F4"] = "fail"
        report = evaluate({"runs": [run, full_run()]})
        self.assertFalse(report["target_8_5_verified"])
        self.assertEqual(report["runs"][0]["points"], 98)

    def test_network_connected_blocks_offline_verification(self):
        run = full_run()
        run["metadata"]["wifi_disabled"] = False
        self.assertIsNone(evaluate({"runs": [run]})["runs"][0]["points"])

    def test_missing_latency_evidence_blocks_score(self):
        run = full_run()
        del run["metadata"]["time_to_first_token_ms"]
        self.assertIsNone(evaluate({"runs": [run]})["runs"][0]["points"])

    def test_unknown_case_rejected(self):
        run = full_run()
        run["cases"]["X1"] = "pass"
        with self.assertRaises(ValueError):
            evaluate({"runs": [run]})

    def test_boolean_pass_is_rejected(self):
        run = full_run()
        run["cases"]["A1"] = True
        with self.assertRaises(ValueError):
            evaluate({"runs": [run]})


if __name__ == "__main__":
    unittest.main()
