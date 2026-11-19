"""Regression gates for 1.7B Files selection and the Astra core side tray."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
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
        self.assertIn("lifecycleScope.launch {", MAIN)
        self.assertIn("AstraModelImport.importDocument(", MAIN)
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
        self.assertIn("takePersistableUriPermission(", MAIN)
        self.assertIn("releasePersistableUriPermission(", MAIN)
        self.assertIn("modelImportRunning = false", MAIN)

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
