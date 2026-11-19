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
        self.assertIn("lockPrivateWorkspace()", MAIN)

    def test_selected_delay_cannot_skip_system_auth_or_private_preview_protection(self):
        self.assertIn("setBackgroundDelayMillis(", LOCK)
        self.assertIn("getLong(BACKGROUND_DELAY_KEY, 0L)", LOCK)
        self.assertIn("saved in AstraAppLockPolicy.backgroundDelaysMillis", LOCK)
        self.assertIn("requestWorkspaceAuthentication {", MAIN)
        self.assertIn("backgroundLockDelayMillis = AstraAppLock.backgroundDelayMillis(this)", MAIN)
        self.assertIn("override fun onStart()", MAIN)
        self.assertIn("android.os.SystemClock.elapsedRealtime()", MAIN)
        self.assertIn("backgroundSessionWasUnlocked = workspaceUnlocked", MAIN)
        self.assertIn("lockPrivateWorkspace()", MAIN)
        self.assertIn("if (secureLockEnabled && leftAt != null && backgroundSessionWasUnlocked)", MAIN)
        self.assertIn("backgroundSessionWasUnlocked = false", MAIN)
        self.assertIn("Screen capture stays blocked at every delay setting.", MAIN)

    def test_inactivity_lock_is_foreground_only_and_auth_protected(self):
        self.assertIn("setIdleTimeoutMillis(", LOCK)
        self.assertIn("getLong(IDLE_TIMEOUT_KEY, 0L)", LOCK)
        self.assertIn("override fun onUserInteraction()", MAIN)
        self.assertIn("override fun onPause()", MAIN)
        self.assertIn("idleLockHandler.removeCallbacks(idleLockRunnable)", MAIN)
        self.assertIn("AstraAppLockPolicy.shouldLockForInactivity(", MAIN)
        self.assertIn("lockPrivateWorkspace()", MAIN)
        self.assertIn("idleTimeoutMillis = AstraAppLock.idleTimeoutMillis(this)", MAIN)
        self.assertIn("requestWorkspaceAuthentication {", MAIN)

    def test_strong_system_auth_only_and_default_off(self):
        self.assertIn("BiometricPrompt.Builder(activity)", LOCK)
        self.assertIn("BiometricManager.Authenticators.BIOMETRIC_STRONG", LOCK)
        self.assertIn("BiometricManager.Authenticators.DEVICE_CREDENTIAL", LOCK)
        # The original getBoolean() could throw on a corrupt pref; a
        # simple catch/default-false would bypass an already-enabled lock.
        self.assertNotIn(".getBoolean(ENABLED_KEY, false)", LOCK)
        self.assertIn("prefs.contains(ENABLED_KEY)", LOCK)
        self.assertIn("prefs.all[ENABLED_KEY]", LOCK)
        self.assertIn("enabledFromStoredPreference(", LOCK)
        self.assertIn("if (!present) false else (value as? Boolean ?: true)", LOCK)
        self.assertIn("}.getOrElse { true }", LOCK)
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
