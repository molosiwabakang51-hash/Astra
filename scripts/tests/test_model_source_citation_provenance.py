"""Protect provenance of sources cited by on-device Qwen.

These are static source checks, not evidence of phone inference or correctness.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
GUARD = (JAVA / "AstraSourceCitationGuard.kt").read_text(encoding="utf-8")

class ModelCitationProvenanceSourceTests(unittest.TestCase):
    def test_sanitize_model_output_before_app_owned_footer(self):
        self.assertIn("AstraSourceCitationGuard.protect(", BRAIN)
        self.assertIn("verifiedPrivatePassages = citedBooks.size", BRAIN)
        self.assertIn("verifiedOpenPassages = citedOpenCourse.size", BRAIN)
        self.assertLess(
            BRAIN.index("AstraSourceCitationGuard.protect("),
            BRAIN.index('Retrieved local book passages (verify originals):')
        )
        self.assertIn("attributedAnswer +", BRAIN)

    def test_only_real_passage_indices_can_survive(self):
        self.assertIn("verifiedPrivatePassages in 0..2", GUARD)
        self.assertIn("verifiedOpenPassages in 0..3", GUARD)
        self.assertIn("number in 1..allowed", GUARD)
        self.assertIn("unverified citation omitted", GUARD)
        self.assertIn("unverified AI source-heading omitted", GUARD)

    def test_no_private_file_io_or_network_or_action_execution(self):
        self.assertNotIn("HttpURLConnection", GUARD)
        self.assertNotIn("http://", GUARD)
        self.assertNotIn("https://", GUARD)
        self.assertNotIn("AstraBookLibrary", GUARD)
        self.assertNotIn("commitWorkflow", GUARD)


if __name__ == "__main__":
    unittest.main()
