"""Owner intent is typed separately from recovered and retrieved context."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text()
BRAIN = (APP / "AstraOnDeviceBrain.kt").read_text()
INTENT = (APP / "AstraInferenceIntent.kt").read_text()

class InferenceIntentSourceTest(unittest.TestCase):
    def test_current_request_controls_reasoning_and_depth(self):
        self.assertIn("ownerRequest = when {", MAIN)
        self.assertIn("grounding != null -> grounding.question", MAIN)
        self.assertIn("isolatedReview -> null", MAIN)
        self.assertIn("AstraInferenceIntent.policyQuestion(", BRAIN)
        self.assertIn("AstraAdaptiveReasoning.select(\n                        policyQuestion,", BRAIN)
        self.assertIn("AstraGenerationPolicy.maxAnswerTokens(\n                        policyQuestion,", BRAIN)
        self.assertIn("hasReferences = memoryGrounded ||", BRAIN)
        self.assertIn("if (diagnosticIsolated || ownerRequest.isNullOrBlank())", INTENT)

    def test_context_does_not_implicitly_search_but_explicit_scope_remains(self):
        self.assertIn("allowAutomaticReferences = calculated == null &&", MAIN)
        self.assertIn("AstraInferenceIntent.allowsAutomaticReferences(prompt, focusedPrompt)", MAIN)
        self.assertIn("!memoryGrounded && memoryBrief == null && !allowAutomaticReferences", BRAIN)
        self.assertIn("prompt, focusedPrompt", MAIN)
        self.assertIn("original == focused && !AstraWorkspaceHandoff.isModelDraft(original)", INTENT)
        self.assertIn('allowAutomaticReferences &&\n                        context.getSharedPreferences', BRAIN)
        self.assertIn("!allowAutomaticReferences)", BRAIN)
        self.assertIn("AstraBookQuery.extract(finalMessage)", BRAIN)
        self.assertIn("explicitBookQuery != null,", BRAIN)
        self.assertIn("augmentedQuestion,", BRAIN)

if __name__ == "__main__":
    unittest.main()
