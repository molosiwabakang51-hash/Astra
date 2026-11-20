"""Conservative English AUTO speech selection without replacing explicit accents."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
HEURISTICS = (APP / "AstraSpokenLanguageHeuristics.kt").read_text(encoding="utf-8")
POLICY = (APP / "AstraVoiceAccentPolicy.kt").read_text(encoding="utf-8")
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")


class EnglishAutoSpeechSourceTests(unittest.TestCase):
    def test_long_english_requires_multiple_distinct_strong_cues(self):
        self.assertIn("private val englishContentCues", HEURISTICS)
        self.assertIn("private val englishStrongCues", HEURISTICS)
        self.assertIn("englishHits.size >= 8", HEURISTICS)
        self.assertIn("englishHits.toSet().size >= 6", HEURISTICS)
        self.assertIn("strongHits.toSet().size >= 3", HEURISTICS)
        self.assertIn("englishHits.size * 5 >= tokens.size * 2", HEURISTICS)

    def test_explicit_and_foreign_detection_still_take_priority(self):
        self.assertIn("if (matched.size > 1 && best.score - matched[1].score < 4)", HEURISTICS)
        self.assertIn("if (explicit != null) return explicit", POLICY)
        self.assertIn("AstraSpokenLanguageHeuristics.detect(excerpt, configured)", POLICY)
        self.assertIn("SpeechLanguage.ENGLISH_UK", HEURISTICS)
        self.assertIn("SpeechLanguage.ENGLISH_US", HEURISTICS)
        self.assertIn("matchingSpeechVoice(selected, offlineOnly)", MAIN)
        self.assertIn("confirmedSpeechVoice(selected, offlineOnly)", MAIN)

    def test_detection_remains_local_and_requires_installed_region_exact_pack(self):
        for forbidden in ("http://", "https://", "java.net", "SpeechRecognizer",
                          "createOnDevice", "download", "gguf", "openConnection("):
            self.assertNotIn(forbidden, HEURISTICS)
        self.assertIn("sameAccent(requestedTag, it.localeTag)", POLICY)
        self.assertIn("it.installed", POLICY)
        self.assertIn("!offlineOnly || !it.networkRequired", POLICY)


if __name__ == "__main__":
    unittest.main()
