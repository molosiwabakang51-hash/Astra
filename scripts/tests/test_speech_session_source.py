"""Speech UX privacy checks: no cancelled/late dictation may submit chat text.

Source tests complement JVM tests, not a substitute for microphone testing.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
SESSION = (JAVA / "AstraSpeechSession.kt").read_text(encoding="utf-8")


class SpeechSessionSourceTests(unittest.TestCase):
    def test_mic_cancel_invalidates_queued_asr_callbacks(self):
        for token in (
            "private val speechSession = AstraSpeechSession()",
            "if (speechSession.isPending() || listening)",
            "speechSession.cancel()",
            "engine.cancel()",
            "val requestId = speechSession.begin()",
            "if (!speechSession.complete(requestId)) return",
            "if (speechSession.accepts(requestId)) listening = true",
        ):
            self.assertIn(token, MAIN)
        self.assertNotIn("engine.stopListening()", MAIN)
        self.assertNotIn("speechResultCallback?.invoke", MAIN)

    def test_speech_output_does_not_escape_foreground_or_secure_lock(self):
        self.assertIn("private var speechForeground = false", MAIN)
        self.assertIn("AstraSpeechPrivacy.mayUseVoice(", MAIN)
        self.assertIn("speechForeground = true", MAIN)
        self.assertGreaterEqual(MAIN.count("speechForeground = false"), 2)
        self.assertIn("override fun onStop()", MAIN)
        self.assertIn("tts?.stop()", MAIN)
        self.assertIn("recognizer?.cancel()", MAIN)
        self.assertIn("foreground && (!appLockEnabled || workspaceUnlocked)", SESSION)

    def test_dictation_has_no_persistence_or_network_dependency(self):
        for forbidden in ("SharedPreferences(", "http://", "https://",
                          "java.net", "requestPermissions", "WRITE_EXTERNAL_STORAGE"):
            self.assertNotIn(forbidden, SESSION)
        self.assertIn('RecognizerIntent.EXTRA_PREFER_OFFLINE,', MAIN)
        self.assertIn("SpeechRecognizer.createOnDeviceSpeechRecognizer(", MAIN)


if __name__ == "__main__":
    unittest.main()
