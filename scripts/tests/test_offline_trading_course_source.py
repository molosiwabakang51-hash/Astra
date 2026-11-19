"""Prevent Day Trading Study from reverting to model-only placeholder prompts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
COURSE = (JAVA / "AstraTradingCourse.kt").read_text(encoding="utf-8")
CATALOG = (JAVA / "AstraStudyCatalog.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")

class TradingCourseSourceTest(unittest.TestCase):
    def test_sixteen_substantive_bundled_lessons_and_risk_disclosure(self):
        self.assertEqual(16, COURSE.count("            Lesson(\n"))
        self.assertIn("DAY TRADING FOUNDATION", COURSE)
        self.assertIn("NOT INVESTMENT ADVICE", COURSE)
        self.assertIn("Hypothetical examples only", COURSE)
        self.assertIn("WORKED EXAMPLE", COURSE)
        self.assertIn("CHECK YOUR THINKING", COURSE)
        self.assertIn("quiz", COURSE)
        self.assertIn("private const val VALID_MASK = 0x0000FFFF", COURSE)
        self.assertIn("TRADING COURSE PROGRESS", COURSE) if False else self.assertIn(
            "DAY TRADING COURSE PROGRESS", COURSE
        )

    def test_study_and_main_processor_are_wired_to_deterministic_engine(self):
        self.assertIn("AstraTradingCourse.lessons.mapIndexed", CATALOG)
        self.assertIn('"trading lesson ${index + 1}"', CATALOG)
        self.assertIn('"trading quiz"', CATALOG)
        self.assertIn('"trading progress"', CATALOG)
        self.assertNotIn('"Teach me day trading basics: order types', CATALOG)
        self.assertIn("private val tradingCourse: AstraTradingCourse,", MAIN)
        self.assertIn("AstraTradingCourse(storage::getInt, storage::setInt)", MAIN)
        self.assertIn("tradingCourse.respond(clean)?.let {", MAIN)
        self.assertLess(MAIN.index("tradingCourse.respond(clean)?.let {"),
                        MAIN.index("dialogue.respond(clean, tasks, notes, completed)?.let {"))

    def test_no_network_models_or_background_import_side_effects(self):
        self.assertIn("else -> null", COURSE)
        self.assertIn('command.startsWith("trading lesson ")', COURSE)
        self.assertIn('command.startsWith("answer trading")', COURSE)
        for forbidden in ("http://", "https://", "android.net", "gguf", "OkHttp",
                          "AstraOnDeviceBrain", "java.io.File", "trainModel(", "placeOrder("):
            self.assertNotIn(forbidden, COURSE)
        self.assertIn("private val readInt: (String, Int) -> Int", COURSE)
        self.assertIn("private val writeInt: (String, Int) -> Unit", COURSE)

if __name__ == "__main__":
    unittest.main()
