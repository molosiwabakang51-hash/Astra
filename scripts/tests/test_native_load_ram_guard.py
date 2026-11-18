"""Bounded source regression for the native-load RAM safety preflight.

No claims of arm64 runtime stability: those require device tests and logcat.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
GUARD = (JAVA / "AstraNativeLoadGuard.kt").read_text(encoding="utf-8")
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")


class NativeLoadRamGuardSourceTest(unittest.TestCase):
    def test_policy_is_device_reported_not_arbitrary_model_size_guess(self):
        self.assertIn("memory.lowMemory", GUARD)
        self.assertIn("memory.availMem", GUARD)
        self.assertIn("memory.threshold", GUARD)
        self.assertIn("context.applicationContext.getSystemService(", GUARD)
        self.assertNotIn("http://", GUARD)
        self.assertNotIn("https://", GUARD)
        self.assertNotIn("modelFile(", GUARD)

    def test_preflight_only_on_fresh_native_load_not_each_chat_turn(self):
        assert BRAIN.count("AstraNativeLoadGuard.requireCapacity(context)") == 1
        self.assertIn(
            "suspend fun loadPreparedModel(path: File) {\n"
            "                        // Android's own low-memory signal", BRAIN
        )
        self.assertIn(
            "if (loadError is AstraNativeLowMemoryException)", BRAIN
        )
        self.assertIn("AstraInferenceCheckpoint.clear(context)", BRAIN)


if __name__ == "__main__":
    unittest.main()
