"""Full MainActivity privacy gate for Android provider and inference failures."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
POLICY = (APP / "AstraPrivateError.kt").read_text(encoding="utf-8")


class OwnerErrorPrivacySurfaceTests(unittest.TestCase):
    def test_no_raw_exception_details_enter_ui_or_persisted_chat(self):
        for var in ("error", "failure", "exception", "throwable"):
            self.assertNotRegex(
                MAIN, rf"\b{var}\.message\b",
                f"Raw {var}.message could expose SAF paths, model errors, or chat data"
            )
            self.assertNotIn(f"{var}.toString()", MAIN)

    def test_all_identified_18_error_handlers_use_reviewed_mapper(self):
        # The bounded calculator adds one isolated native planning pass.
        self.assertEqual(MAIN.count("AstraPrivateError.forOwner("), 18)
        self.assertEqual(MAIN.count("AstraPrivateError.Operation.MODEL_CHAT,"), 2)
        for kind in (
            "MODEL_CHAT", "BACKUP_EXPORT", "BACKUP_READ", "BACKUP_RESTORE",
            "BOOK_IMPORT", "BOOK_CLEAR", "VOCAB_IMPORT", "VOCAB_EXPORT",
            "VOCAB_RESTORE", "VOCAB_CLEAR", "MODEL_SELF_TEST",
            "LANGUAGE_CHECKS", "LIBRARY_SYNC", "WORKFLOW_SAVE",
            "FOLDER_LINK", "FOLDER_IMPORT", "FOLDER_REVOKE"
        ):
            self.assertIn("AstraPrivateError.Operation." + kind + ",", MAIN)

    def test_mapper_does_not_copy_raw_provider_content(self):
        self.assertIn("fun forOwner(operation: Operation, failure: Exception): String", POLICY)
        self.assertIn("val description = generateSequence(", POLICY)
        self.assertIn("val cause = category(failure)", POLICY)
        self.assertIn("val reason = when (cause)", POLICY)
        self.assertNotIn('return failure.message', POLICY)
        self.assertNotIn('return description', POLICY)
        self.assertNotIn('throw failure', POLICY)
        self.assertIn('return "$heading $reason $advice"', POLICY)

    def test_native_startup_deadline_cannot_be_mistaken_for_network_or_bad_weights(self):
        self.assertIn(
            "error.message == AstraNativeStartupWait.TIMEOUT_ADVICE", POLICY
        )
        self.assertIn(") return Cause.STARTUP", POLICY)
        self.assertIn('Cause.STARTUP -> "The offline AI engine did not finish', POLICY)
        self.assertIn("cause == Cause.STARTUP", POLICY)
        self.assertNotIn('return error.message', POLICY)

    def test_other_model_download_failure_remains_separately_sanitized(self):
        self.assertIn("AstraDownloadFailureMessage.forOwner(error)", MAIN)
        self.assertNotIn('("Download interrupted: " + error.message)', MAIN)


if __name__ == "__main__":
    unittest.main()
