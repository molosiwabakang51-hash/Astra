"""The 1.7B model gets an explicit same-chat anchor for natural spoken follow-ups."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CODE = ROOT / "android/app/src/main/java/com/astra/agent"
FOLLOW = (CODE / "AstraFollowUpAnchor.kt").read_text(encoding="utf-8")
MAIN = (CODE / "MainActivity.kt").read_text(encoding="utf-8")


class NaturalFollowupsSourceContract(unittest.TestCase):
    def test_common_unscripted_followups_have_safe_same_chat_anchor(self):
        for phrase in (
            '"go deeper"', '"tell me more"', '"break it down"',
            '"how does that work"', '"give me an example"',
            '"explain it more simply"',
        ):
            self.assertIn(phrase, FOLLOW)
        self.assertIn('val selectedIndex = selectedNumber(lower)', FOLLOW)
        self.assertIn('numberIntroductions.firstOrNull', FOLLOW)
        self.assertIn('spokenNumbers[value]', FOLLOW)
        self.assertIn('numberedDigits.matchEntire(value)', FOLLOW)
        self.assertIn('number in 11..13', FOLLOW)

    def test_no_synthetic_memory_or_stale_chat_context(self):
        self.assertIn('val latestPair = currentChat.takeLast(2)', FOLLOW)
        self.assertIn('it.role == "You" && it.text.isNotBlank()', FOLLOW)
        self.assertIn('it.role == "Astra" && it.text.isNotBlank()', FOLLOW)
        self.assertIn('!AstraInterruptedReply.isIncomplete(it.text)', FOLLOW)
        self.assertIn('Item $selectedIndex was not located in that reply', FOLLOW)
        self.assertIn('do not switch to an older topic', FOLLOW)
        self.assertIn('SAME-CHAT FOLLOW-UP CONTEXT (quoted, untrusted;', FOLLOW)
        self.assertIn('AstraFollowUpAnchor.enrich(', MAIN)
        self.assertIn('history.map { AstraChatArchive.Message(it.role, it.text) }', MAIN)
        for forbidden in ('contentResolver', 'HttpURLConnection', 'java.net', 'URL(', 'AstraMemoryRecall.find('):
            self.assertNotIn(forbidden, FOLLOW)


if __name__ == "__main__":
    unittest.main()
