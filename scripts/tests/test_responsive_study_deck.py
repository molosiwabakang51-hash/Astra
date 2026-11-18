"""Source regression checks for responsive Study and low-motion Mission Control.

These tests are not substitutes for visual/device or accessibility testing.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
STUDY = (JAVA / "AstraStudyHub.kt").read_text(encoding="utf-8")
DECK = (JAVA / "AstraCommandDeck.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class ResponsiveAstraUiTest(unittest.TestCase):
    def test_study_uses_available_width_and_android_text_scale(self):
        self.assertIn("BoxWithConstraints(Modifier.fillMaxSize())", STUDY)
        self.assertIn("LocalDensity.current.fontScale", STUDY)
        self.assertIn("AstraStudyLayout.columnsFor(maxWidth.value, fontScale)", STUDY)
        self.assertIn("visibleSubjects.chunked(columns)", STUDY)
        self.assertIn("AstraStudySubjectFilter.apply(", STUDY)
        self.assertIn("compact = columns == 1", STUDY)
        self.assertIn("min = if (compact) 116.dp else 152.dp", STUDY)
        self.assertNotIn("AstraStudyCatalog.subjects.chunked(2)", STUDY)

    def test_mission_control_static_progress_respects_animation_setting(self):
        self.assertIn("Settings.Global.ANIMATOR_DURATION_SCALE", DECK)
        self.assertIn("else EnterTransition.None", DECK)
        self.assertIn("else ExitTransition.None", DECK)
        self.assertNotIn("rememberInfiniteTransition", DECK)
        self.assertNotIn("infiniteRepeatable", DECK)
        self.assertNotIn("label = \"scanline\"", DECK)
        self.assertIn("AstraDeckMetric(\"OPEN\"", DECK)
        self.assertIn("AstraDeckMetric(\"DONE\"", DECK)
        self.assertIn("AstraDeckMetric(\"NOTES\"", DECK)

    def test_visible_command_actions_have_accessible_touch_targets(self):
        self.assertIn(".heightIn(min = 48.dp)", DECK)
        self.assertIn('contentDescription = "Run $command"', DECK)
        self.assertIn('contentDescription = "Mission Control, $status"', DECK)
        self.assertIn("commandDeckExpanded = false", MAIN)
        self.assertIn("AstraCommandDeck(", MAIN)
        self.assertIn("onExpandedChange = { commandDeckExpanded = it }", MAIN)


if __name__ == "__main__":
    unittest.main()
