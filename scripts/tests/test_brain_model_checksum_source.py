"""Astra inference startup must reuse bounded, cancellable GGUF SHA checks.

Source contracts are CI guards, not evidence of phone inference or model integrity.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "android/app/src/main/java/com/astra/agent"
BRAIN = (SOURCE / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
DOWNLOAD = (SOURCE / "AstraModelDownload.kt").read_text(encoding="utf-8")


class BrainChecksumWiringTests(unittest.TestCase):
    def test_native_startup_uses_shared_bounded_checksum(self):
        entry = BRAIN[BRAIN.index("private suspend fun sha256(file: File)"): ]
        self.assertIn("AstraModelDownload.checksumStream(input)", entry)
        self.assertNotIn("while (true)", entry)
        self.assertNotIn("digest.update(block, 0, count)", entry)
        self.assertIn("sha256(target).equals(expectedSha256, ignoreCase = true)", BRAIN)

    def test_shared_checksum_limits_stalled_reads_and_checks_cancel(self):
        block = DOWNLOAD[DOWNLOAD.index("internal suspend fun checksumStream(input: InputStream)"): ]
        self.assertIn("check(++emptyReads <= 16)", block)
        self.assertIn("scope.ensureActive()", block)
        self.assertIn("if (read < 0) break", block)
        self.assertIn("emptyReads = 0", block)

    def test_model_policy_remains_one_point_seven_billion_only(self):
        self.assertIn("AstraModelImport.ADVANCED_NAME", BRAIN)
        self.assertIn("Only verified Qwen3 1.7B Q4_K_M", BRAIN)
        self.assertNotIn("Fallback to 0.6B", BRAIN)


if __name__ == "__main__":
    unittest.main()
