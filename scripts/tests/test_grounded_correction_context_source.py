"""Static contracts for source-grounded, explicit private-memory continuity.

These complement Kotlin/JVM logic tests; they do not prove phone inference.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
GROUND = (JAVA / "AstraGroundedMemory.kt").read_text(encoding="utf-8")
CORRECTIONS = (JAVA / "AstraGroundedCorrections.kt").read_text(encoding="utf-8")

class GroundedCorrectionContextContracts(unittest.TestCase):
    def test_only_owner_messages_supply_corrected_chat_evidence(self):
        self.assertIn('entry.role != "You"', GROUND)
        self.assertIn('AstraGroundedCorrections.annotated(', GROUND)
        self.assertIn('retrieveChat(current,', GROUND)
        self.assertIn('retrieveChat(chat.messages,', GROUND)
        self.assertIn('if (entry.role != "You") continue', CORRECTIONS)
        self.assertIn('if (!isCorrection(entry.text)) break', CORRECTIONS)

    def test_saved_model_notes_are_explicitly_unverified(self):
        self.assertIn('unverifiedAstraReply', GROUND)
        self.assertIn('} else if (request.notesOnly)', GROUND)
        self.assertIn('UNVERIFIED saved Astra reply', GROUND)
        self.assertIn('not an owner statement or a verified fact', GROUND)

    def test_bounded_safe_explicit_retrieval_without_cloud(self):
        self.assertIn('candidateText = entry.text.take(2_000)', GROUND)
        self.assertIn('corrections.size == 2', CORRECTIONS)
        self.assertIn('Character.isHighSurrogate', CORRECTIONS)
        self.assertNotIn('HttpURLConnection', GROUND)
        self.assertNotIn('https://', CORRECTIONS)

if __name__ == "__main__":
    unittest.main()
