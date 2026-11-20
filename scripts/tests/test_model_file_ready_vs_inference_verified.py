"""Source gates: GGUF file verification must not masquerade as working offline AI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
IMPORT = (APP / "AstraModelImportCoordinator.kt").read_text(encoding="utf-8")
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
SELF_TEST = (APP / "AstraOfflineSelfTest.kt").read_text(encoding="utf-8")


class ImportedModelReadinessSourceTests(unittest.TestCase):
    def test_gguf_import_says_file_verified_not_inference_verified(self):
        self.assertIn("verifiedFileMessage(bytes)", IMPORT)
        self.assertIn("QWEN3 1.7B FILE VERIFIED", IMPORT)
        self.assertIn("Run Settings → Test on-device AI", IMPORT)
        self.assertNotIn("Offline conversation is available.", IMPORT)
        self.assertNotIn("QWEN3 1.7B READY", IMPORT)

    def test_size_only_model_detection_remains_non_certifying(self):
        self.assertIn("QWEN3 1.7B FILE DETECTED", MAIN)
        self.assertIn("TEST ON-DEVICE AI BEFORE TRUSTING INFERENCE", MAIN)
        self.assertNotIn("QWEN3 1.7B AVAILABLE · REQUIRED CONVERSATION MODEL", MAIN)

    def test_strict_offline_generation_challenge_is_still_separate(self):
        self.assertIn("AstraOfflineSelfTest.CHALLENGE", MAIN)
        self.assertIn("AstraOfflineSelfTest", SELF_TEST)
        self.assertIn("OFFLINE TEST VERIFIED", MAIN)


if __name__ == "__main__":
    unittest.main()
