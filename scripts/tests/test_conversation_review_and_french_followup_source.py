"""Astra regression guards for chat self-audit and active multilingual lessons."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
REVIEW = (JAVA / "AstraConversationReview.kt").read_text(encoding="utf-8")
LANG = (JAVA / "AstraMultilingualTutor.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")

class ConversationRepairTest(unittest.TestCase):
    def test_review_is_explicit_and_shiller_correction_is_grounded(self):
        self.assertIn("internal object AstraConversationReview", REVIEW)
        self.assertIn("Robert J. Shiller", REVIEW)
        self.assertIn("Yale", REVIEW)
        self.assertIn("Niall Shiller", REVIEW)
        self.assertIn("unverified claims", REVIEW)
        self.assertIn("earlierMentions", REVIEW)
        self.assertIn("reviewMistake", REVIEW)
        self.assertNotIn("http://", REVIEW)
        self.assertNotIn("https://", REVIEW)

    def test_review_precedes_memory_without_polluting_private_memory(self):
        self.assertIn("AstraConversationReview.respond(", MAIN)
        self.assertIn("AstraMemoryRecall.parse(message)", MAIN)
        self.assertLess(MAIN.index("AstraConversationReview.respond("),
                        MAIN.index("AstraMemoryRecall.parse(message)"))
        self.assertIn('history.map { AstraChatArchive.Message(it.role, it.text) }', MAIN)

    def test_french_progression_is_bounded_and_not_generic_ai(self):
        self.assertIn('val nextLesson = Regex(', LANG)
        self.assertIn('nextLesson.matches(lower)', LANG)
        self.assertIn('current() == Language.FRENCH && lower == "oui"', LANG)
        self.assertIn('next = (readInt(key(language, "lesson"), 1) + 1)', LANG)

if __name__ == "__main__":
    unittest.main()
