"""Owner handset regression: Russian bond questions must not use greeting lessons.

These are code-contract tests. Human language fluency is still unverified.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
OPEN = (JAVA / "AstraOpenLibrary.kt").read_text(encoding="utf-8")
SYSTEM = (JAVA / "AstraConversationContext.kt").read_text(encoding="utf-8")


class FinanceVsRussianLessonRouting(unittest.TestCase):
    def test_language_learning_optin_not_cyrillic_presence(self):
        self.assertIn("isLanguageStudyQuestion(question)", OPEN)
        self.assertIn("unrelatedFinanceTopic.containsMatchIn(question)", OPEN)
        self.assertIn("topics.none { it in keywordSet || it in words }", OPEN)
        self.assertIn("A neural topic hint alone never authorizes", OPEN)
        self.assertNotIn("if (!russian.containsMatchIn(question)", OPEN)

    def test_basic_finance_not_refused_due_to_language_or_rag(self):
        self.assertIn("Basic financial", SYSTEM)
        self.assertIn("education is allowed in every language", SYSTEM)
        self.assertIn("Inflation is a sustained rise in the general price level", SYSTEM)
        self.assertIn("shorter duration normally means", SYSTEM)
        self.assertNotIn("HttpURLConnection", OPEN)


if __name__ == "__main__":
    unittest.main()
