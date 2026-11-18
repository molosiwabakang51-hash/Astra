"""Astra offline English curriculum source and integration regression guards."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
ENGLISH = (JAVA / "AstraEnglishCourse.kt").read_text(encoding="utf-8")
CATALOG = (JAVA / "AstraStudyCatalog.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class EnglishCourseSourceTest(unittest.TestCase):
    def test_sixteen_original_english_lessons_are_bundled(self):
        self.assertEqual(16, ENGLISH.count("            Lesson(\n"))
        self.assertIn("ENGLISH FOUNDATION ·", ENGLISH)
        self.assertIn("ENGLISH COURSE PROGRESS", ENGLISH)
        self.assertIn("WORKED EXAMPLE", ENGLISH)
        self.assertIn("CHECK YOUR THINKING", ENGLISH)
        self.assertIn('"answer english"', ENGLISH)
        self.assertIn('private const val VALID_MASK = 0x0000FFFF', ENGLISH)
        self.assertNotIn("MATHEMATICS FOUNDATION", ENGLISH)

    def test_every_lesson_is_wired_to_existing_study_and_command_dispatch(self):
        self.assertIn("AstraEnglishCourse.lessons.mapIndexed", CATALOG)
        self.assertIn('"english lesson ${index + 1}"', CATALOG)
        self.assertIn('"english quiz"', CATALOG)
        self.assertIn('"english progress"', CATALOG)
        self.assertNotIn("Teach me English grammar, starting with parts of speech", CATALOG)
        self.assertIn("private val englishCourse: AstraEnglishCourse", MAIN)
        self.assertIn("AstraEnglishCourse(storage::getInt, storage::setInt)", MAIN)
        self.assertIn("englishCourse.respond(clean)?.let", MAIN)
        self.assertLess(MAIN.index("englishCourse.respond(clean)?.let"),
                        MAIN.index("dialogue.respond(clean, tasks, notes, completed)?.let"))

    def test_no_models_network_permissions_or_surprise_hijacking(self):
        self.assertIn("else -> null", ENGLISH)
        self.assertIn('command.startsWith("english lesson ")', ENGLISH)
        for forbidden in ("http://", "https://", "android.net", "gguf", "OkHttp",
                          "AstraOnDeviceBrain", "java.io.File", "trainModel("):
            self.assertNotIn(forbidden, ENGLISH)
        self.assertIn("private val readInt: (String, Int) -> Int", ENGLISH)
        self.assertIn("private val writeInt: (String, Int) -> Unit", ENGLISH)


if __name__ == "__main__":
    unittest.main()
