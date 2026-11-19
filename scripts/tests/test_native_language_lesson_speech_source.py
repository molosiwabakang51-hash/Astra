"""Voice unit contract for native-accent bilingual lesson pronunciation."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
LESSON = (JAVA / "AstraLessonSpeech.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")

class LessonAccentRoutingTest(unittest.TestCase):
    def test_target_speech_never_pronounces_english_gloss(self):
        self.assertIn("internal object AstraLessonSpeech", LESSON)
        self.assertIn('line.indexOf(" — ")', LESSON)
        self.assertIn("line.substring(0, divider)", LESSON)
        self.assertIn("val gloss = line.substring(divider + 3).trim()", LESSON)
        self.assertIn('return Sample(target, spokenPhrases.joinToString(" "), spokenPhrases.size)', LESSON)
        self.assertIn("if (totalLength + extra > 700) break", LESSON)
        self.assertIn("spokenPhrases.add(complete)", LESSON)
        self.assertNotIn("val spoken = phrases.joinToString", LESSON)
        self.assertIn("phraseCount", LESSON)
        self.assertIn("AstraLessonSpeech.fromLesson(", MAIN)
        self.assertIn("val speechText = lessonSpeech?.spokenText ?: text", MAIN)
        # Speech may be split into bounded ordered utterances, but the
        # source remains the language-extracted spokenText instead of English glosses.
        self.assertIn("AstraSpeechChunks.partition(", MAIN)
        self.assertIn("speechText,", MAIN)
        self.assertIn("tts?.speak(segment, mode", MAIN)
        self.assertIn("TextToSpeech.QUEUE_FLUSH", MAIN)
        self.assertIn("TextToSpeech.QUEUE_ADD", MAIN)

    def test_regional_accents_and_fail_closed_installed_voice_check(self):
        for name in ("RUSSIAN", "SETSWANA", "FRENCH_CANADA", "SPANISH_MEXICO",
                     "PORTUGUESE_BRAZIL", "GERMAN", "SWAHILI", "JAPANESE",
                     "MANDARIN"):
            self.assertIn("SpeechLanguage." + name, LESSON)
        self.assertIn("matchingSpeechVoice(selected, offlineOnly)", MAIN)
        self.assertIn("confirmedSpeechVoice(selected, offlineOnly)", MAIN)
        self.assertLess(MAIN.index("AstraLessonSpeech.fromLesson("),
                        MAIN.index("matchingSpeechVoice(selected, offlineOnly)"))
        for forbidden in ("http://", "https://", "OkHttp", "SpeechRecognizer",
                          "INTERNET", "RECORD_AUDIO", "GGUF"):
            self.assertNotIn(forbidden, LESSON)

if __name__ == "__main__":
    unittest.main()
