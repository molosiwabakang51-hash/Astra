"""Guard against treating a verified GGUF download as working offline inference."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
DOWNLOAD = (APP / "AstraModelDownload.kt").read_text(encoding="utf-8")
IMPORT = (APP / "AstraModelImportCoordinator.kt").read_text(encoding="utf-8")
SELFTEST = (APP / "AstraOfflineSelfTest.kt").read_text(encoding="utf-8")


class DownloadReadyStatusSourceTests(unittest.TestCase):
    def test_download_completion_persists_verified_file_without_claiming_inference(self):
        self.assertIn("AstraModelDownload.download(", MAIN)
        self.assertIn("AstraModelImportCoordinator.verifiedFileMessage(count)", MAIN)
        self.assertIn('updateModelImportStatus(\n', MAIN)
        self.assertIn('"verified"\n', MAIN)
        self.assertNotIn('"QWEN3 1.7B READY', MAIN)

    def test_verified_file_status_and_real_generation_test_remain_separate(self):
        self.assertIn("sha256(target) == AstraModelImport.ADVANCED_SHA256", DOWNLOAD)
        self.assertIn("sha256(partial) != AstraModelImport.ADVANCED_SHA256", DOWNLOAD)
        self.assertIn("QWEN3 1.7B FILE VERIFIED", IMPORT)
        self.assertIn("Run Settings → Test on-device AI", IMPORT)
        self.assertIn("AstraOfflineSelfTest.evaluate(", MAIN)
        self.assertIn("OFFLINE TEST VERIFIED", SELFTEST)

    def test_status_persisted_without_provider_paths_or_private_data(self):
        self.assertIn('putString("astra_model_import_status", message.take(350))', MAIN)
        self.assertIn('putString("astra_model_import_stage", stage)', MAIN)
        self.assertNotIn("updateModelImportStatus(error.message", MAIN)


if __name__ == "__main__":
    unittest.main()
