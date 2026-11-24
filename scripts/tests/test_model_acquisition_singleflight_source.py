"""Prevent 2 simultaneous Qwen3 1.7B imports/downloads exhausting Android storage."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"


class ModelAcquisitionSingleFlightSource(unittest.TestCase):
    def test_both_acquisition_paths_use_exact_same_process_gate(self):
        gate = (JAVA / "AstraModelAcquisitionGate.kt").read_text()
        importer = (JAVA / "AstraModelImport.kt").read_text()
        downloader = (JAVA / "AstraModelDownload.kt").read_text()
        self.assertIn("private val active = Mutex()", gate)
        self.assertIn("check(active.tryLock())", gate)
        self.assertIn("active.unlock()", gate)
        self.assertIn("finally {", gate)
        self.assertIn("): Long = AstraModelAcquisitionGate.singleFlight {", importer)
        self.assertIn("): Long = AstraModelAcquisitionGate.singleFlight {", downloader)
        self.assertIn("AstraModelOperationGate.withImport", importer)
        self.assertIn("AstraModelOperationGate.withDownload", downloader)

    def test_stage_copy_still_avoids_locking_native_generation(self):
        importer = (JAVA / "AstraModelImport.kt").read_text()
        downloader = (JAVA / "AstraModelDownload.kt").read_text()
        for source in (importer, downloader):
            self.assertIn("AstraModelOperationGate.withPromotion {", source)
            self.assertNotIn("AstraModelOperationGate.withNativeSession", source)
        gate = (JAVA / "AstraModelAcquisitionGate.kt").read_text()
        self.assertNotIn("AstraModelOperationGate.withNativeSession", gate)

    def test_colliding_acquisition_error_is_private_and_actionable(self):
        gate = (JAVA / "AstraModelAcquisitionGate.kt").read_text()
        coordinator = (JAVA / "AstraModelImportCoordinator.kt").read_text()
        self.assertIn("import or download is already running", gate)
        self.assertIn("import or download is already running", coordinator)
        self.assertIn("Finish or cancel it in Settings.", coordinator)
        self.assertNotIn("selectedUri", gate)
        self.assertNotIn("absolutePath", gate)
        self.assertNotIn("ContentResolver", gate)


if __name__ == "__main__":
    unittest.main()
