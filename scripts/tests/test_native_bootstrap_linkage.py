"""Guard that missing/incompatible JNI libraries cannot crash past owner UI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
BOOTSTRAP = (JAVA / "AstraNativeBootstrap.kt").read_text(encoding="utf-8")
PRIVACY = (JAVA / "AstraPrivateError.kt").read_text(encoding="utf-8")


class NativeStartupLinkageTests(unittest.TestCase):
    def test_real_engine_construction_runs_within_linkage_boundary(self):
        self.assertIn("val current = engine ?: AstraNativeBootstrap.acquire {", BRAIN)
        self.assertIn("AiChat.getInferenceEngine(context.applicationContext)", BRAIN)
        self.assertLess(BRAIN.index("AstraNativeBootstrap.acquire {"),
                        BRAIN.index("AiChat.getInferenceEngine(context.applicationContext)"))
        self.assertLess(BRAIN.index("AiChat.getInferenceEngine(context.applicationContext)"),
                        BRAIN.index("current.loadModel(path.absolutePath)"))

    def test_only_java_linkage_failures_are_translated(self):
        self.assertIn("catch (failure: LinkageError)", BOOTSTRAP)
        self.assertIn("IllegalStateException(RECOVERY_ADVICE, failure)", BOOTSTRAP)
        self.assertIn("throw recover(failure)", BOOTSTRAP)
        self.assertIn("catch (linkage: LinkageError)", BRAIN)
        self.assertIn("throw AstraNativeBootstrap.recover(linkage)", BRAIN)
        self.assertIn("session.invalidate()", BRAIN)
        self.assertLess(
            BRAIN.index("catch (linkage: LinkageError)"),
            BRAIN.index("catch (error: Exception)", BRAIN.index("catch (linkage: LinkageError)"))
        )
        self.assertNotIn("catch (failure: Throwable)", BOOTSTRAP)
        self.assertNotIn("catch (failure: Error)", BOOTSTRAP)
        self.assertNotIn("OutOfMemoryError", BOOTSTRAP.split("fun <T> acquire")[1])

    def test_error_boundary_does_not_use_cloud_or_delete_models(self):
        self.assertIn("Qwen3 1.7B", BOOTSTRAP)
        self.assertIn("downloaded GGUF", BOOTSTRAP)
        for phrase in ("delete(", "contentResolver", "URL(", "HttpURLConnection",
                       "AstraModelDownload.download", "OpenAI", "Retrofit"):
            self.assertNotIn(phrase, BOOTSTRAP)
        self.assertIn("Operation.MODEL_CHAT", PRIVACY)
        self.assertIn("Cause.MODEL", PRIVACY)


if __name__ == "__main__":
    unittest.main()
