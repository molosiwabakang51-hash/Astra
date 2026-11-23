"""Protect opt-in reasoning and natural arithmetic routing.

Source-level CI checks do not establish actual Qwen performance or handset speed.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
BRAIN = (SRC / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
ADAPTIVE = (SRC / "AstraAdaptiveReasoning.kt").read_text(encoding="utf-8")
CALCULATOR = (SRC / "AstraVerifiedArithmetic.kt").read_text(encoding="utf-8")

class AdaptiveReasoningSourceTests(unittest.TestCase):
    def test_owner_opt_in_and_diagnostic_isolation(self):
        self.assertIn('storage.getBoolean("astra_adaptive_reasoning", false)', MAIN)
        self.assertIn('"astra_adaptive_reasoning", checked', MAIN)
        self.assertIn('getBoolean("astra_adaptive_reasoning", false)', BRAIN)
        self.assertIn("AstraAdaptiveReasoning.select(", BRAIN)
        self.assertIn("diagnosticIsolated = diagnosticIsolated", BRAIN)
        self.assertIn("if (diagnosticIsolated) return false", ADAPTIVE)
        self.assertIn("if (!automaticEnabled) return false", ADAPTIVE)
        self.assertNotIn("http://", ADAPTIVE)
        self.assertNotIn("https://", ADAPTIVE)

    def test_verified_calculations_precede_model_fallback(self):
        self.assertIn("AstraVerifiedArithmetic.answer(clean)", MAIN)
        self.assertLess(
            MAIN.index("AstraVerifiedArithmetic.answer(clean)"),
            MAIN.index("AstraNaturalFinance.respond(clean)")
        )
        self.assertIn("matchEntire(question)", CALCULATOR)
        self.assertIn("stripTrailingZeros().toPlainString()", CALCULATOR)
        self.assertNotIn("Http", CALCULATOR)

    def test_honest_performance_language(self):
        self.assertIn("Default off until handset tests", MAIN)
        self.assertIn("not a correctness guarantee", ADAPTIVE)
        self.assertIn("not", CALCULATOR)

if __name__ == "__main__":
    unittest.main()
