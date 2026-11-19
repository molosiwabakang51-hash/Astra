"""Guard long-form on-phone TTS segmentation with ordered, privacy-safe playback."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CODE = ROOT / "android/app/src/main/java/com/astra/agent"
CHUNKS = (CODE / "AstraSpeechChunks.kt").read_text(encoding="utf-8")
MAIN = (CODE / "MainActivity.kt").read_text(encoding="utf-8")

class BoundedSpeechQueueTest(unittest.TestCase):
    def test_chunks_are_bounded_unicode_safe_and_never_persisted(self):
        self.assertIn("internal object AstraSpeechChunks", CHUNKS)
        self.assertIn("maxChars: Int = 3_000", CHUNKS)
        self.assertIn("capChunks: Int = 32", CHUNKS)
        self.assertIn("Character.isHighSurrogate(", CHUNKS)
        self.assertIn("Character.isLowSurrogate(", CHUNKS)
        self.assertIn("truncated: Boolean", CHUNKS)
        for forbidden in ("http://", "https://", "OkHttp", "java.net",
                          "File(", "SharedPreferences", "SpeechRecognizer"):
            self.assertNotIn(forbidden, CHUNKS)

    def test_speech_queue_flushes_before_add_and_stops_at_lock(self):
        self.assertIn("AstraSpeechChunks.partition(", MAIN)
        self.assertIn("TextToSpeech.getMaxSpeechInputLength()", MAIN)
        self.assertIn("TextToSpeech.QUEUE_FLUSH", MAIN)
        self.assertIn("TextToSpeech.QUEUE_ADD", MAIN)
        self.assertIn("tts?.speak(segment, mode", MAIN)
        self.assertIn("tts?.stop()", MAIN)
        self.assertIn("Spoken playback was limited for safety.", MAIN)
        self.assertIn("Speech playback failed.", MAIN)

if __name__ == "__main__":
    unittest.main()
