"""Visual legibility contract: status and disabled navigation text cannot fade away."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
VISUALS = (APP / "AstraVisuals.kt").read_text(encoding="utf-8")
TRAY = (APP / "AstraNavigationTray.kt").read_text(encoding="utf-8")
AURORA = (APP / "AstraAurora.kt").read_text(encoding="utf-8")


class ReadableStatusAndTraySourceTests(unittest.TestCase):
    def test_core_status_is_at_least_12sp_and_semibold_or_stronger(self):
        self.assertIn("AstraAurora.statusLabel(listening, thinking)", VISUALS)
        self.assertIn("fontSize = 12.sp,", VISUALS)
        self.assertIn("fontWeight = FontWeight.Bold,", VISUALS)
        self.assertNotIn("fontSize = 10.sp", VISUALS)
        for label in ('"LISTENING"', '"GENERATING"', '"LOCAL ASSISTANT"'):
            self.assertIn(label, AURORA)

    def test_disabled_navigation_remains_visible_without_becoming_actionable(self):
        self.assertIn("color = if (disabled) AstraAurora.muted", TRAY)
        self.assertNotIn("AstraAurora.muted.copy(alpha = 0.45f)", TRAY)
        self.assertIn(".clickable(enabled = !disabled) { onAction(action) }", TRAY)
        self.assertIn("action == AstraTrayAction.READ_LAST_REPLY && !canReadLastReply", TRAY)
        self.assertIn("contentDescription = when (action)", TRAY)
        self.assertIn("contentDescription = \"Close navigation\"", TRAY)
        for forbidden in ("HttpURLConnection", "WebView(", "Runtime.getRuntime"):
            self.assertNotIn(forbidden, VISUALS)
            self.assertNotIn(forbidden, TRAY)


if __name__ == "__main__":
    unittest.main()
