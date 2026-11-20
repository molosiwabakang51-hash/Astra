"""Protect native stream output when JNI splits a UTF-16 emoji across chunks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "android/app/src/main/java/com/astra/agent"
BRAIN = (BASE / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
STREAM = (BASE / "AstraStreamUnicode.kt").read_text(encoding="utf-8")


class NativeSplitSurrogateTests(unittest.TestCase):
    def test_inference_stream_uses_pair_aware_accumulator(self):
        self.assertIn("val answer = AstraStreamUnicode.StreamingBuffer(16_000)", BRAIN)
        self.assertIn("answer.append(part)", BRAIN)
        self.assertIn("AstraOfflinePolicy.publicReply(answer.snapshot())", BRAIN)
        self.assertIn("AstraOfflinePolicy.publicReply(answer.finish())", BRAIN)
        self.assertNotIn("AstraStreamUnicode.appendCapped(\n                            answer, part", BRAIN)

    def test_pending_high_surrogate_never_exposes_partial_character(self):
        self.assertIn("private var pendingHigh: Char? = null", STREAM)
        self.assertIn("if (next.isLowSurrogate())", STREAM)
        self.assertIn("text.append(high).append(low)", STREAM)
        self.assertIn("appendCodeUnit(", STREAM)
        self.assertIn("fun snapshot(): String = text.toString()", STREAM)
        self.assertIn("fun finish(): String", STREAM)

    def test_generation_limits_remain_bounded_and_stable(self):
        self.assertIn("private var truncated = false", STREAM)
        self.assertIn("if (maxUnits - text.length < 2)", STREAM)
        self.assertIn("truncated = true", STREAM)
        self.assertIn("if (truncated || chunk.isEmpty()) return truncated", STREAM)
        self.assertNotIn("AstraModelDownload", STREAM)
        self.assertNotIn("HttpsURLConnection", STREAM)


if __name__ == "__main__":
    unittest.main()
