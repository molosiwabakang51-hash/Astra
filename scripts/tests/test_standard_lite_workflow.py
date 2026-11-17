"""Keep Astra's primary distribution LITE-only, without a Preview app."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
LITE = ROOT / ".github/workflows/lite.yml"
APP = ROOT / "android/app/build.gradle.kts"


class StandardLiteWorkflowTest(unittest.TestCase):
    def test_no_preview_build_type_or_workflow(self):
        source = APP.read_text(encoding="utf-8")
        self.assertIn('applicationId = "com.astra.agent"', source)
        self.assertNotIn('create("preview")', source)
        self.assertNotIn('applicationIdSuffix', source)
        self.assertFalse((ROOT / ".github/workflows/isolated-preview.yml").exists())
        self.assertFalse((ROOT / "android/app/src/preview/AndroidManifest.xml").exists())

    def test_small_manual_model_build_is_separate_and_downloadable(self):
        content = LITE.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", content)
        self.assertIn("prepare_offline_android.py --lite", content)
        self.assertIn("ASTRA_BUNDLE_MODEL: 'false'", content)
        self.assertIn("verify_lite_apk.py", content)
        self.assertIn("actions/upload-artifact@v4", content)
        self.assertIn("Astra-v0.12.2-LITE-manual-model-import", content)
        self.assertNotIn("assemblePreview", content)
        self.assertNotIn("com.astra.agent.preview", content)
        self.assertNotIn("adb uninstall", content)

    def test_main_branch_builds_small_lite_without_large_model_download(self):
        lite = LITE.read_text(encoding="utf-8")
        full = (ROOT / ".github/workflows/android.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("  push:\n    branches: [main]", lite)
        self.assertIn("  workflow_dispatch:", lite)
        self.assertIn("prepare_offline_android.py --lite", lite)
        self.assertNotIn("prepare_offline_android.py\n", lite)
        self.assertIn("testDebugUnitTest", lite)
        self.assertNotIn("  push:\n    branches: [main]", full)
        self.assertIn("  pull_request:\n    branches: [main]", full)
        self.assertIn("  schedule:\n", full)
        self.assertIn("  workflow_dispatch:", full)
        self.assertIn("prepare_offline_android.py\n", full)
        self.assertIn("verify_offline_apk.py", full)

    def test_version_is_bumped_from_existing_install(self):
        content = LITE.read_text(encoding="utf-8")
        self.assertIn("ASTRA_RELEASE_VERSION_CODE: '29'", content)
        self.assertIn("ASTRA_RELEASE_VERSION_NAME: '0.12.2'", content)
        self.assertIn("versionCode='29'", content)
        self.assertIn("versionName='0.12.2'", content)

    def test_signing_mismatch_warning_is_explicit(self):
        source = LITE.read_text(encoding="utf-8")
        self.assertIn("apksigner", source)
        self.assertIn("disposable CI debug key", source)
        self.assertIn("INSTALL_FAILED_UPDATE_INCOMPATIBLE", source)
        self.assertIn("DO NOT UNINSTALL", source)


if __name__ == "__main__":
    unittest.main()
