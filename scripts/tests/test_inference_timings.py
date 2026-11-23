"""Tests for content-free offline inference timing analysis."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "astra_timing_analysis", ROOT / "scripts" / "quality" / "analyze_inference_timings.py"
)
analysis = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(analysis)

HEADER = "run_id,mode,first_output_ms,total_ms,completed,anr\n"


class TimingAnalysisTests(unittest.TestCase):
    def parse_text(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "local.csv"
            path.write_text(data, encoding="utf-8")
            return analysis.parse(path)

    def test_valid_data_and_p95(self):
        rows = self.parse_text(HEADER + "1,cold,100,500,1,0\n2,warm,40,200,1,0\n3,warm,80,300,1,0\n")
        report = analysis.summarize(rows)
        self.assertEqual(60, report["warm"]["first_output_p50_ms"])
        self.assertAlmostEqual(78, report["warm"]["first_output_p95_ms"])
        self.assertEqual(1, report["cold"]["successful"])

    def test_failed_and_anr_not_counted_as_success(self):
        rows = self.parse_text(HEADER + "1,warm,0,1000,0,0\n2,warm,100,5000,1,1\n")
        report = analysis.summarize(rows)["warm"]
        self.assertEqual(0, report["successful"])
        self.assertEqual(1, report["blank_or_failed"])
        self.assertEqual(1, report["anr"])
        self.assertIsNone(report["total_p95_ms"])

    def test_reject_private_columns(self):
        with self.assertRaises(ValueError):
            self.parse_text(HEADER.strip() + ",prompt\n1,warm,1,2,1,0,secret\n")

    def test_reject_invalid_and_inconsistent_times(self):
        with self.assertRaises(ValueError):
            self.parse_text(HEADER + "1,warm,300,200,1,0\n")
        with self.assertRaises(ValueError):
            self.parse_text(HEADER + "1,warm,-1,200,1,0\n")

    def test_reject_empty_measurements(self):
        with self.assertRaises(ValueError):
            self.parse_text(HEADER)


if __name__ == "__main__":
    unittest.main()
