"""ACIE initial audit must be local, explicit, bounded and isolated."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (SRC / "MainActivity.kt").read_text()
REVIEW = (SRC / "AstraCognitiveReview.kt").read_text()

class CognitiveReviewWiringTests(unittest.TestCase):
    def test_review_before_actions_and_isolated_from_history(self):
        self.assertLess(MAIN.index("AstraCognitiveReview.resolve("),
                        MAIN.index("AstraBookFollowUp.resolve("))
        self.assertIn("if (isolatedReview) emptyList()", MAIN)
        self.assertIn("isolatedReview = true", MAIN)
        self.assertIn("enqueueOffline(message, review.prompt, modelOnly = true,", MAIN)
        self.assertIn("require(!isolatedReview || (modelOnly && grounding == null))", MAIN)

    def test_only_exact_independent_check_and_honest_model_review(self):
        self.assertIn("AstraVerifiedArithmetic.answer(original)", REVIEW)
        self.assertIn("actualNumber.compareTo(expectedNumber)", REVIEW)
        self.assertIn("not independently verified", REVIEW)
        self.assertIn("AstraInterruptedReply.isIncomplete(answer)", REVIEW)
        self.assertIn("history.takeLast(2)", REVIEW)
        self.assertNotIn("HttpURLConnection", REVIEW)
        self.assertNotIn("https://", REVIEW)

if __name__ == "__main__":
    unittest.main()
