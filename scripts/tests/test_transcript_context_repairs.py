"""Regression guards for reported mixed-language, citation and context failures."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
CHAT = (JAVA / "AstraChatReview.kt").read_text(encoding="utf-8")
MULTI = (JAVA / "AstraMultilingualTutor.kt").read_text(encoding="utf-8")
FACTS = (JAVA / "AstraVerifiedEconomics.kt").read_text(encoding="utf-8")
SUPPORT = (JAVA / "AstraDistressSupport.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")

class TranscriptFailureSourceTest(unittest.TestCase):
    def test_offline_course_handles_natural_french_next_lesson(self):
        self.assertIn("val naturalNext =", MULTI)
        self.assertIn("if (nextLesson)", MULTI)
        self.assertIn("return lesson(language, next)", MULTI)
        self.assertIn("if (lower == \"oui\" && current() == Language.FRENCH)", MULTI)
        self.assertNotIn("AstraOnDeviceBrain", MULTI)

    def test_earlier_assistant_claims_are_reviewable_without_new_user_memories(self):
        self.assertIn("AstraChatReview.respond(", MAIN)
        self.assertIn("Earlier Astra reply", CHAT)
        self.assertIn("quotes, not automatically verified", CHAT)
        self.assertIn("Niall Shiller", CHAT)
        self.assertIn("Robert J. Shiller", CHAT)
        self.assertLess(MAIN.index("AstraChatReview.respond("),
                        MAIN.index("AstraMemoryRecall.parse(message)"))
        self.assertNotIn("archivedChats", CHAT)
        self.assertNotIn("AstraOnDeviceBrain", CHAT)

    def test_fact_correction_is_sourced_and_distress_is_not_model_only(self):
        self.assertIn("som.yale.edu/faculty-research", FACTS)
        self.assertIn("Irrational Exuberance (2000)", FACTS)
        self.assertIn("Yale economist", FACTS)
        self.assertIn("AstraDistressSupport.respond(clean)?.let", MAIN)
        self.assertIn("AstraVerifiedEconomics.respond(clean)?.let", MAIN)
        self.assertIn("cannot verify", SUPPORT)
        for source in (CHAT, FACTS, SUPPORT):
            self.assertNotIn("okhttp", source.lower())
            self.assertNotIn("trainModel(", source)
            self.assertNotIn("AstraModelDownload", source)

if __name__ == "__main__":
    unittest.main()
