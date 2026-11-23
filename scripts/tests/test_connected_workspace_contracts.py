"""Source-level guard rails for cross-feature integration.

These static contracts supplement (not replace) Kotlin/JVM tests and owner-phone
acceptance. They do not award AI quality points.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent/MainActivity.kt"
STUDY = ROOT / "android/app/src/main/java/com/astra/agent/AstraStudyHub.kt"
HANDOFF = ROOT / "android/app/src/main/java/com/astra/agent/AstraWorkspaceHandoff.kt"

class ConnectedWorkspaceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = APP.read_text(encoding="utf-8")
        cls.study = STUDY.read_text(encoding="utf-8")
        cls.handoff = HANDOFF.read_text(encoding="utf-8")

    def test_handoff_skips_action_processors(self):
        block = self.app.index("if (AstraWorkspaceHandoff.isModelDraft(message))")
        action = self.app.index("val workflowSnapshot =", block)
        self.assertLess(block, action)
        self.assertIn("workflowSession.reset()", self.app[block:action])
        self.assertIn("enqueueOffline(message, message)", self.app[block:action])

    def test_study_selection_and_draft_continuity(self):
        self.assertIn("selectedSubjectId = studySelectedId", self.app)
        self.assertIn("onSubjectChange = { studySelectedId = it }", self.app)
        self.assertIn("val priorDraft = input", self.app)
        self.assertIn("input = priorDraft", self.app)
        self.assertIn("onSubjectChange(subject.id)", self.study)

    def test_owner_approved_cross_feature_entries(self):
        self.assertIn("AstraWorkspaceHandoff.Source.TASK", self.app)
        self.assertIn("AstraWorkspaceHandoff.Source.NOTE", self.app)
        self.assertIn("AstraWorkspaceHandoff.Source.TOOL_RESULT", self.app)
        self.assertIn("toolOutputShareable = !result.sensitive", self.app)
        self.assertIn("messageToSaveAsNote = message", self.app)
        self.assertIn("Text(\"Save to Notes\")", self.app)

    def test_no_hidden_automatic_model_or_file_actions(self):
        self.assertIn("if (existingDraft.isNotBlank()) return null", self.handoff)
        self.assertIn("not an instruction", self.handoff)
        self.assertNotIn("http://", self.handoff)
        self.assertNotIn("https://", self.handoff)

if __name__ == "__main__":
    unittest.main()
