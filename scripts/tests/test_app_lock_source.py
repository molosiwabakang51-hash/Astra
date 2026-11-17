"""Static defense-in-depth guard for the opt-in Android app lock.

This is not an authentication penetration test or replacement for Android
hardware/instrumentation tests. It blocks obvious accidental regressions.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
LOCK = (APP / "AstraAppLock.kt").read_text(encoding="utf-8")
SCREEN = (APP / "AstraLockedWorkspace.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")


class AstraAppLockSourceTest(unittest.TestCase):
    def test_gate_precedes_private_chat_composition(self):
        self.assertIn("if (AstraAppLockPolicy.requiresUnlock(", MAIN)
        self.assertLess(
            MAIN.index("AstraLockedWorkspace("),
            MAIN.index("val history = remember"),
        )
        self.assertIn("return@MaterialTheme", MAIN)
        self.assertIn("override fun onStop()", MAIN)
        self.assertIn("unlockedAfterBackground(true)", MAIN)

    def test_strong_system_auth_only_and_default_off(self):
        self.assertIn("BiometricPrompt.Builder(activity)", LOCK)
        self.assertIn("BiometricManager.Authenticators.BIOMETRIC_STRONG", LOCK)
        self.assertIn("BiometricManager.Authenticators.DEVICE_CREDENTIAL", LOCK)
        self.assertIn(".getBoolean(ENABLED_KEY, false)", LOCK)
        self.assertIn("onAuthenticationSucceeded", LOCK)
        self.assertIn("onAuthenticationError", LOCK)
        self.assertNotIn("onAuthenticationFailed", LOCK)
        self.assertNotIn("recordAudio", LOCK)

    def test_privacy_cover_and_no_new_permissions(self):
        self.assertIn("WindowManager.LayoutParams.FLAG_SECURE", LOCK)
        self.assertIn("activity.setRecentsScreenshotEnabled(false)", LOCK)
        self.assertIn("This does not encrypt existing files.", MAIN)
        self.assertIn("android:allowBackup=\"false\"", MANIFEST)
        self.assertIn("android.permission.USE_BIOMETRIC", MANIFEST)

    def test_lock_screen_never_loads_private_content(self):
        self.assertIn("Unlock with fingerprint or PIN", SCREEN)
        self.assertNotIn("loadHistory(", SCREEN)
        self.assertNotIn("AstraBookLibrary(", SCREEN)
        self.assertNotIn("history", SCREEN.lower())


if __name__ == "__main__":
    unittest.main()
