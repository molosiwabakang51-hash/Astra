"""Production wiring contract; not evidence of neural intelligence."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
BRAIN = (APP / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")


class ModelOnlyConversationSourceTest(unittest.TestCase):
    def test_explicit_route_precedes_actions_and_grounded_retrieval(self):
        start = MAIN.index("fun submit(")
        section = MAIN[start:]
        for later in ("AstraWorkspaceHandoff.isModelDraft(message)",
                      "AstraBookQuery.extract(message)",
                      "AstraGroundedMemory.parse(message)",
                      "AstraOfflinePolicy.shouldUseModel("):
            self.assertLess(section.index("AstraModelOnly.question(message)"),
                            section.index(later))
        self.assertIn("enqueueOffline(message, question, modelOnly = true)", section)
        self.assertIn("workflowSession.reset()", section)
        self.assertIn("grounding == null && !modelOnly", MAIN)

    def test_pure_dialogue_restricts_history_and_native_sources(self):
        self.assertIn("AstraModelOnly.previousTurnCount(", MAIN)
        self.assertIn("modelOnly = modelOnly", MAIN)
        self.assertIn("previousModelOnly != modelOnly", BRAIN)
        self.assertIn("if (modelOnly) null else", BRAIN)
        self.assertIn("memoryGrounded = memoryGrounded || modelOnly", BRAIN)
        self.assertIn("if (modelOnly || !AstraDiagnosticContext.allowOpenCourse", BRAIN)
        self.assertIn("current.sendUserPrompt(", BRAIN)


if __name__ == "__main__":
    unittest.main()
