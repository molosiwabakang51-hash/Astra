"""Prevent cancelled native answers contaminating future user follow-ups."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
ANCHOR = (APP / "AstraFollowUpAnchor.kt").read_text(encoding="utf-8")
POLICY = (APP / "AstraInterruptedReply.kt").read_text(encoding="utf-8")


class InterruptedContextRecoveryTests(unittest.TestCase):
    def test_live_native_recovery_excludes_partial_assistant_turns(self):
        start = MAIN.index("val turns = AstraConversationHistory.forRecovery(history)")
        end = MAIN.index('AstraOnDeviceBrain.Turn("user", focusedPrompt)', start)
        section = MAIN[start:end]
        self.assertIn('entry.role == "Astra"', section)
        self.assertIn("AstraInterruptedReply.isIncomplete(entry.text)", section)
        self.assertIn('entry.role)', section)
        self.assertLess(section.index("filterNot"), section.index("mapNotNull"))

    def test_followup_is_anchored_to_last_completed_pair_only(self):
        self.assertIn("currentChat.takeLast(2)", ANCHOR)
        self.assertIn('it.role == "You" && it.text.isNotBlank()', ANCHOR)
        self.assertIn('it.role == "Astra" && it.text.isNotBlank()', ANCHOR)
        self.assertIn("!AstraInterruptedReply.isIncomplete(it.text)", ANCHOR)
        self.assertNotIn('currentChat.lastOrNull {', ANCHOR)

    def test_interruption_markers_are_private_local_metadata_only(self):
        for marker in (
            "ASTRA // THINKING",
            "The response was interrupted when Astra closed.",
            "Offline response interrupted.",
            "Offline response stopped.",
            "[Response stopped]",
            "[Response interrupted]",
        ):
            self.assertIn(marker, POLICY)
        for prohibited in ("delete(", "URL(", "HttpURLConnection", "Retrofit",
                           "AstraModelDownload", "contentResolver", "network"):
            self.assertNotIn(prohibited, POLICY)


if __name__ == "__main__":
    unittest.main()
