"""Regression for owner report: Android Back exits from Chat root.

These source contracts complement Kotlin/JVM navigation tests.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
POLICY = (JAVA / "AstraTabBackPolicy.kt").read_text(encoding="utf-8")

class ChatRootNavigationTests(unittest.TestCase):
    def test_root_always_handles_back_instead_of_finishing_activity(self):
        self.assertIn("rootOpensNavigation(tab, tabBackStack)", MAIN)
        self.assertIn("previousScreen != null || chatRootBackOpensNavigation", MAIN)
        self.assertIn("chatRootBackOpensNavigation -> {", MAIN)
        self.assertIn("navigationPopupOpen = true", MAIN)
        self.assertIn("navigationPopupOpen -> navigationPopupOpen = false", MAIN)
        self.assertIn("currentTab == CHAT && previous(currentTab, history) == null", POLICY)

    def test_history_still_uses_actual_previous_screen(self):
        self.assertIn("AstraTabBackPolicy.previous(tab, tabBackStack)", MAIN)
        self.assertIn("tab = target.tab", MAIN)
        self.assertIn("returnToPreviousDestination(previousScreen)", MAIN)
        self.assertIn("Return from Chat to ", MAIN)
        self.assertIn("restoringTabFromBack = true", MAIN)
        self.assertNotIn("finishAffinity()", POLICY)

if __name__ == "__main__":
    unittest.main()
