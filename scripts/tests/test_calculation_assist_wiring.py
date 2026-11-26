"""Static routing checks; not proof of native tool selection quality."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text()
ASSIST = (APP / "AstraCalculationAssist.kt").read_text()

class CalculationAssistWiringTest(unittest.TestCase):
    def test_owner_only_and_isolated_calls(self):
        submit = MAIN[MAIN.index("fun submit("):]
        self.assertLess(submit.index("AstraModelOnly.question(message)"),
                        submit.index("AstraCalculationAssist.question(message)"))
        self.assertLess(submit.index("AstraCalculationAssist.question(message)"),
                        submit.index("AstraCognitiveReview.resolve("))
        self.assertIn("else if (calculationQuestion != null) emptyList()", MAIN)
        self.assertIn("diagnosticMaxTokens = 128", MAIN)
        self.assertIn("diagnosticIsolated = true,\n                                        modelOnly = true", MAIN)
        self.assertIn("AstraCalculationAssist.evaluate(calculationQuestion, proposal)", MAIN)
        self.assertIn("AstraCalculationAssist.synthesisPrompt(calculationQuestion, verified)", MAIN)
        self.assertIn("calculated.footer()", MAIN)

    def test_strict_parser_and_no_external_actions(self):
        self.assertIn("plan.matchEntire(proposal.trim())", ASSIST)
        self.assertIn("value !in permitted", ASSIST)
        self.assertIn("AstraInterruptedReply.isIncomplete(proposal)", ASSIST)
        self.assertIn("value.divide(atom(depth))", ASSIST)
        for forbidden in ("ProcessBuilder", "Runtime.getRuntime", "HttpURLConnection",
                          "java.io.File", "commitWorkflow"):
            self.assertNotIn(forbidden, ASSIST)

if __name__ == "__main__":
    unittest.main()
