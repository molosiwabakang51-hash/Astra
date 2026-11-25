"""Regression contract for nonblank final answers after citation filtering."""
from pathlib import Path
import unittest

SRC = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
BRAIN = (SRC / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
INTERRUPTED = (SRC / "AstraInterruptedReply.kt").read_text(encoding="utf-8")


class GuardedReplyContracts(unittest.TestCase):
    def test_visible_final_answer_controls_success_and_recovery(self):
        # The source guard may remove a model-invented citation-only answer.
        self.assertIn("val attributedAnswer = AstraSourceCitationGuard.protect(", BRAIN)
        self.assertIn("if (attributedAnswer.isNotBlank() && !budgetExhausted) {", BRAIN)
        self.assertIn("if (attributedAnswer.isBlank()) {", BRAIN)
        self.assertNotIn("if (publicAnswer.isBlank()) {", BRAIN)
        self.assertNotIn("if (publicAnswer.isNotBlank()) {", BRAIN)

    def test_empty_visible_answer_invalidates_native_session(self):
        start = BRAIN.index("if (attributedAnswer.isBlank()) {")
        end = BRAIN.index("} else if (citedBooks.isNotEmpty())", start)
        recovery = BRAIN[start:end]
        self.assertIn("session.invalidate()", recovery)
        self.assertIn("loadedModelPath = null", recovery)
        self.assertIn("AstraInterruptedReply.EMPTY_MODEL_REPLY", recovery)
        self.assertIn("The offline model did not produce a usable answer.", INTERRUPTED)
        self.assertIn("value == EMPTY_MODEL_REPLY", INTERRUPTED)
        self.assertNotIn("attributedAnswer +", recovery)


if __name__ == "__main__":
    unittest.main()
