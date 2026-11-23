"""Owner consent contracts for same-chat local-book follow-ups.

Static checks support Kotlin/JVM tests but cannot verify Qwen output on phone.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
FOLLOWUP = (JAVA / "AstraBookFollowUp.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")

class OwnerBookFollowupContracts(unittest.TestCase):
    def test_explicit_only_same_chat_completed_evidence(self):
        self.assertIn('"continue using my book"', FOLLOWUP)
        self.assertIn('"what do my books say about that"', FOLLOWUP)
        self.assertIn("currentChat.takeLast(2)", FOLLOWUP)
        self.assertIn("AstraInterruptedReply.isIncomplete", FOLLOWUP)
        self.assertIn("AstraBookQuery.extract(recent[0].text)", FOLLOWUP)
        self.assertNotIn("archivedChats", FOLLOWUP)

    def test_always_reretrieves_source_and_never_uses_prior_model_reply(self):
        self.assertIn('Result.Ready("ask my books $groundedQuestion")', FOLLOWUP)
        self.assertIn('Use newly retrieved book excerpts as evidence', FOLLOWUP)
        self.assertIn("AstraBookFollowUp.resolve(", MAIN)
        self.assertIn("enqueueOffline(message, bookFollowUp.modelPrompt)", MAIN)
        self.assertIn("AstraBookQuery.extract(finalMessage)", BRAIN)
        self.assertIn("includedEvidence(question, bookReferences, requestPrompt)", BRAIN)

    def test_owner_disambiguation_cannot_trigger_workflow(self):
        self.assertLess(
            MAIN.index("AstraBookFollowUp.resolve("),
            MAIN.index("val workflowSnapshot =")
        )
        self.assertIn("I will not guess or silently open private files.", MAIN)
        self.assertNotIn("https://", FOLLOWUP)
        self.assertNotIn("HttpURLConnection", FOLLOWUP)

if __name__ == "__main__":
    unittest.main()
