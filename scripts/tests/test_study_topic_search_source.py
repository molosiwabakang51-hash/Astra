"""Study long-course discovery contract: fully local, searchable and non-destructive."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
HUB = (JAVA / "AstraStudyHub.kt").read_text(encoding="utf-8")
SEARCH = (JAVA / "AstraStudySearch.kt").read_text(encoding="utf-8")

class StudyTopicSearchSourceTest(unittest.TestCase):
    def test_catalog_uses_bounded_local_filter_only(self):
        self.assertIn("internal object AstraStudyTopicFilter", HUB)
        self.assertIn("val query = AstraStudySearch.query(rawQuery)", HUB)
        self.assertIn("raw.trim().take(80)", SEARCH)
        self.assertNotIn("https://", SEARCH)
        self.assertIn("items(visibleTopics, key = { it.title })", HUB)
        self.assertIn("AstraStudyTopicFilter.apply(selected.topics, topicQuery)", HUB)
        self.assertIn("onLesson(topic)", HUB)
        self.assertNotIn("http://", HUB)
        self.assertNotIn("https://", HUB)
        self.assertNotIn("onLesson(topicQuery)", HUB)

    def test_query_is_ephemeral_and_leaves_course_navigation_intact(self):
        self.assertIn("var topicQuery by remember(selectedSubjectId) { mutableStateOf(\"\") }", HUB)
        self.assertIn("onValueChange = { topicQuery = it.take(80) }", HUB)
        self.assertIn("No matching lessons", HUB)
        self.assertIn('onClick = { topicQuery = "" }', HUB)
        self.assertIn("onBackToChat()", HUB)
        self.assertIn("onSubjectChange(null)", HUB)
        self.assertIn("onSubjectChange(subject.id)", HUB)
        self.assertIn("visibleTopics.size", HUB)

if __name__ == "__main__":
    unittest.main()
