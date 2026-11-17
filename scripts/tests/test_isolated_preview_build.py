"""Guard the isolated Astra Preview test package against unsafe package reuse."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class IsolatedPreviewBuildTest(unittest.TestCase):
    def test_preview_uses_distinct_package_without_changing_original_id(self):
        gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationId = "com.astra.agent"', gradle)
        self.assertRegex(gradle, r'create\("preview"\)\s*\{')
        self.assertIn('applicationIdSuffix = ".preview"', gradle)
        self.assertIn('initWith(getByName("debug"))', gradle)
        self.assertIn('matchingFallbacks += listOf("debug")', gradle)

    def test_preview_launcher_is_differently_labelled(self):
        manifest = (ROOT / "android/app/src/preview/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        self.assertIn('android:label="Astra Preview"', manifest)
        self.assertIn('tools:replace="android:label"', manifest)
        self.assertNotIn("android:sharedUserId", manifest)

    def test_preview_build_refuses_bundled_model_and_old_app_id(self):
        workflow = (ROOT / ".github/workflows/isolated-preview.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("prepare_offline_android.py --lite", workflow)
        self.assertIn("ASTRA_BUNDLE_MODEL: 'false'", workflow)
        self.assertIn(":app:assemblePreview", workflow)
        self.assertIn("verify_lite_apk.py", workflow)
        self.assertIn("com.astra.agent.preview", workflow)
        self.assertIn('"$signer" verify "$apk"', workflow)
        self.assertNotIn("adb uninstall", workflow)
        self.assertNotIn("adb install", workflow)

    def test_existing_main_package_and_owner_release_unchanged(self):
        main_manifest = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        release = (ROOT / ".github/workflows/owner-release.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn('android:label="Astra"', main_manifest)
        self.assertIn("name='com.astra.agent'", release)
        self.assertNotIn("com.astra.agent.preview", release)


if __name__ == "__main__":
    unittest.main()
