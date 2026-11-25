"""Source contract for explicit same-chat review without model hallucination."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
REVIEW = (SRC / "AstraChatReview.kt").read_text(encoding="utf-8")

class SameChatReviewSourceTest(unittest.TestCase):
    def test_review_is_routed_before_native_inference(self):
        self.assertIn("AstraChatReview.respond(", MAIN)
        self.assertLess(MAIN.index("AstraChatReview.respond("),
                        MAIN.index("val focusedPrompt = if (grounding == null && !modelOnly)"))
        self.assertIn('history.add(ChatMessage("Astra", chatReview))', MAIN)

    def test_review_is_private_and_does_not_promote_assistant_text(self):
        self.assertNotIn("archivedChats", REVIEW)
        self.assertNotIn("AstraOnDeviceBrain", REVIEW)
        self.assertIn("quotes, not automatically verified", REVIEW)
        # Preserve both-role review while requiring the narrower assistant-only scope.
        self.assertIn('turn.role == "Astra" || (turn.role == "You" && !request.assistantOnly)', REVIEW)
        self.assertIn('ask != null && ask.groupValues[1] != "we"', REVIEW)
        self.assertIn('turn.text.startsWith(REPORT_HEADER)', REVIEW)
        self.assertIn('turn.text.startsWith(EMPTY_PREFIX)', REVIEW)
        self.assertIn('parse(turn.text) != null', REVIEW)

if __name__ == "__main__":
    unittest.main()
