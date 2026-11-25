"""Ensure native Qwen timing remains content-free and only success-scored.

Static source checks supplement JVM tests and phone measurements.
"""
from pathlib import Path
import unittest

SRC = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
BRAIN = (SRC / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
MAIN = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
TIMING = (SRC / "AstraInferenceTiming.kt").read_text(encoding="utf-8")

class InferenceTimingWiringTests(unittest.TestCase):
    def test_captures_only_native_generation_success(self):
        self.assertIn("val requestStartedNs = System.nanoTime()", BRAIN)
        self.assertIn("if (firstNativeChunkNs == null)", BRAIN)
        self.assertIn("if (attributedAnswer.isNotBlank() && !budgetExhausted)", BRAIN)
        self.assertIn("lastNativeTiming = AstraInferenceTiming.sample(", BRAIN)
        self.assertIn("AstraInferenceTiming.describe(lastNativeTiming)", BRAIN)

    def test_new_attempt_invalidates_stale_timing_before_inference(self):
        self.assertLess(BRAIN.index("lastNativeTiming = null"),
                        BRAIN.index("val requestStartedNs = System.nanoTime()"))
        self.assertLess(BRAIN.index("val requestStartedNs = System.nanoTime()"),
                        BRAIN.index("lastNativeTiming = AstraInferenceTiming.sample("))

    def test_owner_can_read_without_private_debug_exports(self):
        self.assertIn("onDeviceBrain.lastInferenceTiming()", MAIN)
        self.assertIn("Copy offline timing", MAIN)
        self.assertNotIn("sharedPreferences", TIMING)
        self.assertNotIn("writeText", TIMING)
        self.assertNotIn("Http", TIMING)
        self.assertIn("not tokens", TIMING)
        self.assertNotIn("message.text", TIMING)

if __name__ == "__main__":
    unittest.main()
