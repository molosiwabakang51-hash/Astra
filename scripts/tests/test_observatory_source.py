"""Static checks for Astra's real, offline-first Observatory redesign.

These guard intent and integration, not device rendering or visual quality.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
WELCOME = (JAVA / "AstraWelcomePanel.kt").read_text(encoding="utf-8")
AURORA = (JAVA / "AstraAurora.kt").read_text(encoding="utf-8")
VISUALS = (JAVA / "AstraVisuals.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class AstraObservatorySourceTest(unittest.TestCase):
    def test_welcome_remains_empty_chat_only_and_no_unrequested_generation(self):
        self.assertIn('fun show(roles: List<String>)', WELCOME)
        self.assertIn('onOpenStudy()', WELCOME)
        self.assertNotIn('"Teach me finance"', WELCOME)
        self.assertIn('AstraWelcomePolicy.commands[1]', WELCOME)
        self.assertIn('AstraWelcomePolicy.commands[2]', WELCOME)
        self.assertIn('AstraWelcomePolicy.commands[3]', WELCOME)
        self.assertIn('AstraWelcomePanel(', MAIN)

    def test_observatory_has_distinct_graphics_and_real_actions(self):
        for marker in (
            'ASTRA  /  OBSERVATORY',
            'Your ideas,',
            'CHOOSE A PATH',
            'AstraOrb(diameter = 176.dp)',
            'AstraObservatoryAction(',
            'Touch the core'.upper(),
            'contentDescription = "Open Astra navigation and workspace"',
        ):
            self.assertIn(marker, WELCOME)
        self.assertIn('.heightIn(min = if (featured) 108.dp else 84.dp)', WELCOME)
        self.assertIn('onOpenNavigation', WELCOME)
        self.assertIn('Brush.linearGradient', WELCOME)

    def test_header_status_is_truthful_and_compact(self):
        self.assertIn('fun statusLabel(listening: Boolean, thinking: Boolean)', AURORA)
        self.assertIn('listening -> "LISTENING"', AURORA)
        self.assertIn('thinking -> "GENERATING"', AURORA)
        self.assertIn('else -> "LOCAL ASSISTANT"', AURORA)
        self.assertIn('thinking = offlineJob?.isActive == true', MAIN)
        self.assertIn('AstraAurora.statusLabel(listening, thinking)', VISUALS)

    def test_vector_identity_uses_no_network_or_raster_download(self):
        self.assertIn('val ticks = if (compact) 12 else 32', AURORA)
        self.assertIn('val facet = androidx.compose.ui.graphics.Path()', AURORA)
        self.assertIn('Settings.Global.ANIMATOR_DURATION_SCALE', AURORA)
        self.assertIn('motion && (!compact || listening || thinking)', AURORA)
        for code in ('http://', 'https://', 'AsyncImage', 'URL(', 'BitmapFactory'):
            self.assertNotIn(code, WELCOME + AURORA)


if __name__ == "__main__":
    unittest.main()
