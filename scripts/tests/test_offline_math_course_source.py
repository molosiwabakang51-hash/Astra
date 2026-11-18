"""Astra mathematics course source regression checks; no external AI required."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
MATH = (JAVA / "AstraMathCourse.kt").read_text(encoding="utf-8")
CAT = (JAVA / "AstraStudyCatalog.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")

class AstraMathCourseSourceTest(unittest.TestCase):
    def test_builtin_course_is_model_free(self):
        self.assertEqual(16, MATH.count("            Lesson(\n"))
        self.assertIn('"MATHEMATICS FOUNDATION', MATH)
        self.assertIn('"WORKED EXAMPLE"', MATH)
        self.assertIn('"math quiz"', MATH)
        self.assertIn('"answer math"', MATH)
        self.assertIn('private const val VALID_MASK = 0x0000FFFF', MATH)
        for forbidden in ("http://", "https://", "import okhttp", "gguf", "AudioRecord"):
            self.assertNotIn(forbidden, MATH)

    def test_all_subject_menu_topics_reach_the_real_math_engine(self):
        self.assertIn('AstraMathCourse.lessons.mapIndexed', CAT)
        self.assertIn('"math lesson ${index + 1}"', CAT)
        self.assertIn('"math progress"', CAT)
        self.assertIn('"math quiz"', CAT)
        self.assertIn("private val mathCourse: AstraMathCourse", MAIN)
        self.assertIn("AstraMathCourse(storage::getInt, storage::setInt)", MAIN)
        self.assertIn("mathCourse.respond(clean)?.let", MAIN)

    def test_study_is_not_auto_generated_or_automatically_started(self):
        self.assertIn('"Maths course"', CAT)
        self.assertNotIn('"Teach me algebra with a simple worked example"', CAT)
        self.assertNotIn("AstraOnDeviceBrain", MATH)
        self.assertNotIn("readString", MATH)

if __name__ == "__main__":
    unittest.main()
