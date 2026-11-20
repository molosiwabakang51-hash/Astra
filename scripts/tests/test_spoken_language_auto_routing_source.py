"""Offline voice-language detection regression: conservative auto mode and exact accents."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CODE = ROOT / "android/app/src/main/java/com/astra/agent"
RULES = (CODE / "AstraSpokenLanguageHeuristics.kt").read_text(encoding="utf-8")
POLICY = (CODE / "AstraVoiceAccentPolicy.kt").read_text(encoding="utf-8")
MAIN = (CODE / "MainActivity.kt").read_text(encoding="utf-8")

class SpokenLanguageAutoRoutingSourceTest(unittest.TestCase):
    def test_conservative_latin_profiles_and_model_independence(self):
        for lang in ("FRENCH", "SPANISH", "PORTUGUESE", "GERMAN",
                     "ITALIAN", "TURKISH", "SWAHILI", "SETSWANA"):
            self.assertIn("Profile(SpeechLanguage." + lang + ",", RULES)
        self.assertIn("anchors.toSet().size < 3", RULES)
        self.assertIn("matches * 3 < tokens.size", RULES)
        self.assertIn("matches <= english", RULES)
        self.assertIn("best.score - matched[1].score < 4", RULES)
        self.assertIn("if (text.length !in 40..50_000) return null", RULES)
        self.assertIn("private val englishMarkers", RULES)
        for forbidden in ("http://", "https://", "OkHttp", "java.net",
                          "SpeechRecognizer", "createOnDevice", "download", "gguf"):
            self.assertNotIn(forbidden, RULES)

    def test_exact_locale_and_native_script_routing_still_gate_playback(self):
        self.assertIn("AstraSpokenLanguageHeuristics.detect(excerpt, configured) ?: configured", POLICY)
        self.assertIn("kana + han", POLICY)
        self.assertIn("SpeechLanguage.ARABIC", POLICY)
        self.assertIn("SpeechLanguage.URDU", POLICY)
        self.assertIn("SpeechLanguage.JAPANESE", POLICY)
        self.assertIn("if (explicit != null) return explicit", POLICY)
        self.assertIn("matchingSpeechVoice(selected, offlineOnly)", MAIN)
        self.assertIn("confirmedSpeechVoice(selected, offlineOnly)", MAIN)
        self.assertIn("AstraVoiceAccentPolicy.forPlayback(", MAIN)
        self.assertIn("previewLanguage ?: lessonLanguage ?: forReply(", POLICY)

if __name__ == "__main__":
    unittest.main()
