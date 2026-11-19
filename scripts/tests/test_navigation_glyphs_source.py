"""Static checks for coherent Astra's six icon-menu destinations.

Canvas vectors are non-networked and share existing labelled menu tap targets.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
GLYPHS = (JAVA / "AstraNavigationGlyph.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
TRAY = (JAVA / "AstraNavigationTray.kt").read_text(encoding="utf-8")


class NavigationGlyphSourceTest(unittest.TestCase):
    def test_six_core_destinations_have_drawn_icons(self):
        for name in ("CHAT", "TASKS", "NOTES", "TOOLS", "SETTINGS", "STUDY"):
            self.assertIn("AstraNavSection." + name + " ->", GLYPHS)
        self.assertIn("Canvas(Modifier.size(23.dp))", GLYPHS)
        self.assertIn("val line = 2.dp.toPx()", GLYPHS)
        self.assertNotIn("http://", GLYPHS)
        self.assertNotIn("https://", GLYPHS)

    def test_new_left_tray_uses_vectors_and_retains_labelled_destinations(self):
        self.assertIn("AstraNavigationTray(", MAIN)
        self.assertIn("AstraNavigationGlyph(", TRAY)
        self.assertIn("AstraNavSection.entries[index]", TRAY)
        for title in ('"Chat"', '"Tasks"', '"Notes"', '"Tools"',
                      '"Settings"', '"Study"'):
            self.assertIn(title, TRAY)
        self.assertNotIn('listOf("◌", "✓", "▤", "⌘", "⚙", "✦")[index]', MAIN)
        self.assertIn("navigationPopupOpen = true", MAIN)
        self.assertIn("tab = index", MAIN)
        self.assertIn("RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)", TRAY)
        self.assertIn("Alignment.CenterStart", TRAY)


if __name__ == "__main__":
    unittest.main()
