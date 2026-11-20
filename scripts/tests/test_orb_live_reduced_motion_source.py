"""The Astra orb must stop if Android disables animations while Chat is open."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ORBIT = (ROOT / "android/app/src/main/java/com/astra/agent/AstraAurora.kt").read_text(
    encoding="utf-8"
)
MAIN = (ROOT / "android/app/src/main/java/com/astra/agent/MainActivity.kt").read_text(
    encoding="utf-8"
)
VISUALS = (ROOT / "android/app/src/main/java/com/astra/agent/AstraVisuals.kt").read_text(
    encoding="utf-8"
)


class LiveReducedMotionSourceTests(unittest.TestCase):
    def test_system_scale_changes_update_existing_composition(self):
        for marker in (
            "Settings.Global.ANIMATOR_DURATION_SCALE",
            "Settings.Global.getUriFor(",
            "context.contentResolver.registerContentObserver(",
            "context.contentResolver.unregisterContentObserver(observer)",
            "override fun onChange(selfChange: Boolean)",
            "motion = AstraAurora.motionEnabled(AstraAurora.animatorScale(context))",
            "DisposableEffect(context)",
            "onDispose {",
        ):
            self.assertIn(marker, ORBIT)
        # Read failure must not force decorative motion against a possibly
        # unavailable accessibility preference.
        self.assertIn("}.getOrDefault(0f)", ORBIT)
        self.assertIn("motionEnabled(scale: Float): Boolean = scale.isFinite()", ORBIT)
        self.assertIn("val rotation = if (motion && (!compact || listening || thinking))", ORBIT)

    def test_observer_does_not_leak_app_or_expand_permissions(self):
        self.assertIn("ContentObserver(Handler(Looper.getMainLooper()))", ORBIT)
        self.assertIn("runCatching {", ORBIT)
        self.assertIn("if (registered) runCatching {", ORBIT)
        self.assertNotIn("while (true)", ORBIT)
        self.assertNotIn("Global.getInt(context.contentResolver, \"secure", ORBIT)
        for forbidden in ("requestPermissions", "HttpURLConnection", "WebView(",
                          "URL(", "Thread.sleep", "getLastKnownLocation"):
            self.assertNotIn(forbidden, ORBIT)
        self.assertIn("AstraBrand(", MAIN)
        self.assertIn("AstraOrb(", VISUALS)


if __name__ == "__main__":
    unittest.main()
