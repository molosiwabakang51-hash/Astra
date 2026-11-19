"""Regression contract: Files import survives Astra secure-workspace disposal.

The on-phone Android picker and the app's biometric lock both start/stop
activities; Compose-only registration loses the selected document result
when the private subtree is removed. Keep the importer owned by Activity.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
TRAY = (APP / "AstraNavigationTray.kt").read_text(encoding="utf-8")


class ModelPickerLifecycleContract(unittest.TestCase):
    def test_picker_is_activity_owned_and_independent_of_private_composition(self):
        activity = MAIN.index("class MainActivity :")
        callback = MAIN.index("private val importModelDocument =")
        composition = MAIN.index("setContent {", activity)
        self.assertLess(activity, callback)
        self.assertLess(callback, composition)
        self.assertIn(
            "registerForActivityResult(ActivityResultContracts.OpenDocument())",
            MAIN[callback:composition],
        )
        self.assertNotIn("val importModelLauncher =", MAIN)
        self.assertNotIn("rememberLauncherForActivityResult", MAIN[callback:composition])
        self.assertIn("lifecycleScope.launch", MAIN[callback:composition])
        self.assertIn("AstraModelImport.importDocument(", MAIN[callback:composition])
        self.assertIn("finally {\n                            modelImportRunning = false", MAIN)

    def test_files_picker_return_preserves_settings_and_visible_failure(self):
        callback = MAIN.index("private val importModelDocument =")
        composition = MAIN.index("setContent {", callback)
        code = MAIN[callback:composition]
        self.assertIn("tab = 4", code)
        self.assertIn("MODEL IMPORT FAILED:", code)
        self.assertIn("modelImportStatus = ", code)
        self.assertIn("Toast.makeText(", code)
        self.assertIn("private var tab by mutableIntStateOf(0)", MAIN)
        self.assertIn('putInt("astra_selected_tab", tab)', MAIN)
        self.assertIn('savedInstanceState?.getInt("astra_selected_tab", 0)', MAIN)
        self.assertNotIn("var tab by remember", MAIN)
        self.assertNotIn("var modelImportRunning by remember", MAIN)
        self.assertNotIn("var modelImportStatus by remember", MAIN)
        self.assertIn("importModelDocument.launch(arrayOf(\"*/*\"))", MAIN)
        self.assertIn("if (modelImportStatus.isNotBlank())", MAIN)

    def test_brand_icon_opens_real_modal_edge_navigation_not_dropdown(self):
        self.assertIn("ModalNavigationDrawer(", MAIN)
        self.assertIn("drawerState.open()", MAIN)
        self.assertIn("drawerState.close()", MAIN)
        self.assertIn("AstraNavigationTray(", MAIN)
        self.assertIn("AstraTrayAction.CLEAR_CHAT -> showClearDialog = true", MAIN)
        self.assertNotIn("navigationPopupOpen", MAIN)
        self.assertNotIn("expanded = navigationPopupOpen", MAIN)
        self.assertIn("ModalDrawerSheet(", TRAY)
        self.assertIn("RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp)", TRAY)
        self.assertIn("verticalScroll(rememberScrollState())", TRAY)
        self.assertIn("heightIn(min = 64.dp)", TRAY)
        self.assertIn("heightIn(min = 49.dp)", TRAY)
        self.assertIn("contentDescription =", TRAY)
        for destination in ("CHAT", "TASKS", "NOTES", "TOOLS", "SETTINGS", "STUDY"):
            self.assertIn("AstraNavSection." + destination, TRAY)


if __name__ == "__main__":
    unittest.main()
