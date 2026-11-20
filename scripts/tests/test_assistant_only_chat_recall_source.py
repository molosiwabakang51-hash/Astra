"""Astra's own earlier replies are reviewable, but never user memory or verified facts."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
REVIEW = (APP / "AstraConversationReview.kt").read_text(encoding="utf-8")
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
MEMORY = (APP / "AstraMemoryRecall.kt").read_text(encoding="utf-8")


class AssistantOnlyChatReviewSourceTest(unittest.TestCase):
    def test_explicit_assistant_recall_uses_read_only_same_chat_review(self):
        self.assertIn("private val assistantRecall = Regex(", REVIEW)
        self.assertIn("assistantRecall.matchEntire(command)", REVIEW)
        self.assertIn("earlierMentions(term, history, onlyAssistant = true)", REVIEW)
        self.assertIn('onlyAssistant && entry.value.role != "Astra"', REVIEW)
        self.assertIn("Previous Astra responses were checked.", REVIEW)

    def test_production_routes_prior_chat_only_without_promoting_assistant_text(self):
        self.assertIn("AstraConversationReview.respond(", MAIN)
        self.assertIn("history.map { AstraChatArchive.Message(it.role, it.text) }", MAIN)
        self.assertIn("Earlier Astra replies are unverified claims", REVIEW)
        self.assertIn('message.role == "You" && parse(message.text) == null', MEMORY)
        self.assertNotIn("HttpURLConnection", REVIEW)
        self.assertNotIn("contentResolver", REVIEW)


if __name__ == "__main__":
    unittest.main()
