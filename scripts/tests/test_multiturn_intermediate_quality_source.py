"""Protect real-Qwen multi-turn evaluation from misleading pass counts.

Intermediate turn obedience and final answer accuracy are two separate
metrics. CI source checks do not create an on-phone performance result.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
BENCH = (JAVA / "AstraMultiTurnReasoningBenchmark.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class IntermediateQualityEvidenceTests(unittest.TestCase):
    def test_score_intermediate_only_on_native_responded_turns(self):
        self.assertIn("fun interimMatches(", BENCH)
        self.assertIn("turnIndex !in 0..1", BENCH)
        self.assertIn("scenario.interimExpected[turnIndex]", BENCH)
        self.assertIn("scenario, turnIndex, answer", MAIN)
        self.assertIn("followedEarlyInstructions++", MAIN)
        self.assertIn("followedEarlyInstructions", MAIN)

    def test_keep_final_answers_as_distinct_narrow_metric(self):
        self.assertIn("strict final-answer checks passed.", BENCH)
        self.assertIn("earlier-turn instruction checks passed.", BENCH)
        self.assertIn("results.all { it.interimPassed in 0..2 }", BENCH)
        self.assertIn("strict final-answer checks", BENCH)

    def test_still_isolated_no_uploaded_content(self):
        self.assertIn("diagnosticIsolated = true", MAIN)
        self.assertIn("No private source data", BENCH)
        self.assertNotIn("HttpURLConnection", BENCH)
        self.assertNotIn("AstraBookLibrary", BENCH)


if __name__ == "__main__":
    unittest.main()
