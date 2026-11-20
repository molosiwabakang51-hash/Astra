"""Exact internal SAF stall errors yield safe, actionable owner recovery advice."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
GUARD = (APP / "AstraProgressInputStream.kt").read_text(encoding="utf-8")
ERROR = (APP / "AstraPrivateError.kt").read_text(encoding="utf-8")
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")


class StalledDocumentOwnerAdviceSourceTests(unittest.TestCase):
    def test_owner_advice_requires_fixed_internal_signature(self):
        self.assertIn("internal const val STALLED_PROVIDER", GUARD)
        self.assertIn("MAX_EMPTY_READS = 16", GUARD)
        self.assertIn("it is IOException &&", ERROR)
        self.assertIn("it.message == AstraProgressInputStream.STALLED_PROVIDER", ERROR)
        self.assertIn("Cause.DOCUMENT_STALLED", ERROR)
        self.assertIn("Copy the file to Downloads and select it again from Settings.", ERROR)

    def test_private_exceptions_are_never_echoed_and_ui_uses_the_boundary(self):
        self.assertIn("AstraPrivateError.forOwner(", MAIN)
        self.assertIn("AstraPrivateError.Operation.BOOK_IMPORT", MAIN)
        self.assertIn("it.message.orEmpty().take(350)", ERROR)
        self.assertNotIn('return failure.message', ERROR)
        self.assertNotIn('append(failure.message)', ERROR)
        self.assertNotIn("HttpURLConnection", GUARD)
        self.assertNotIn("contentResolver", GUARD)
        self.assertNotIn("println(", ERROR)


if __name__ == "__main__":
    unittest.main()
