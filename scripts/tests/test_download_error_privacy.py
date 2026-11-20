"""Guard owner-facing download status against raw Java exception disclosure."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
SAFE = (APP / "AstraDownloadFailureMessage.kt").read_text(encoding="utf-8")


class ModelDownloadPrivacyTests(unittest.TestCase):
    def test_visible_model_download_error_uses_safe_mapper(self):
        start = MAIN.index("val count = AstraModelDownload.download(")
        end = MAIN.index("modelDownloadJob = null", start)
        block = MAIN[start:end]
        self.assertIn("AstraDownloadFailureMessage.forOwner(error)", block)
        self.assertNotIn('(error.message ?: "Check connection.")', block)

    def test_mapper_never_returns_exception_message_or_paths(self):
        self.assertIn("fun forOwner(failure: Exception): String", SAFE)
        self.assertIn("failure.message.orEmpty().lowercase", SAFE)
        self.assertNotIn("return source", SAFE)
        self.assertNotIn("return failure.message", SAFE)
        self.assertIn('"http 416" in source', SAFE)
        self.assertIn("SHA-256", SAFE)


if __name__ == "__main__":
    unittest.main()
