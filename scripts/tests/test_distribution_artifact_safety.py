"""Guard users against confusing disposable CI debug APKs with owner-signed updates."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"


class ArtifactSafetyTest(unittest.TestCase):
    def test_heavy_debug_apk_is_not_advertised_as_an_upgrade(self):
        heavy = (WORKFLOWS / "android.yml").read_text(encoding="utf-8")
        self.assertIn("Astra-v0.12.1-DEBUG-LITE-NOT-FOR-UPGRADES", heavy)
        self.assertIn("Publish CI diagnostic LITE APK (not an update installer)", heavy)
        self.assertIn("CI DEBUG APK — NOT AN IN-PLACE UPDATE", heavy)
        self.assertIn("disposable runner-local debug key", heavy)
        self.assertNotIn("Publish lightweight APK for low-data updates", heavy)

    def test_small_standard_lite_remains_separate_and_not_owner_signed(self):
        standard = (WORKFLOWS / "lite.yml").read_text(encoding="utf-8")
        self.assertIn("Astra-v0.12.2-LITE-manual-model-import", standard)
        self.assertIn("ASTRA_RELEASE_VERSION_CODE: '29'", standard)
        self.assertIn("Signing: disposable CI debug key", standard)
        self.assertIn("DO NOT UNINSTALL", standard)

    def test_protected_owner_release_keeps_same_app_id_and_monotonic_code(self):
        owner = (WORKFLOWS / "owner-release.yml").read_text(encoding="utf-8")
        self.assertIn("environment: astra-release", owner)
        self.assertIn("ASTRA_RELEASE_VERSION_CODE=\"$((100000 + GITHUB_RUN_NUMBER))\"", owner)
        self.assertIn('ASTRA_RELEASE_VERSION_NAME="0.12.2.$GITHUB_RUN_NUMBER"', owner)
        self.assertIn("Astra-v0.12.2-Owner-Signed-LITE", owner)
        self.assertIn("--prerelease", owner)
        self.assertIn("ASTRA_RELEASE_CERT_SHA256", owner)

    def test_readme_directs_owner_to_correct_build_and_preserves_private_model(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn("Astra-v0.12.2-LITE-manual-model-import", readme)
        self.assertIn("Astra-v0.12.1-DEBUG-LITE-NOT-FOR-UPGRADES", readme)
        self.assertIn("same owner signing key", readme)
        self.assertIn("Never uninstall", readme)
        self.assertNotIn("select **`Astra-v0.12.1-LITE-Offline-APK`**", readme)


if __name__ == "__main__":
    unittest.main()
