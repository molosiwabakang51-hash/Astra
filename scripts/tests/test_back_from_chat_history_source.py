"""Protect the Android Back escape regression when Chat is re-entered."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
POLICY = (APP / "AstraTabBackPolicy.kt").read_text(encoding="utf-8")
STUDY = (APP / "AstraStudyHub.kt").read_text(encoding="utf-8")


class BackFromChatHistorySourceTest(unittest.TestCase):
    def test_back_handler_includes_history_even_while_chat_is_active(self):
        self.assertIn(
            "val previousScreen = AstraTabBackPolicy.previous(tab, tabBackStack)",
            MAIN
        )
        self.assertIn("previousScreen != null", MAIN)
        self.assertNotIn(
            "commandDeckExpanded || showFindInChat || showMemorySearch ||\n"
            "                        tab != 0", MAIN
        )
        self.assertIn("tab = previousScreen.tab", MAIN)

    def test_back_consumes_previous_entry_without_recording_new_forward_navigation(self):
        self.assertIn("while (tabBackStack.size > previousScreen.remainingHistory)", MAIN)
        self.assertIn("restoringTabFromBack = true", MAIN)
        self.assertIn("if (restoringTabFromBack)", MAIN)
        self.assertIn("if (tabBackStack.size > 20)", MAIN)
        self.assertIn("candidate != currentTab", POLICY)

    def test_study_subject_back_returns_to_catalog_before_parent_activity(self):
        self.assertIn("import androidx.activity.compose.BackHandler", STUDY)
        self.assertIn("BackHandler(enabled = selected != null)", STUDY)
        self.assertIn("onSubjectChange(null)", STUDY)
        self.assertIn("if (selected == null) onBackToChat() else onSubjectChange(null)", STUDY)
        self.assertIn("AstraStudyHub(", MAIN)

    def test_dialogs_and_search_take_priority_over_back_history(self):
        start = MAIN.index("BackHandler(\n", MAIN.index("val previousScreen ="))
        end = MAIN.index("\n                }", start)
        handler = MAIN[start:end]
        self.assertLess(
            handler.index("navigationPopupOpen -> navigationPopupOpen = false"),
            handler.index("previousScreen != null ->")
        )
        self.assertLess(
            handler.index("showChatSwitcher -> showChatSwitcher = false"),
            handler.index("previousScreen != null ->")
        )
        self.assertLess(
            handler.index("showFindInChat -> showFindInChat = false"),
            handler.index("previousScreen != null ->")
        )


if __name__ == "__main__":
    unittest.main()
