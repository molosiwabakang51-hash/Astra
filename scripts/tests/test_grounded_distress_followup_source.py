"""Regression guard for offline grounded distress and immediate-topic anchoring."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CODE = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (CODE / "MainActivity.kt").read_text(encoding="utf-8")
DISTRESS = (CODE / "AstraDistressSupport.kt").read_text(encoding="utf-8")
ANCHOR = (CODE / "AstraFollowUpAnchor.kt").read_text(encoding="utf-8")

class GroundedFollowUpTests(unittest.TestCase):
    def test_support_comes_before_dialogue_and_model(self):
        self.assertIn("AstraDistressSupport.respond(clean)", MAIN)
        self.assertLess(MAIN.index("AstraDistressSupport.respond(clean)"),
                        MAIN.index("dialogue.respond(clean, tasks, notes, completed)"))
        self.assertIn("cannot verify", DISTRESS)
        self.assertIn("Are you physically safe", DISTRESS)
        self.assertIn("local emergency service", DISTRESS)
        for missing in ("http://", "https://", "OkHttp", "java.net"):
            self.assertNotIn(missing, DISTRESS)

    def test_followup_anchors_only_ordinary_same_chat_model_prompts(self):
        self.assertIn("AstraFollowUpAnchor.enrich(", MAIN)
        self.assertIn("if (grounding == null)", MAIN)
        self.assertIn('AstraOnDeviceBrain.Turn("user", focusedPrompt)', MAIN)
        self.assertIn("CURRENT USER FOLLOW-UP", ANCHOR)
        self.assertIn("Previous user question", ANCHOR)
        self.assertIn("untrusted", ANCHOR)
        for forbidden in ("http://", "https://", "java.net", "OkHttp", "writeInt("):
            self.assertNotIn(forbidden, ANCHOR)

if __name__ == "__main__":
    unittest.main()
