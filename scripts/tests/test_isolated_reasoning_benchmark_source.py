"""Source contracts for owner-data-free Qwen3 on-device reasoning diagnostics."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
BRAIN = (APP / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
POLICY = (APP / "AstraDiagnosticContext.kt").read_text(encoding="utf-8")
SUITE = (APP / "AstraReasoningBenchmark.kt").read_text(encoding="utf-8")


class IsolatedReasoningBenchmarkSourceChecks(unittest.TestCase):
    def test_fixed_probes_never_read_private_books_or_synced_knowledge(self):
        self.assertIn("diagnosticIsolated: Boolean = false", BRAIN)
        self.assertIn(
            "val explicitBookQuery = if (diagnosticIsolated) null else", BRAIN
        )
        self.assertIn(
            "val openReferences = if (diagnosticIsolated) emptyList()", BRAIN
        )
        self.assertIn("AstraDiagnosticContext.allowPrivateBooks(", BRAIN)
        self.assertIn("!diagnosticIsolated &&", POLICY)

    def test_existing_language_and_new_reasoning_suite_both_request_isolation(self):
        self.assertIn("AstraLanguageBenchmark.render(", MAIN)
        self.assertIn("AstraReasoningBenchmark.render(", MAIN)
        self.assertGreaterEqual(MAIN.count("diagnosticIsolated = true"), 4)
        self.assertIn('Text("Run reasoning checks")', MAIN)
        self.assertIn('Text("Stop reasoning checks")', MAIN)
        self.assertIn("copyText(reasoningBenchmarkStatus)", MAIN)

    def test_fixed_inputs_stay_offline_and_reports_reject_overclaiming(self):
        self.assertIn("enum class Probe(", SUITE)
        self.assertIn("PROJECT_NPV(", SUITE)
        self.assertIn("FOREIGN_EXCHANGE_RISK(", SUITE)
        self.assertNotIn("HttpURLConnection", SUITE)
        self.assertNotIn("https://", SUITE)
        self.assertNotIn("personal memory", SUITE)
        self.assertIn("do not establish general reasoning ability", SUITE)


if __name__ == "__main__":
    unittest.main()
