"""Voice diagnostic must test the speech locale chosen in Settings, not AI reply style."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
VOICE = (APP / "AstraVoiceAccentPolicy.kt").read_text(encoding="utf-8")


class VoicePreviewSelectedLocaleSourceTests(unittest.TestCase):
    def test_settings_preview_explicitly_pins_selected_speech_locale(self):
        self.assertIn("previewLanguage = speechLanguage", MAIN)
        self.assertIn("AstraVoiceLanguageCommand.testPhrase(speechLanguage)", MAIN)
        self.assertIn("previewLanguage: SpeechLanguage? = null", MAIN)
        self.assertIn("AstraVoiceAccentPolicy.forPlayback(", MAIN)
        self.assertIn("previewLanguage ?: lessonLanguage ?: forReply(", VOICE)

    def test_no_region_substitution_or_network_bypass_from_preview(self):
        self.assertIn("matchingSpeechVoice(selected, offlineOnly)", MAIN)
        self.assertIn("confirmedSpeechVoice(selected, offlineOnly)", MAIN)
        self.assertIn("AstraVoiceAccentPolicy.sameAccent(", MAIN)
        self.assertIn("it.networkRequired", VOICE)
        self.assertIn("it.installed", VOICE)
        self.assertIn("voice.isNetworkConnectionRequired", MAIN)
        self.assertNotIn("java.net", VOICE)


if __name__ == "__main__":
    unittest.main()
