"""Enforce the owner's 1.7B-only offline conversation policy at build time."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class AdvancedOnlyReasoningContract(unittest.TestCase):
    def test_runtime_never_downgrades_to_compact_weights(self):
        brain = (APP / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
        choice = (APP / "AstraModelChoice.kt").read_text(encoding="utf-8")
        self.assertIn("fun hasLocalModel(): Boolean = hasAdvancedModel()", brain)
        self.assertIn("if (!hasAdvancedModel())", brain)
        self.assertIn("AstraModelImport.ADVANCED_SHA256", brain)
        self.assertIn("loadPreparedModel(modelPath)", brain)
        self.assertNotIn("Variant.COMPACT", brain)
        self.assertNotIn("compact offline fallback", brain)
        self.assertNotIn("Variant.COMPACT", choice)
        self.assertIn("Variant.ADVANCED", choice)

    def test_import_rejects_legacy_gguf_even_when_present(self):
        imported = (APP / "AstraModelImport.kt").read_text(encoding="utf-8")
        self.assertIn("fun appearsInstalled(context: Context): Boolean = advancedInstalled(context)", imported)
        self.assertIn("val target = advancedFile(context)", imported)
        self.assertIn("Only the official verified Qwen3 1.7B", imported)
        self.assertNotIn("hash.equals(AstraOnDeviceBrain.MODEL_SHA256", imported)

    def test_build_pins_advanced_full_without_compact_asset(self):
        prep = read("scripts/prepare_offline_android.py")
        verifier = read("scripts/verify_offline_apk.py")
        gradle = read("android/app/build.gradle.kts")
        expected = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
        self.assertIn("Qwen3-1.7B-Q4_K_M.gguf", prep)
        self.assertIn(expected, prep)
        self.assertIn(expected, verifier)
        self.assertNotIn("QuantFactory/Qwen3-0.6B-GGUF", prep)
        self.assertIn("Obsolete Qwen3 0.6B GGUF must not be bundled", gradle)
        self.assertIn('MODEL = "assets/Qwen3-1.7B-Q4_K_M.gguf"', verifier)
        workflow = read(".github/workflows/android.yml")
        # CI builds FULL first, then LITE in the same workspace. Both
        # GGUF names must be removed before the LITE Gradle invocation.
        lite_section = workflow.split(
            "- name: Build lightweight APK without repackaging Qwen3 weights", 1
        )[1].split("- name: Record LITE APK checksum", 1)[0]
        for asset in ("Qwen3-1.7B-Q4_K_M.gguf", "qwen3-0.6b-q4km.gguf"):
            self.assertIn("rm -f app/src/main/assets/" + asset, lite_section)
        self.assertLess(lite_section.index("rm -f app/src/main/assets/Qwen3-1.7B"),
                        lite_section.index("ASTRA_BUNDLE_MODEL=false"))


    def test_settings_do_not_claim_a_weak_fallback(self):
        main = (APP / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("QWEN3 1.7B REQUIRED", main)
        self.assertNotIn("QWEN3 0.6B AVAILABLE", main)
        self.assertNotIn("The 0.6B model is a recovery fallback", main)


if __name__ == "__main__":
    unittest.main()
