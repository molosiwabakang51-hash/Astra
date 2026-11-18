"""Study library discovery stays local, bounded and accessible."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / "android/app/src/main/java/com/astra/agent/AstraStudyHub.kt").read_text(
    encoding="utf-8"
)

class StudySubjectDiscoverySourceTest(unittest.TestCase):
    def test_subject_search_is_pure_local_and_does_not_start_lessons(self):
        self.assertIn("internal object AstraStudySubjectFilter", SOURCE)
        self.assertIn("subject.topics.any { topic ->", SOURCE)
        self.assertIn("rawQuery.trim().take(80)", SOURCE)
        self.assertIn("AstraStudySubjectFilter.apply(", SOURCE)
        self.assertIn("items(visibleSubjects.chunked(columns), key = { pair -> pair.first().id })", SOURCE)
        self.assertIn("onValueChange = { subjectQuery = it.take(80) }", SOURCE)
        self.assertNotIn("onLesson(subject)", SOURCE)
        self.assertNotIn("onLesson(subjectQuery)", SOURCE)
        self.assertNotIn("https://", SOURCE)

    def test_empty_state_and_model_gate_are_visible(self):
        self.assertIn("No subjects found", SOURCE)
        self.assertIn('onClick = { subjectQuery = "" }', SOURCE)
        self.assertIn("internal object AstraStudyAccess", SOURCE)
        self.assertIn('-> "MODEL NEEDED"', SOURCE)
        self.assertIn('-> "OFFLINE"', SOURCE)
        self.assertIn("AstraStudyAccess.label(subject)", SOURCE)
        self.assertIn("selectedId = subject.id", SOURCE)
        self.assertIn("onLesson(topic)", SOURCE)

if __name__ == "__main__":
    unittest.main()
