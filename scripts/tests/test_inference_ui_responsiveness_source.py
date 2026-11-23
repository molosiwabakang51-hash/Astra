"""Avoid Android main-thread synchronous model checkpoints and stream UI jank.

Static contracts are regressions, not an ANR reproduction or measured speedup.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
POLICY = (JAVA / "AstraStreamingUiPolicy.kt").read_text(encoding="utf-8")
RETRY = (JAVA / "AstraResponseRetry.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class OfflineUiResponsivenessContracts(unittest.TestCase):
    def test_checkpoint_durability_is_moved_to_io_but_retained(self):
        self.assertIn("withContext(Dispatchers.IO) {\n                    AstraInferenceCheckpoint.begin(context)", BRAIN)
        self.assertIn("withContext(NonCancellable + Dispatchers.IO)", BRAIN)
        self.assertIn("AstraInferenceCheckpoint.clear(context)", BRAIN)
        self.assertIn("AstraInferenceCheckpoint.mark(", BRAIN)
        self.assertIn("AstraInferenceCheckpoint.TOKEN_GENERATION", BRAIN)

    def test_streaming_updates_throttled_without_losing_final_answer(self):
        self.assertIn("MIN_PUBLISH_INTERVAL_NS = 550_000_000L", POLICY)
        self.assertIn("AstraStreamingUiPolicy.intervalElapsed", BRAIN)
        self.assertIn("AstraStreamingUiPolicy.hasNewVisibleText", BRAIN)
        self.assertIn("answer.append(part)", BRAIN)
        self.assertIn("answer.finish()", BRAIN)
        self.assertIn("onPartial(partial)", BRAIN)
        self.assertIn("lastPublishedText = partial", BRAIN)

    def test_transient_native_errors_have_existing_retry_surface(self):
        self.assertIn('response.startsWith("Offline model unavailable.")', RETRY)
        self.assertIn("AstraResponseRetry.eligibleQuestion(", MAIN)
        self.assertIn("Retry last response", MAIN)
        self.assertNotIn("HttpURLConnection", POLICY)


if __name__ == "__main__":
    unittest.main()
