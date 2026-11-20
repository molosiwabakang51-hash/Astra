"""Source gate: completed verified Qwen3 1.7B partials are reused before network."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
DOWNLOAD = (APP / "AstraModelDownload.kt").read_text(encoding="utf-8")
PROMOTION = (APP / "AstraCompletedModelDownload.kt").read_text(encoding="utf-8")


class CompletedModelRecoveryTests(unittest.TestCase):
    def test_promote_verified_saved_bytes_before_https_request(self):
        start = DOWNLOAD.index("suspend fun download(")
        body = DOWNLOAD[start:]
        local = body.index("AstraCompletedModelDownload.promoteIfVerified(")
        network = body.index("URL(MODEL_URL).openConnection()")
        self.assertLess(local, network)
        self.assertIn("if (recoveredBytes != null)", body)
        self.assertLess(body.index("return@withContext recoveredBytes"), network)

    def test_checksum_is_required_before_install(self):
        self.assertIn("checksum(partial).equals(expectedSha256", PROMOTION)
        self.assertIn("AstraModelStorage.installVerified(partial, target)", PROMOTION)
        self.assertLess(
            PROMOTION.index("checksum(partial).equals(expectedSha256"),
            PROMOTION.index("AstraModelStorage.installVerified(partial, target)")
        )

    def test_saved_partial_is_not_deleted_when_incomplete_or_mismatched(self):
        self.assertIn("partial.length() !in minBytes..maxBytes", PROMOTION)
        self.assertIn("return null", PROMOTION)
        self.assertNotIn("partial.delete()", PROMOTION)
        self.assertNotIn("contentResolver", PROMOTION)
        self.assertNotIn("URL(", PROMOTION)


if __name__ == "__main__":
    unittest.main()
