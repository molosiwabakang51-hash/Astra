"""Regression contracts for exclusive Qwen3 1.7B generation and cleanup.

These checks are static safeguards, not evidence that a blocked JNI call can be
interrupted or that an Android handset is free of ANRs.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class NativeExclusivityContracts(unittest.TestCase):
    def test_entire_inference_and_cleanup_remain_under_mutex(self):
        start = BRAIN.index("suspend fun reply(")
        end = BRAIN.index("/** Verify the requested 1.7B GGUF", start)
        body = BRAIN[start:end]
        self.assertRegex(body, r"\): String = AstraModelOperationGate\.withNativeSession\s*\{\s*mutex\.withLock\s*\{")
        self.assertLess(body.index("AstraModelOperationGate.withNativeSession"), body.index("mutex.withLock"))
        self.assertLess(body.index("mutex.withLock"), body.index("sendUserPrompt("))
        self.assertLess(body.index("sendUserPrompt("), body.index("finally {"))
        self.assertLess(body.index("finally {"), body.index("AstraInferenceCheckpoint.clear(context)"))

    def test_timeout_is_cooperative_not_a_second_worker(self):
        self.assertIn("kotlinx.coroutines.withTimeoutOrNull(", BRAIN)
        self.assertIn("currentCoroutineContext().ensureActive()", BRAIN)
        self.assertIn("check(completed)", BRAIN)
        self.assertNotIn("GlobalScope.launch", BRAIN)
        self.assertNotIn("Thread.stop(", BRAIN)

    def test_failed_generation_invalidates_context_before_retry(self):
        start = BRAIN.index("} catch (error: Exception)")
        end = BRAIN.index("} finally {", start)
        recovery = BRAIN[start:end]
        self.assertIn("session.invalidate()", recovery)
        self.assertIn("loadedModelPath = null", recovery)
        self.assertLess(recovery.index("session.invalidate()"), recovery.index("throw error"))

    def test_cleanup_is_non_cancellable_and_off_main_thread(self):
        start = BRAIN.index("} finally {")
        end = BRAIN.index("/** Verify the requested 1.7B GGUF", start)
        cleanup = BRAIN[start:end]
        self.assertIn("withContext(NonCancellable + Dispatchers.IO)", cleanup)
        self.assertIn("AstraInferenceCheckpoint.clear(context)", cleanup)

    def test_no_legacy_model_fallback(self):
        self.assertIn("AstraModelImport.ADVANCED_NAME", BRAIN)
        self.assertIn("Only verified Qwen3 1.7B Q4_K_M", BRAIN)
        self.assertNotIn("Qwen3 0.6B is required", BRAIN)


    def test_global_gate_and_safe_model_promotions_are_wired(self):
        gate = (JAVA / "AstraModelOperationGate.kt").read_text(encoding="utf-8")
        imported = (JAVA / "AstraModelImport.kt").read_text(encoding="utf-8")
        downloaded = (JAVA / "AstraModelDownload.kt").read_text(encoding="utf-8")
        recovered = (JAVA / "AstraCompletedModelDownload.kt").read_text(encoding="utf-8")
        storage = (JAVA / "AstraModelStorage.kt").read_text(encoding="utf-8")
        self.assertIn("private val nativeOrPromotion = Mutex()", gate)
        self.assertIn("nativeOrPromotion.withLock { action() }", gate)
        self.assertIn("withImport { withContext(Dispatchers.IO)", imported)
        self.assertIn("withDownload { withContext(Dispatchers.IO)", downloaded)
        for source in (imported, downloaded, recovered):
            self.assertIn("AstraModelOperationGate.withPromotion {", source)
        self.assertIn("@Synchronized\n    fun activeModel", storage)
        self.assertIn("@Synchronized\n    fun installVerified", storage)
        self.assertNotIn("Thread.stop(", gate)



if __name__ == "__main__":
    unittest.main()
