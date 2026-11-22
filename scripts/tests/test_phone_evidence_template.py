"""CI checks for the private handset evidence template, not handset scores."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/quality/create_phone_evidence_template.py"
spec = importlib.util.spec_from_file_location("phone_template", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PhoneTemplateTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((ROOT / "scripts/quality/scorecard_cases.json").read_text(encoding="utf-8"))

    def test_two_runs_start_unverified(self):
        record = module.blank_record(self.manifest)
        self.assertEqual(len(record["runs"]), 2)
        for run in record["runs"]:
            self.assertEqual(len(run["cases"]), 30)
            self.assertTrue(all(x == {"status": "unverified"} for x in run["cases"].values()))
            self.assertFalse(run["evidence"]["owner_signed_upgrade"])
            self.assertFalse(run["evidence"]["airplane_mode"])
            self.assertEqual(run["evidence"]["model_sha256"], "")

    def test_invalid_manifest_fails_closed(self):
        invalid = {"cases": self.manifest["cases"][:-1]}
        with self.assertRaises(ValueError):
            module.blank_record(invalid)

    def test_independent_runs_do_not_share_mutable_cases(self):
        record = module.blank_record(self.manifest)
        record["runs"][0]["cases"]["A1"]["status"] = "pass"
        self.assertEqual(record["runs"][1]["cases"]["A1"]["status"], "unverified")


if __name__ == "__main__":
    unittest.main()
