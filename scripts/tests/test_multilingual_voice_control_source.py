"""Integration guard: selected voice language must control STT and TTS without remote services."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
VOICE = (JAVA / "AstraVoiceLanguageCommand.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")

class VoiceLanguageControlTest(unittest.TestCase):
    def test_voice_and_model_language_switches_are_explicit(self):
        self.assertIn("internal object AstraVoiceLanguageCommand", VOICE)
        self.assertIn("fun parse(raw: String): Selection?", VOICE)
        self.assertIn("SpeechLanguage.FRENCH", VOICE)
        self.assertIn("AstraReplyLanguage.Choice.FRENCH", VOICE)
        self.assertIn("AstraVoiceLanguageCommand.parse(normalized)", MAIN)
        self.assertIn('storage.setString("speech_language", chosen.speech.tag)', MAIN)
        self.assertIn('storage.setString("astra_ai_reply_language", chosen.reply.id)', MAIN)
        self.assertIn("selectOfflineVoice()", MAIN)

    def test_language_appropriate_test_voice_and_offline_limitations(self):
        self.assertIn("fun testPhrase(language: SpeechLanguage)", VOICE)
        self.assertIn("Bonjour. Je suis Astra.", VOICE)
        self.assertIn("Я Астра", VOICE)
        self.assertIn("No offline ", MAIN)
        self.assertIn("offline recognition depends", VOICE.lower())
        self.assertIn("non-network", VOICE)
        self.assertIn("AstraVoiceLanguageCommand.testPhrase(speechLanguage)", MAIN)
        self.assertIn("SpeechRecognizer.createOnDeviceSpeechRecognizer(", MAIN)

    def test_no_network_or_new_permission_is_needed(self):
        for forbidden in ("http://", "https://", "java.net", "OkHttp",
                          "trainModel(", "INTERNET", "RECORD_AUDIO", "gguf"):
            self.assertNotIn(forbidden, VOICE)

if __name__ == "__main__":
    unittest.main()
