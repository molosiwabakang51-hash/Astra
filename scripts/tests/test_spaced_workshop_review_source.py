"""Source checks for offline per-chapter spaced retrieval practice."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CODE = ROOT / "android/app/src/main/java/com/astra/agent"
SCHEDULE = (CODE / "AstraWorkshopReview.kt").read_text(encoding="utf-8")
ENGINE = (CODE / "AstraMasterclass.kt").read_text(encoding="utf-8")
STUDY = (CODE / "AstraStudyCatalog.kt").read_text(encoding="utf-8")


class SpacedStudyReviewSourceTest(unittest.TestCase):
    def test_review_is_local_bound_and_migrates_prior_mastery(self):
        self.assertIn("internal object AstraWorkshopReview", SCHEDULE)
        self.assertIn("intArrayOf(1, 3, 7, 14, 30, 60, 120)", SCHEDULE)
        self.assertIn("previous.dueUtcDay", SCHEDULE.replace("before.dueUtcDay", "previous.dueUtcDay"))
        self.assertIn("if (!correct) return State(0, 0, todayUtcDay)", SCHEDULE)
        self.assertIn('read(subject, "mastered_" + segment)', ENGINE)
        self.assertIn('read(subject, "review_due_" + number, -1)', ENGINE)
        self.assertIn("val schedule = AstraWorkshopReview.afterAnswer(", ENGINE)
        self.assertIn("fun respond(raw: String): String?", ENGINE)
        for command in ("review due", "review next", "review plan"):
            self.assertIn('cmd == "' + command + '"', ENGINE)
        for forbidden in ("http://", "https://", "java.net", "OkHttp", "TextToSpeech"):
            self.assertNotIn(forbidden, SCHEDULE)

    def test_review_is_reachable_from_study_without_llm(self):
        for action in (
            'course + " workshop review"',
            'course + " workshop review next"',
            'course + " workshop review plan"',
        ):
            self.assertIn(action, STUDY)
        self.assertIn('"Due reviews"', STUDY)
        self.assertIn('"Next due review"', STUDY)
        self.assertIn('"Review plan"', STUDY)
        self.assertIn('mark(subject, "mastered", pending)', ENGINE)

if __name__ == "__main__":
    unittest.main()
