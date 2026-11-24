"""Regression contract: cancelled native generation remains busy until cleanup finishes."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "android/app/src/main/java/com/astra/agent/MainActivity.kt"


class NativeCancellationBusyGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_chat_transition_retains_job_until_finally(self):
        start = self.source.index("fun interruptOfflineForTransition()")
        end = self.source.index("\n                }", start)
        transition = self.source[start:end]
        self.assertIn("offlineJob?.cancel()", transition)
        self.assertNotIn("offlineJob = null", transition)

    def test_cleanup_clears_only_own_job(self):
        self.assertIn("if (offlineJob === runningJob)", self.source)
        self.assertIn("offlineJob = null", self.source)

    def test_send_and_retry_block_during_cancel_drain(self):
        self.assertIn("if (offlineJob != null)", self.source)
        self.assertIn("offlineJob != null || modelImportRunning ||", self.source)
        self.assertIn("if (offlineJob == null) submit(input)", self.source)


if __name__ == "__main__":
    unittest.main()
