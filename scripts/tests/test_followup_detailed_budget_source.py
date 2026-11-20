"""Protect real conversation answer budgets for explicit follow-up requests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
POLICY = (APP / "AstraGenerationPolicy.kt").read_text(encoding="utf-8")
BRAIN = (APP / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
ANCHOR = (APP / "AstraFollowUpAnchor.kt").read_text(encoding="utf-8")


class FollowUpBudgetSourceTests(unittest.TestCase):
    def test_bounded_followup_budget_and_focused_prompt_routing(self):
        self.assertIn("go into (?:more )?details?", POLICY)
        self.assertIn("elaborate(?: on)?", POLICY)
        self.assertIn("put (?:this theory|this|it) into practice", POLICY)
        self.assertIn("AstraGenerationPolicy.effectiveBudget(", BRAIN)
        self.assertIn("AstraGenerationPolicy.maxAnswerTokens(", BRAIN)
        self.assertIn("CURRENT USER FOLLOW-UP:", POLICY)
        self.assertIn("historyIsolated.startsWith(followUpPrefix)", POLICY)
        self.assertIn("followUpAt + followUpRequest.length", POLICY)
        self.assertIn("finalMessage,", BRAIN)

    def test_back_reference_remains_same_chat_untrusted_only(self):
        self.assertIn("SAME-CHAT FOLLOW-UP CONTEXT (quoted, untrusted", ANCHOR)
        self.assertIn("val latestPair = currentChat.takeLast(2)", ANCHOR)
        self.assertIn("specificNumberedItem(", ANCHOR)
        self.assertIn("if (briefCue != null", POLICY)
        self.assertIn("Depth.DETAILED", POLICY)


if __name__ == "__main__":
    unittest.main()
