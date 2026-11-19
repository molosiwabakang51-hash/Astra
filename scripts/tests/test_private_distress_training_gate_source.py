"""Privacy regression for opt-in neural training and direct distress disclosures."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CODE = ROOT / "android/app/src/main/java/com/astra/agent"
SIGNALS = (CODE / "AstraLearningSignals.kt").read_text(encoding="utf-8")
SUPPORT = (CODE / "AstraDistressSupport.kt").read_text(encoding="utf-8")
MAIN = (CODE / "MainActivity.kt").read_text(encoding="utf-8")

class SensitiveTrainingGateTest(unittest.TestCase):
    def test_neural_adapter_filters_health_and_crisis_language(self):
        self.assertIn("private val personalWellbeing = Regex(", SIGNALS)
        self.assertIn("self", SIGNALS)
        self.assertIn("panic attack", SIGNALS)
        self.assertIn("mental", SIGNALS)
        self.assertIn("medication", SIGNALS)
        self.assertIn("you are all i have", SIGNALS)
        self.assertIn("AstraDistressSupport.respond(value) == null", SIGNALS)
        self.assertIn("fun eligible(message: String): Boolean", SIGNALS)

    def test_responsible_learning_remains_opt_in(self):
        self.assertIn("AstraLearningSignals.eligible(normalized)", MAIN)
        self.assertIn("if (neuralTrainingEnabled &&", MAIN)
        self.assertIn("astra_neural_learning_opt_in", MAIN)
        self.assertIn("fun respond(raw: String): String?", SUPPORT)
        for forbidden in ("http://", "https://", "OkHttp", "network", "upload"):
            self.assertNotIn(forbidden, SIGNALS)

if __name__ == "__main__":
    unittest.main()
