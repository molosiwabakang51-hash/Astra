"""Guard open-ended finance instruction from being replaced by canned glossary text."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
FINANCE = (APP / "AstraFinanceEducation.kt").read_text(encoding="utf-8")
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")


class DetailedFinanceRoutingSourceTests(unittest.TestCase):
    def test_detailed_requests_bypass_fixed_glossary_and_return_to_ai(self):
        self.assertIn("asksForCustomizedExplanation.containsMatchIn(query)", FINANCE)
        self.assertIn("if (asksForCustomizedExplanation.containsMatchIn(query)) return null", FINANCE)
        for term in ("worked examples?", "step[- ]by[- ]step", "compare",
                     "in russian", "in french", "using|including"):
            self.assertIn(term, FINANCE)

    def test_standard_glossary_remains_but_model_is_general_fallback(self):
        self.assertIn("AstraFinanceEducation.respond(clean)?.let {", MAIN)
        self.assertIn("AstraNaturalFinance.respond(clean)?.let {", MAIN)
        self.assertIn("AstraOnDeviceBrain.Turn(\"user\"", MAIN)
        self.assertIn("else -> null", FINANCE)
        self.assertNotIn("http://", FINANCE)
        self.assertNotIn("HttpURLConnection", FINANCE)


if __name__ == "__main__":
    unittest.main()
