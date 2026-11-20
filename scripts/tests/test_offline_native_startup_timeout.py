"""Guard that local-engine readiness deadline is not mistaken for owner cancellation."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
BRAIN = (APP / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
WAIT = (APP / "AstraNativeStartupWait.kt").read_text(encoding="utf-8")
UI = (APP / "MainActivity.kt").read_text(encoding="utf-8")


class NativeStartupTimeoutWiring(unittest.TestCase):
    def test_live_native_engine_wait_uses_isolated_timeout_policy(self):
        self.assertIn("AstraNativeStartupWait.awaitReady {", BRAIN)
        self.assertIn("current.state.first { state ->", BRAIN)
        self.assertNotIn("val initialized = withTimeout(30_000L)", BRAIN)
        self.assertIn("withTimeoutOrNull(timeoutMs)", WAIT)
        self.assertIn("Finished(operation())", WAIT)
        self.assertIn("IllegalStateException(TIMEOUT_ADVICE)", WAIT)

    def test_explicit_owner_cancel_remains_cancellation(self):
        self.assertIn("catch (cancel: CancellationException)", UI)
        self.assertIn("catch (error: Exception)", UI)
        self.assertIn("catch (error: Exception)", BRAIN)
        self.assertNotIn("catch (error: CancellationException)", WAIT)
        self.assertNotIn("catch (failure: Throwable)", WAIT)

    def test_feedback_is_content_free_and_not_an_internet_failure(self):
        self.assertIn("Qwen3 1.7B", WAIT)
        self.assertIn("native engine initialization did not finish", WAIT)
        self.assertNotIn("URL(", WAIT)
        self.assertNotIn("HttpURLConnection", WAIT)
        self.assertNotIn("error.message", WAIT)
        self.assertNotIn("failure.message", WAIT)


if __name__ == "__main__":
    unittest.main()
