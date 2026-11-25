"""Memory attribution integration; not semantic entailment or device proof."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
BRAIN = (APP / "AstraOnDeviceBrain.kt").read_text()
MAIN = (APP / "MainActivity.kt").read_text()
MEMORY = (APP / "AstraGroundedMemory.kt").read_text()

class MemoryCitationProvenanceTest(unittest.TestCase):
    def test_full_prompt_and_typed_evidence(self):
        self.assertIn("memoryBrief = groundedBrief", MAIN)
        self.assertIn("groundedBrief = brief", MAIN)
        self.assertIn("memoryBrief?.includedCount(requestPrompt)", BRAIN)
        self.assertIn("finalPrompt.contains(modelPrompt())", MEMORY)
        self.assertLess(BRAIN.index("memoryBrief?.includedCount(requestPrompt)"),
                        BRAIN.index("current.sendUserPrompt("))

    def test_streaming_and_completed_output_are_guarded(self):
        self.assertIn("if (memoryGrounded)", BRAIN)
        self.assertIn("AstraSourceCitationGuard.protectMemory(text, verifiedMemoryPassages)", BRAIN)
        self.assertIn("val partial = guardMemory(AstraSourceCitationGuard.protect(", BRAIN)
        self.assertIn("val attributedAnswer = guardMemory(AstraSourceCitationGuard.protect(", BRAIN)

if __name__ == "__main__":
    unittest.main()
