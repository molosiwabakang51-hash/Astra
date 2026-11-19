"""Contract tests: real detailed curriculum and openly labeled subject outlines."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
CATALOG = (JAVA / "AstraStudyCatalog.kt").read_text(encoding="utf-8")
ATLAS = (JAVA / "AstraSubjectAtlas.kt").read_text(encoding="utf-8")
MASTER = (JAVA / "AstraMasterclass.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class SubstantiveCurriculumTest(unittest.TestCase):
    def test_57_detailed_authored_workshops(self):
        expected = {
            "AstraMasterFinance.kt": 14,
            "AstraMasterMath.kt": 14,
            "AstraMasterEnglish.kt": 15,
            "AstraMasterTrading.kt": 14,
        }
        self.assertEqual(57, sum(expected.values()))
        for filename, count in expected.items():
            content = (JAVA / filename).read_text(encoding="utf-8")
            self.assertEqual(count, content.count("        AstraMasterclass.Unit("))
            self.assertIn("internal object", content)
            self.assertNotIn("http://", content)
            self.assertNotIn("https://", content)
        for phrase in ("DETAILED THEORY", "WORKED CASE", "INDEPENDENT PRACTICE",
                       "Mastery quizzes passed", "Segment local bitsets"):
            self.assertIn(phrase, MASTER)

    def test_atlas_is_large_but_honestly_not_a_completed_course(self):
        self.assertEqual(285, ATLAS.count("\n        Entry("))
        self.assertIn("OFFLINE ROADMAP — NOT A COMPLETE TAUGHT COURSE", ATLAS)
        self.assertIn("AstraSubjectAtlas.entries.map", CATALOG)
        self.assertIn("requiresModel = true", CATALOG)
        self.assertIn('AstraSubjectAtlas.respond(clean)?.let', MAIN)
        self.assertIn('AstraMasterclass.units(course).mapIndexed', CATALOG)
        self.assertIn('masterclass.respond(clean)?.let', MAIN)

    def test_math_english_trading_foundation_progress_is_not_shared(self):
        cases = {
            "AstraMathCourse.kt": '"astra_math_',
            "AstraEnglishCourse.kt": '"astra_english_',
            "AstraTradingCourse.kt": '"astra_trading_',
        }
        for filename, prefix in cases.items():
            body = (JAVA / filename).read_text(encoding="utf-8")
            self.assertEqual(6, body.count(prefix))
            if filename != "AstraMathCourse.kt":
                self.assertNotIn('"astra_math_', body)
        self.assertIn('private val masterclass: AstraMasterclass', MAIN)
        self.assertIn('AstraMasterclass(storage::getInt, storage::setInt)', MAIN)

    def test_course_dispatch_is_prior_to_language_model(self):
        self.assertLess(MAIN.index('masterclass.respond(clean)?.let'),
                        MAIN.index('multilingualTutor.respond(clean)?.let'))
        self.assertLess(MAIN.index('AstraSubjectAtlas.respond(clean)?.let'),
                        MAIN.index('multilingualTutor.respond(clean)?.let'))
        self.assertIn("offline syllabus roadmap", CATALOG)
        self.assertIn("optional local ai", CATALOG.lower())


if __name__ == "__main__":
    unittest.main()
