"""Real handset regression: one-year P110 present value and no invented author correction.

Source contracts supplement JVM tests. They do not measure model intelligence.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
FINANCE = (JAVA / "AstraNaturalFinance.kt").read_text(encoding="utf-8")
EDU = (JAVA / "AstraFinanceEducation.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")

class HandsetFinanceRegression(unittest.TestCase):
    def test_word_horizons_feed_exact_offline_pv(self):
        self.assertIn("spelledPeriodPattern", FINANCE)
        self.assertIn("spelledPeriodYears", FINANCE)
        self.assertIn("periodMatches.size + spelledPeriods.size != 1", FINANCE)
        self.assertIn("AstraNaturalFinance.respond(clean)", MAIN)
        self.assertLess(
            MAIN.index("AstraNaturalFinance.respond(clean)"),
            MAIN.index("AstraFinanceEducation.respond(clean)")
        )

    def test_explicit_book_author_question_never_invents_correction(self):
        self.assertIn("who (?:wrote|authored)", EDU)
        self.assertIn("Robert J. Shiller wrote Irrational Exuberance (2000)", EDU)
        self.assertIn("AstraFinanceEducation.respond(clean)", MAIN)

if __name__ == "__main__":
    unittest.main()
