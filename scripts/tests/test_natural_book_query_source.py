"""Conservative owner-initiated offline document retrieval in natural language.

Source checks are not handset proof of citation quality or Qwen knowledge.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
BOOK = (JAVA / "AstraBookQuery.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")


class NaturalOwnerBookQuestions(unittest.TestCase):
    def test_only_explicit_ownership_patterns_trigger_local_retrieval(self):
        self.assertIn("according to|using", BOOK)
        self.assertIn("do\\s+my\\s+books", BOOK)
        self.assertIn("search|check|look in", BOOK)
        self.assertIn("raw.contains", BOOK)
        self.assertIn("return null", BOOK)
        self.assertNotIn("Http", BOOK)

    def test_natural_source_question_takes_priority_over_write_workflows(self):
        self.assertIn("if (AstraBookQuery.extract(message) != null)", MAIN)
        self.assertLess(
            MAIN.index("if (AstraBookQuery.extract(message) != null)"),
            MAIN.index("val workflowSnapshot =")
        )
        self.assertIn("enqueueOffline(message, message)", MAIN)

    def test_source_fit_and_citation_remain_enforced(self):
        self.assertIn("AstraBookQuery.extract(finalMessage)", BRAIN)
        self.assertIn("AstraReferenceFit.permitsBook(question)", BRAIN)
        self.assertIn("includedEvidence(question, bookReferences, requestPrompt)", BRAIN)
        self.assertIn("Your book excerpts could not fit safely", BRAIN)


if __name__ == "__main__":
    unittest.main()
