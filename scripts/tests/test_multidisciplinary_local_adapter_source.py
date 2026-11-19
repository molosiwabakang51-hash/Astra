"""Verify broad opt-in local neural topic training and backward compatible persistence."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
ADAPTER = (JAVA / "AstraContinualLearning.kt").read_text(encoding="utf-8")
CONSENT = (JAVA / "AstraLearningSignals.kt").read_text(encoding="utf-8")

class MultidisciplinaryAdapterSourceTest(unittest.TestCase):
    def test_twenty_local_labels_and_source_classes(self):
        match = re.search(r"enum class Topic\s*\{([^}]+)\}", ADAPTER)
        self.assertIsNotNone(match)
        entries = [value.strip() for value in match.group(1).split(",")]
        self.assertEqual(20, len(entries))
        self.assertEqual(["RUSSIAN", "FINANCE", "GENERAL"], entries[:3])
        for expected in ["FRENCH", "SETSWANA", "TRADING", "MATHEMATICS",
                         "PHYSICS", "COMPUTING", "BIOLOGY", "HISTORY"]:
            self.assertIn(expected, entries)
            self.assertIn("Topic." + expected + " to Regex", ADAPTER)

    def test_does_not_modify_qwen_weights_or_send_private_text(self):
        self.assertIn("private fun teacherLabel(message: String)", ADAPTER)
        self.assertIn("train after opted-in", ADAPTER.lower())
        self.assertIn("ASTRA_NEURAL_TOPIC_V2", ADAPTER)
        self.assertIn("ASTRA_NEURAL_TOPIC_V1", ADAPTER)
        self.assertIn("for (row in 0 until 3)", ADAPTER)
        self.assertIn("readWeight()", ADAPTER)
        self.assertIn("AstraLearningSignals", CONSENT)
        for forbidden in ["http://", "https://", "OkHttp", "gguf", "Socket("]:
            self.assertNotIn(forbidden, ADAPTER)

if __name__ == "__main__":
    unittest.main()
