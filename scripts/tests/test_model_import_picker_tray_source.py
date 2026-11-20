"""Regression gates for 1.7B Files selection and the Astra core side tray."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
COORD = (APP / "AstraModelImportCoordinator.kt").read_text(encoding="utf-8")
IMPORT = (APP / "AstraModelImport.kt").read_text(encoding="utf-8")
BRAIN = (APP / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
TRAY = (APP / "AstraNavigationTray.kt").read_text(encoding="utf-8")


class ModelPickerRecoveryTests(unittest.TestCase):
    def test_model_picker_is_registered_on_activity_not_disposable_chat_ui(self):
        self.assertIn(
            "private val importModelLauncher = registerForActivityResult(",
            MAIN
        )
        self.assertIn("ActivityResultContracts.OpenDocument()", MAIN)
        self.assertNotIn(
            "val importModelLauncher =\n"
            "                    rememberLauncherForActivityResult(",
            MAIN
        )
        self.assertLess(
            MAIN.index("private val importModelLauncher ="),
            MAIN.index("override fun onCreate(")
        )
        # Only the observer is Activity-bound. The large copy survives an
        # Activity recreation in an app-process scope with single-flight gating.
        self.assertIn("lifecycleScope.launch {", MAIN)
        self.assertIn("AstraModelImportCoordinator.start(", MAIN)
        self.assertIn("AstraModelImportCoordinator.snapshots.collect", MAIN)
        self.assertIn("private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)", COORD)
        self.assertIn("AstraModelImport.importDocument(", COORD)
        self.assertIn("if (!gate.acquire()) return false", COORD)
        self.assertNotIn("AstraModelImport.importDocument(", MAIN)
        self.assertIn("selectOfflineModelFromFiles()", MAIN)

    def test_return_from_files_restores_setup_and_status_even_after_lock(self):
        self.assertIn("var tab by rememberSaveable", MAIN)
        self.assertIn(
            "LaunchedEffect(modelSetupRequested, workspaceUnlocked)", MAIN
        )
        self.assertIn("tab = 4", MAIN)
        self.assertIn('"astra_model_import_stage"', MAIN)
        self.assertIn('"astra_model_import_status"', MAIN)
        self.assertIn('previousStage == "copying"', MAIN)
        self.assertIn('previousStage == "selecting"', MAIN)
        self.assertIn("if (modelImportStatus.isNotBlank()) {", MAIN)
        self.assertIn('"Model setup"', MAIN)
        self.assertIn("MODEL IMPORT FAILED:", MAIN)
        self.assertIn("takePersistableUriPermission(", COORD)
        self.assertIn("releasePersistableUriPermission(", COORD)
        self.assertIn("gate.release()", COORD)
        self.assertIn("modelImportRunning = snapshot.running", MAIN)
        self.assertIn("modelImportRunning = activeImport", MAIN)

    def test_abandoned_17b_temp_files_are_removed_before_storage_checks(self):
        # A killed process may leave ~1.2 GB of stale .importing/.partial
        # bytes. Capacity checks before cleanup would make retry impossible.
        import_fn = IMPORT[IMPORT.index("suspend fun importDocument("):]
        self.assertLess(
            import_fn.index("discardInterruptedCopy(partial)"),
            import_fn.index("require(hasImportStartingSpace(")
        )
        full_asset_fn = BRAIN[BRAIN.index("private suspend fun ensureModel("):]
        self.assertLess(
            full_asset_fn.index("AstraModelImport.discardInterruptedCopy(temporary)"),
            full_asset_fn.index("require(StatFs(directory.absolutePath).availableBytes")
        )
        self.assertIn("if (partial.exists())", IMPORT)

    def test_owner_can_cancel_process_wide_import_without_orphaning_saf_grant(self):
        self.assertIn("fun cancel(): Boolean", COORD)
        self.assertIn("ownerRequestedCancel = true", COORD)
        self.assertIn("job.cancel(CancellationException(", COORD)
        self.assertIn("job.invokeOnCompletion { outcome ->", COORD)
        self.assertIn("releasePersistableUriPermission(", COORD)
        self.assertIn("gate.release()", COORD)
        self.assertIn("AstraModelImportCoordinator.cancel()", MAIN)
        self.assertIn('Text("Cancel model import")', MAIN)

    def test_security_remains_a_gate_after_picker(self):
        self.assertIn(
            "AstraAppLockPolicy.requiresUnlock(", MAIN
        )
        self.assertIn("return@MaterialTheme", MAIN)
        self.assertIn("(!secureLockEnabled || workspaceUnlocked)", MAIN)
        self.assertIn("private fun lockPrivateWorkspace()", MAIN)

    def test_navigation_is_left_side_sheet_with_all_legacy_controls(self):
        self.assertIn("AstraNavigationTray(", MAIN)
        self.assertIn("Dialog(", TRAY)
        self.assertIn("Alignment.CenterStart", TRAY)
        self.assertIn("RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)", TRAY)
        self.assertIn("AstraNavigationGlyph(", TRAY)
        self.assertIn("Modifier.size(48.dp)", TRAY)  # close affordance
        self.assertIn("onNavigate(index)", TRAY)
        self.assertIn("AstraTrayAction.CLEAR_CHAT", MAIN)
        self.assertIn("showClearDialog = true", MAIN)
        self.assertIn("AstraTrayAction.COPY_CHAT", MAIN)
        self.assertIn("AstraTrayAction.SHARE_CHAT", MAIN)
        self.assertIn("AstraTrayAction.MEMORY_SEARCH", MAIN)
        self.assertIn("AstraTrayAction.NEW_CONVERSATION", MAIN)
        self.assertNotIn("expanded = navigationPopupOpen,\n"
                         "                                    onDismissRequest", MAIN)


if __name__ == "__main__":
    unittest.main()
