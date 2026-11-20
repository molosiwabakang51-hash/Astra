"""CI guard: the file-identity failure quarantine must reach native load."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "android/app/src/main/java/com/astra/agent/AstraOnDeviceBrain.kt"
HEALTH = ROOT / "android/app/src/main/java/com/astra/agent/AstraModelHealth.kt"


class NativeFailureQuarantineWiring(unittest.TestCase):
    def test_gate_is_applied_to_actual_native_model_loading(self):
        text = SRC.read_text(encoding="utf-8")
        guard = "check(modelHealth.shouldAttempt(path))"
        native = "current.loadModel(path.absolutePath)"
        failure = "modelHealth.recordFailure(path)"
        self.assertIn("private val modelHealth = AstraModelHealth()", text)
        self.assertIn("AstraNativeLoadGuard.requireCapacity(context)", text)
        self.assertIn(guard, text)
        self.assertIn(native, text)
        self.assertIn(failure, text)
        self.assertLess(text.index(guard), text.index(native))
        self.assertLess(text.index(native), text.index(failure))
        self.assertIn("catch (cancel: kotlinx.coroutines.CancellationException)", text)

    def test_backoff_identity_does_not_just_match_filename_or_timestamp(self):
        policy = HEALTH.read_text(encoding="utf-8")
        self.assertIn("BasicFileAttributes::class.java", policy)
        self.assertIn("attrs.fileKey()", policy)
        self.assertIn("Identity(file.absolutePath", policy)
        self.assertIn("identity(advancedFile)?.let { it != failedAdvanced } ?: true", policy)

    def test_failed_model_is_not_substituted_with_smaller_weights(self):
        text = SRC.read_text(encoding="utf-8")
        self.assertIn("AstraModelImport.ADVANCED_NAME", text)
        self.assertIn("AstraModelImport.ADVANCED_SHA256", text)
        self.assertIn("loadPreparedModel(modelPath)", text)
        self.assertNotIn('const val ASSET_NAME = "qwen3-0.6b-q4km.gguf"', text)


if __name__ == "__main__":
    unittest.main()
