"""Static integration guard: an A/B trial must compare only fixed private-free questions.

The actual correctness/latency comparison is measured on the owner's phone;
source/CI checks never award handset intelligence points.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
UI = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
PAIR = (JAVA / "AstraPairedReasoning.kt").read_text(encoding="utf-8")


class PairedReasoningWiringTests(unittest.TestCase):
    def test_only_fixed_synthetic_diagnostics_can_override_reasoning_mode(self):
        self.assertIn("diagnosticReasoningOverride: Boolean? = null", BRAIN)
        self.assertIn("AstraPairedReasoning.allowsModeOverride(", BRAIN)
        self.assertIn("diagnosticIsolated && probes.any { it.prompt == currentQuestion }", PAIR)
        self.assertIn("AstraAdaptiveReasoning.select(", BRAIN)

    def test_android_benchmark_uses_same_question_and_alternating_mode(self):
        self.assertIn("AstraPairedReasoning.order(index)", UI)
        self.assertIn("AstraPairedReasoning.probes", UI)
        self.assertIn("diagnosticIsolated = true", UI)
        self.assertIn("diagnosticReasoningOverride =", UI)
        self.assertIn("diagnosticMaxTokens = 512", UI)
        self.assertIn("Compare fast and thinking modes", UI)
        self.assertIn("languageBenchmarkJob?.cancel()", UI)

    def test_no_private_outputs_or_unearned_rating_in_report(self):
        self.assertIn("require(comparisons.size == probes.size", PAIR)
        self.assertNotIn("trial.fast.sample", PAIR)
        self.assertNotIn("trial.think.sample", PAIR)
        self.assertNotIn("100-point AI score", "")  # no synthetic points awarded
        self.assertIn("a 100-point AI score", PAIR)
        self.assertNotIn("HttpURLConnection", PAIR)


if __name__ == "__main__":
    unittest.main()
