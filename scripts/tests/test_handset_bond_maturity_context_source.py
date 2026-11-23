"""Owner's observed Test 2: correct bond-history provenance and duration direction.

Static tests complement Kotlin/JVM checks; no model intelligence score claimed.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
BOND = (JAVA / "AstraBondMaturityContext.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")

class BondHistoryHandsetContracts(unittest.TestCase):
    def test_only_own_chat_user_messages_supply_orig_and_current(self):
        self.assertIn('filter { it.role == "You" }', BOND)
        self.assertIn("owner.drop(root.first + 1)", BOND)
        self.assertIn("roots.lastOrNull()", BOND)
        self.assertNotIn("archivedChats", BOND)
        self.assertIn("AstraBondMaturityContext.respond(", MAIN)

    def test_rate_risk_direction_derived_from_corrected_premise(self):
        self.assertIn("shortening maturity generally reduces modified duration", BOND)
        self.assertIn("extending maturity generally increases modified duration", BOND)
        self.assertIn("Maturity is not modified duration", BOND)
        self.assertIn("AstraInterruptedReply.isIncomplete", BOND)

    def test_facts_precede_actions_and_no_model_conjecture(self):
        self.assertLess(
            MAIN.index("AstraBondMaturityContext.respond("),
            MAIN.index("val workflowSnapshot =")
        )
        self.assertNotIn("HttpURLConnection", BOND)
        self.assertNotIn("AstraBookLibrary", BOND)
        self.assertNotIn("commitWorkflow", BOND)

if __name__ == "__main__":
    unittest.main()
