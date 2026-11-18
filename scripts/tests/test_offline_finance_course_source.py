"""Astra Study Hub Finance curriculum source regression checks.

These checks only validate integration and data minimization. Real-world
financial accuracy and on-phone display remain separate acceptance gates.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
COURSE = (JAVA / "AstraFinanceCourse.kt").read_text(encoding="utf-8")
CATALOG = (JAVA / "AstraStudyCatalog.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class FinanceCourseSourceTest(unittest.TestCase):
    def test_real_offline_lesson_catalog_not_fake_ai_prompts(self):
        self.assertEqual(20, COURSE.count("            Lesson("))
        self.assertIn("AstraFinanceCourse.lessons.mapIndexed", CATALOG)
        self.assertIn('"finance lesson ${index + 1}"', CATALOG)
        self.assertIn('"finance quiz"', CATALOG)
        self.assertIn('"finance progress"', CATALOG)
        self.assertIn("WORKED EXAMPLE", COURSE)
        self.assertIn("CHECK THE ASSUMPTIONS", COURSE)
        self.assertIn("Quiz answers:", COURSE)
        self.assertIn("private const val VALID_MASK = 0x000FFFFF", COURSE)

    def test_natural_general_finance_questions_still_reach_normal_ai(self):
        self.assertIn('command.startsWith("finance lesson ")', COURSE)
        self.assertIn('command.startsWith("answer finance")', COURSE)
        self.assertIn("else -> null", COURSE)
        self.assertIn("AstraFinanceEducation.respond(clean)", MAIN)
        self.assertIn("AstraNaturalFinance.respond(clean)", MAIN)
        self.assertIn("BondAnalytics.respond(clean)", MAIN)

    def test_pending_task_does_not_swallow_explicit_finance_answers(self):
        self.assertIn("private val financeCourse: AstraFinanceCourse,", MAIN)
        self.assertIn("financeCourse.respond(clean)?.let {", MAIN)
        self.assertIn("AstraFinanceCourse(storage::getInt, storage::setInt)", MAIN)
        self.assertLess(MAIN.index("financeCourse.respond(clean)?.let {"),
                        MAIN.index("dialogue.respond(clean, tasks, notes, completed)?.let {"))

    def test_course_is_private_no_network_and_no_retraining_claims(self):
        for forbidden in ("android.content", "android.net", "http://", "https://",
                          "openConnection(", "trainModel(", "WRITE_EXTERNAL_STORAGE",
                          "java.io.File"):
            self.assertNotIn(forbidden, COURSE)
        self.assertIn("private val readInt: (String, Int) -> Int", COURSE)
        self.assertIn("private val writeInt: (String, Int) -> Unit", COURSE)


if __name__ == "__main__":
    unittest.main()
