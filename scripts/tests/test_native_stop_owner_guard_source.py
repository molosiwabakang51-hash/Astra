"""Owner authorization and JNI Stop signal must be a single atomic operation."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
GATE = (ROOT / "AstraModelOperationGate.kt").read_text(encoding="utf-8")
BRAIN = (ROOT / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")


class NativeStopOwnerGuardSourceTest(unittest.TestCase):
    def test_last_kv_owner_is_distinct_from_active_abort_owner(self):
        self.assertIn("private var lastNativeOwnerId: Long? = null", GATE)
        self.assertIn("@Volatile private var activeNativeOwnerId: Long? = null", GATE)
        self.assertIn("private val nativeStopMonitor = Any()", GATE)
        self.assertIn("activeNativeOwnerId = ownerId", GATE)

    def test_owner_check_and_actual_stop_signal_share_one_lock(self):
        start = GATE.index("fun signalStopIfCurrentOwner(")
        end = GATE.index("fun newOwnerId()", start)
        method = GATE[start:end]
        self.assertIn("return synchronized(nativeStopMonitor) {", method)
        self.assertIn("if (activeNativeOwnerId != ownerId) false", method)
        self.assertLess(method.index("activeNativeOwnerId != ownerId"),
                        method.index("signal()"))
        self.assertNotIn("Thread.stop(", method)
        self.assertNotIn("nativeOrPromotion.withLock", method)

    def test_claim_release_and_promotion_use_same_stop_monitor(self):
        start = GATE.index("fun claimNativeSession(")
        end = GATE.index("/** Two imports", start)
        body = GATE[start:end]
        self.assertEqual(3, body.count("synchronized(nativeStopMonitor) {"))
        self.assertIn("activeNativeOwnerId = ownerId", body)
        self.assertEqual(2, body.count("activeNativeOwnerId = null"))
        self.assertIn("finally {", body)
        self.assertIn("nativeOrPromotion.withLock", body)

    def test_brain_checks_session_state_within_atomic_authorization(self):
        start = BRAIN.index("fun requestGenerationStop()")
        end = BRAIN.index("fun lastInferenceTiming()", start)
        stop = BRAIN[start:end]
        self.assertIn("AstraModelOperationGate.signalStopIfCurrentOwner(nativeOwnerId) {", stop)
        self.assertLess(stop.index("signalStopIfCurrentOwner(nativeOwnerId) {"),
                        stop.index("val state = current.state.value"))
        self.assertLess(stop.index("val state = current.state.value"),
                        stop.index("current.requestAbort()"))
        self.assertIn("catch (_: LinkageError)", stop)
        self.assertNotIn("isActiveNativeOwner", stop)

    def test_atomic_stop_does_not_load_or_unload_native_model(self):
        self.assertNotIn("Thread.stop(", GATE)
        self.assertNotIn("GlobalScope", GATE)
        self.assertNotIn("SharedPreferences", GATE)
        self.assertNotIn("loadModel(", GATE)
        self.assertNotIn("promptText", GATE)


if __name__ == "__main__":
    unittest.main()
