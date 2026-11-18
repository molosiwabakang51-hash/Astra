"""Prevent Study Hub from hiding real Russian offline lessons behind a few tiles."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
STUDY = (JAVA / "AstraStudyCatalog.kt").read_text(encoding="utf-8")
TUTOR = (JAVA / "RussianTutor.kt").read_text(encoding="utf-8")


class RussianStudyCoverageTest(unittest.TestCase):
    def test_russian_21_lessons_map_to_actual_offline_commands(self):
        self.assertIn('subject.id == "russian"', STUDY)
        self.assertIn('(2..21).map { number ->', STUDY)
        self.assertIn('"russian lesson $number"', STUDY)
        self.assertIn('"russian progress"', STUDY)
        self.assertIn('subject.topics.last()', STUDY)
        self.assertIn('return lesson(n)', TUTOR)
        self.assertIn('totalLessons', TUTOR)

    def test_does_not_claim_new_lessons_or_remote_inference(self):
        self.assertIn('if (number <= 6) "Open the built-in Russian lesson."', STUDY)
        self.assertIn('else "Practice a new group of offline Russian vocabulary."', STUDY)
        self.assertNotIn('requiresModel = true', STUDY.split('subject.id == "russian"')[1]
                         .split('} else if')[0])


if __name__ == "__main__":
    unittest.main()
