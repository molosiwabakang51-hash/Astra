"""Verified calculation followups remain independent of Qwen and local actions.

Static checks cannot establish that unrelated open-ended LLM reasoning improved.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
FOLLOW = (JAVA / "AstraVerifiedCalculationFollowUp.kt").read_text(encoding="utf-8")
ARITH = (JAVA / "AstraVerifiedArithmetic.kt").read_text(encoding="utf-8")


class VerifiedMathFollowupWiringTests(unittest.TestCase):
    def test_verification_takes_priority_over_workflow_actions(self):
        self.assertLess(
            MAIN.index("AstraVerifiedCalculationFollowUp.explain("),
            MAIN.index("val workflowSnapshot =")
        )
        self.assertIn("AstraVerifiedArithmetic.verifiedWorking(", FOLLOW)
        self.assertIn("val result = answer(originalQuestion)", ARITH)
        self.assertIn("result != observedAnswer.trim()", ARITH)
        self.assertIn("Recomputed on this device using exact decimal arithmetic", ARITH)

    def test_bounded_contiguous_verified_same_chat_pairs_only(self):
        self.assertIn("MAX_CHAIN_PAIRS = 4", FOLLOW)
        self.assertIn("activeChat.size - 2", FOLLOW)
        self.assertIn('user.role != "You"', FOLLOW)
        self.assertIn('assistant.role != "Astra"', FOLLOW)
        self.assertIn("AstraInterruptedReply.isIncomplete", FOLLOW)
        self.assertIn("if (!isFollowUp(user.text)) return null", FOLLOW)
        self.assertIn("followUps.all { reply -> reply.text == verified }", FOLLOW)
        self.assertIn("request.any", FOLLOW)

    def test_no_private_memory_network_or_side_effects(self):
        self.assertNotIn("Http", FOLLOW)
        self.assertNotIn("https://", FOLLOW)
        self.assertNotIn("AstraBookLibrary", FOLLOW)
        self.assertNotIn("commitWorkflow", FOLLOW)
        self.assertIn("AstraVerifiedCalculationFollowUp.explain(", MAIN)


if __name__ == "__main__":
    unittest.main()
