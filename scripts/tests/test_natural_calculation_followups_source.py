"""Natural calculation followups must use only the latest completed same-chat result.

Python source contracts complement Kotlin/JVM logic tests; device conversation
quality remains unverified until Qwen3 generates explanations on the phone.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
ANCHOR = (JAVA / "AstraFollowUpAnchor.kt").read_text(encoding="utf-8")
BUDGET = (JAVA / "AstraGenerationPolicy.kt").read_text(encoding="utf-8")

class NaturalToolFollowUpSourceTests(unittest.TestCase):
    def test_simple_calculation_explanation_phrases_are_contextual(self):
        for phrase in (
            "how did you calculate that", "show the working",
            "explain that result", "what assumptions did you use",
            "where did that number come from",
        ):
            self.assertIn('"' + phrase + '"', ANCHOR)
        self.assertIn('CURRENT USER FOLLOW-UP:', ANCHOR)

    def test_only_last_completed_chat_pair_can_supply_context(self):
        self.assertIn("currentChat.takeLast(2)", ANCHOR)
        self.assertIn('it.role == "You"', ANCHOR)
        self.assertIn('it.role == "Astra"', ANCHOR)
        self.assertIn("!AstraInterruptedReply.isIncomplete(it.text)", ANCHOR)

    def test_complex_calculation_explanations_have_enough_offline_budget(self):
        self.assertIn("show the calculation", BUDGET)
        self.assertIn("show the working", BUDGET)
        self.assertIn("how did you calculate", BUDGET)

if __name__ == "__main__":
    unittest.main()
