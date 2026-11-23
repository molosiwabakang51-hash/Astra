"""Owner-triggered native multi-turn evaluation wiring contracts.

Python/Kotlin CI cannot supply handset Qwen3 behavior or intelligence points.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
BENCH = (JAVA / "AstraMultiTurnReasoningBenchmark.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")


class MultiTurnReasoningWiringTests(unittest.TestCase):
    def test_real_native_engine_runs_successive_isolated_synthetic_turns(self):
        self.assertIn("AstraMultiTurnReasoningBenchmark.scenarios", MAIN)
        self.assertIn("for ((turnIndex, question) in", MAIN)
        self.assertIn('turns.add(AstraOnDeviceBrain.Turn(', MAIN)
        self.assertIn('"assistant", answer', MAIN)
        self.assertIn("generation,", MAIN)
        self.assertIn("diagnosticIsolated = true", MAIN)
        self.assertIn("diagnosticMaxTokens = 128", MAIN)
        self.assertIn("languageBenchmarkJob?.cancel()", MAIN)

    def test_model_isolation_disables_private_user_books(self):
        self.assertIn('val explicitBookQuery = if (diagnosticIsolated) null', BRAIN)
        self.assertIn("AstraDiagnosticContext.allowPrivateBooks(", BRAIN)
        self.assertIn("if (diagnosticIsolated) emptyList()", BRAIN)
        self.assertNotIn("AstraBookLibrary", BENCH)
        self.assertNotIn("HttpURLConnection", BENCH)
        self.assertNotIn("AstraGroundedMemory", BENCH)

    def test_report_only_pass_fail_not_prompts_or_private_outputs(self):
        self.assertIn("All synthetic multi-turn cases must complete.", BENCH)
        self.assertIn("earn no 100-point", BENCH)
        self.assertIn("No private source data", BENCH)
        self.assertIn("Test multi-turn Qwen reasoning", MAIN)
        self.assertIn("Multi-turn test cancelled.", MAIN)


if __name__ == "__main__":
    unittest.main()
