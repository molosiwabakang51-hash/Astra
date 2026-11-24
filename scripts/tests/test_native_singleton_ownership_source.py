"""Cross-Activity native singleton KV-cache ownership must not be assumed."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
GATE = (JAVA / "AstraModelOperationGate.kt").read_text(encoding="utf-8")


class NativeSessionIdentityContracts(unittest.TestCase):
    def test_each_brain_has_distinct_unshared_owner_id(self):
        self.assertIn("private val nativeOwnerId = AstraModelOperationGate.newOwnerId()", BRAIN)
        self.assertIn("private val nextOwnerId = AtomicLong()", GATE)
        self.assertIn("fun newOwnerId(): Long = nextOwnerId.incrementAndGet()", GATE)

    def test_claim_invalidates_local_native_session_before_model_reuse(self):
        start = BRAIN.index("suspend fun reply(")
        finish = BRAIN.index("private suspend fun ensureModel(", start)
        reply = BRAIN[start:finish]
        self.assertIn("AstraModelOperationGate.withNativeSession", reply)
        self.assertIn("AstraModelOperationGate.claimNativeSession(nativeOwnerId)", reply)
        self.assertLess(reply.index("claimNativeSession(nativeOwnerId)"), reply.index("ensureModel("))
        self.assertIn("session.invalidate()", reply)
        owner_guard = reply[reply.index("if (AstraModelOperationGate.claimNativeSession("):]
        self.assertLess(owner_guard.index("session.invalidate()"), owner_guard.index("loadedModelPath = null"))

    def test_promotion_invalidates_any_previous_brain_owner(self):
        self.assertIn("private var lastNativeOwnerId: Long? = null", GATE)
        self.assertIn("lastNativeOwnerId = null", GATE)
        self.assertIn("finally {", GATE)
        self.assertIn("lastNativeOwnerId != ownerId", GATE)

    def test_ownership_tracking_does_not_store_private_model_or_chat_data(self):
        self.assertNotIn("UserPrompt", GATE)
        self.assertNotIn("conversationText", GATE)
        self.assertNotIn("SharedPreferences", GATE)
        self.assertNotIn("GlobalScope", GATE)
        self.assertNotIn("Thread.stop(", GATE)


if __name__ == "__main__":
    unittest.main()
