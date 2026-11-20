"""Regression guard: private TTS must stop on lock and foreground loss.

Pure unit tests validate the policy state matrix. These source checks ensure
Android lifecycle hooks and screen-off callbacks actually enforce it.
Actual TTS engine behavior requires an Android device acceptance test.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
PRIVACY = (JAVA / "AstraSpeechSession.kt").read_text(encoding="utf-8")


def block_after(start: str, end: str) -> str:
    assert MAIN.count(start) == 1, start
    return MAIN.split(start, 1)[1].split(end, 1)[0]


class BackgroundSpeechPrivacySourceTest(unittest.TestCase):
    def test_pause_stops_speech_before_stop_and_above_super(self):
        pause = block_after("    override fun onPause() {", "    override fun onStop() {")
        self.assertIn("speechForeground = false", pause)
        self.assertIn("tts?.stop()", pause)
        self.assertLess(pause.index("tts?.stop()"), pause.index("super.onPause()"))
        stop = block_after("    override fun onStop() {", "    override fun onDestroy() {")
        self.assertIn("speechForeground = false", stop)
        self.assertIn("tts?.stop()", stop)
        locked = block_after("    private fun lockPrivateWorkspace() {", "    private fun checkForegroundIdleTimeout()")
        self.assertIn("tts?.stop()", locked)

    def test_offscreen_notification_and_keyguard_are_all_gated(self):
        self.assertIn("override fun onWindowFocusChanged(hasFocus: Boolean)", MAIN)
        focus = block_after("    override fun onWindowFocusChanged(hasFocus: Boolean) {",
                            "    override fun onPause() {")
        self.assertIn("if (!hasFocus)", focus)
        self.assertIn("speechForeground = false", focus)
        self.assertIn("tts?.stop()", focus)
        self.assertIn("Intent.ACTION_SCREEN_OFF", MAIN)
        self.assertIn("registerReceiver(screenOffReceiver, IntentFilter(", MAIN)
        self.assertIn("unregisterReceiver(screenOffReceiver)", MAIN)
        self.assertIn("as? PowerManager)?.isInteractive == true", MAIN)
        self.assertIn("as? KeyguardManager)?.isKeyguardLocked == false", MAIN)
        self.assertIn("windowFocused = hasWindowFocus()", MAIN)
        self.assertIn("displayInteractive && deviceUnlocked && windowFocused", PRIVACY)

    def test_tts_checks_actual_device_state_immediately_before_queuing(self):
        speak = block_after("    private fun speak(\n",
                            "    private fun initializeRecognizer() {")
        self.assertIn("if (!ttsReady || !canPlaySpeechNow()) return", speak)
        self.assertIn("if (!canPlaySpeechNow()) {", speak)
        self.assertLess(speak.index("if (!canPlaySpeechNow()) {"),
                        speak.index("tts?.speak(segment, mode"))
        # A non-foreground onStart must not authorize private speech.
        onstart = block_after("    override fun onStart() {", "    override fun onResume() {")
        self.assertIn("speechForeground = false", onstart)
        self.assertNotIn("speechForeground = true", onstart)
        for forbidden in ("android.permission.WAKE_LOCK", "android.permission.FOREGROUND_SERVICE",
                          "startForegroundService(", "acquireWakeLock("):
            self.assertNotIn(forbidden, MAIN)


if __name__ == "__main__":
    unittest.main()
