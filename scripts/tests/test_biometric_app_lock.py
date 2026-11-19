"""Static security regression checks for Astra's opt-in Android system app lock.

These are source guards, not proof of device-specific biometric behavior.
Physical device cancellation, credential fallback, Android recents, and app
background/relaunch must still be verified.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"


class AstraBiometricLockContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
        cls.lock = (JAVA / "AstraAppLock.kt").read_text(encoding="utf-8")
        cls.lock_screen = (JAVA / "AstraLockedWorkspace.kt").read_text(encoding="utf-8")
        cls.manifest = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

    def test_system_biometrics_permission_and_no_extra_exported_components(self):
        self.assertIn('android.permission.USE_BIOMETRIC', self.manifest)
        self.assertIn('android:allowBackup="false"', self.manifest)
        self.assertEqual(self.manifest.count('android:exported="true"'), 1)

    def test_opt_in_defaults_off_and_never_downgrades_to_voice_or_weak_biometrics(self):
        # A missing key defaults to off, but corrupt existing preferences
        # must never disable an already opted-in lock.
        self.assertIn('prefs.contains(ENABLED_KEY)', self.lock)
        self.assertIn('prefs.all[ENABLED_KEY]', self.lock)
        self.assertIn('enabledFromStoredPreference(', self.lock)
        self.assertIn('if (!present) false else (value as? Boolean ?: true)', self.lock)
        self.assertIn('}.getOrElse { true }', self.lock)
        self.assertNotIn('.getBoolean(ENABLED_KEY, false)', self.lock)
        self.assertIn('BIOMETRIC_STRONG', self.lock)
        self.assertIn('DEVICE_CREDENTIAL', self.lock)
        self.assertNotIn('BIOMETRIC_WEAK', self.lock)
        self.assertNotIn('SpeechRecognizer', self.lock)
        self.assertNotIn('VoiceRecognition', self.lock)
        self.assertIn('onAuthenticationSucceeded(', self.lock)
        self.assertIn('onAuthenticationError(', self.lock)

    def test_sensitive_composition_is_gated_before_loading_saved_chats(self):
        self.assertIn('if (AstraAppLockPolicy.requiresUnlock(', self.main)
        self.assertIn('AstraLockedWorkspace(', self.main)
        self.assertIn('return@MaterialTheme', self.main)
        self.assertLess(
            self.main.index('AstraLockedWorkspace('),
            self.main.index('val history = remember {')
        )
        self.assertLess(
            self.main.index('secureLockEnabled = AstraAppLock.enabled(this)'),
            self.main.index('setContent {')
        )

    def test_background_lock_interrupts_microphone_and_speech(self):
        start = self.main.index('override fun onStop()')
        end = self.main.index('override fun onDestroy()', start)
        on_stop = self.main[start:end]
        self.assertIn('if (secureLockEnabled)', on_stop)
        self.assertIn('lockPrivateWorkspace()', on_stop)
        lock_start = self.main.index('private fun lockPrivateWorkspace()')
        lock_end = self.main.index('private fun checkForegroundIdleTimeout()', lock_start)
        lock_body = self.main[lock_start:lock_end]
        for required in ('recognizer?.cancel()', 'listening = false',
                         'tts?.stop()', 'workspaceUnlocked = false'):
            self.assertIn(required, lock_body)

    def test_only_one_biometric_permission_declared(self):
        self.assertEqual(self.manifest.count('android:name="android.permission.USE_BIOMETRIC"'), 1)

    def test_opted_in_app_disables_screenshots_and_recent_task_preview(self):
        self.assertIn('WindowManager.LayoutParams.FLAG_SECURE', self.lock)
        self.assertIn('setRecentsScreenshotEnabled(false)', self.lock)
        self.assertIn('AstraAppLock.protectRecentsAndScreenshots(', self.main)
        self.assertIn('AstraAppLockPolicy.unlockedOnColdLaunch(secureLockEnabled)', self.main)
        self.assertIn('Unlock with fingerprint or PIN', self.lock_screen)
        self.assertNotIn('storage.loadHistory(', self.lock_screen)
        self.assertNotIn('AstraBookLibrary(', self.lock_screen)


if __name__ == "__main__":
    unittest.main()
