"""Mic callbacks must be invalidated before app becomes hidden or keyguarded."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
SESSION = (APP / "AstraSpeechSession.kt").read_text(encoding="utf-8")


def between(start: str, end: str) -> str:
    assert MAIN.count(start) == 1, start
    return MAIN.split(start, 1)[1].split(end, 1)[0]


class EarlyMicrophoneCancellationTests(unittest.TestCase):
    def test_screen_off_invalidates_stale_transcription_before_stop_lifecycle(self):
        screen = between("private val screenOffReceiver = object : BroadcastReceiver() {",
                         "private var ttsReady by mutableStateOf(false)")
        self.assertIn("Intent.ACTION_SCREEN_OFF", screen)
        self.assertIn("speechSession.cancel()", screen)
        self.assertIn("recognizer?.cancel()", screen)
        self.assertIn("listening = false", screen)
        self.assertLess(screen.index("speechSession.cancel()"),
                        screen.index("recognizer?.cancel()"))
        self.assertLess(screen.index("speechSession.cancel()"),
                        screen.index("tts?.stop()"))

    def test_focus_and_pause_cancel_instead_of_waiting_for_on_stop(self):
        focus = between("override fun onWindowFocusChanged(hasFocus: Boolean) {",
                        "override fun onPause() {")
        pause = between("override fun onPause() {", "override fun onStop() {")
        for region in (focus, pause):
            self.assertIn("speechForeground = false", region)
            self.assertIn("speechSession.cancel()", region)
            self.assertIn("recognizer?.cancel()", region)
            self.assertIn("listening = false", region)
            self.assertIn("tts?.stop()", region)
        self.assertIn("if (!hasFocus)", focus)
        self.assertLess(pause.index("speechSession.cancel()"),
                        pause.index("super.onPause()"))

    def test_results_and_errors_require_current_active_request(self):
        self.assertIn("if (!speechSession.complete(requestId)) return", MAIN)
        self.assertIn("if (speechSession.accepts(requestId))", MAIN)
        self.assertIn("requestId = if (requestId == Long.MAX_VALUE)", SESSION)
        self.assertIn("pending = false", SESSION)
        self.assertNotIn("java.net", SESSION)


if __name__ == "__main__":
    unittest.main()
