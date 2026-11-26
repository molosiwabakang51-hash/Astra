"""Static routing checks; not proof of native tool selection quality."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text()
ASSIST = (APP / "AstraCalculationAssist.kt").read_text()
BRAIN = (APP / "AstraOnDeviceBrain.kt").read_text()

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
        self.assertIn("AstraCalculationAssist.synthesisWarning(", MAIN)
        self.assertIn("OWNER OUTPUT FORMAT:", ASSIST)
        self.assertIn("value.compareTo(computed.result.toBigDecimal()) != 0", ASSIST)

    def test_thinking_synthesis_is_explicit_isolated_and_bounded(self):
        self.assertIn("calculatorSynthesis = calculated", MAIN)
        self.assertIn("modelOnly = modelOnly || calculated != null", MAIN)
        self.assertIn("allowAutomaticReferences = calculated == null &&", MAIN)
        self.assertIn("ownerRequest != null && modelOnly && earlierTurns.isEmpty()", BRAIN)
        self.assertIn("!memoryGrounded && memoryBrief == null && !allowAutomaticReferences", BRAIN)
        self.assertIn("finalMessage == AstraCalculationAssist.synthesisPrompt(ownerRequest, calculatorSynthesis)", BRAIN)
        self.assertIn("calculatorThinking || AstraAdaptiveReasoning.select(", BRAIN)
        self.assertIn("if (calculatorThinking) AstraCalculationAssist.SYNTHESIS_THINKING_CAP else diagnosticMaxTokens", BRAIN)
        self.assertIn("const val SYNTHESIS_THINKING_CAP = 768", ASSIST)
        self.assertIn("AstraPairedReasoning.allowsModeOverride(", BRAIN)

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
