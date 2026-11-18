"""Owner LITE distribution contract: APK <=300,000,000 bytes, no GGUF.

Uses tiny synthetic APKs; never downloads or allocates a 300MB model.
"""
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "scripts/verify_lite_apk.py"
spec = importlib.util.spec_from_file_location("astra_lite_verifier", SOURCE)
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class LiteSizeCapTests(unittest.TestCase):
    def test_decimal_300mb_cap_inclusive_exact_boundary(self):
        self.assertEqual(300_000_000, verifier.MAX_APK_SIZE_BYTES)
        verifier.check_lite_size(1)
        verifier.check_lite_size(299_999_999)
        verifier.check_lite_size(300_000_000)
        with self.assertRaisesRegex(RuntimeError, "exceeds the 300 MB maximum"):
            verifier.check_lite_size(300_000_001)
        with self.assertRaisesRegex(RuntimeError, "empty"):
            verifier.check_lite_size(0)
        with self.assertRaisesRegex(RuntimeError, "empty"):
            verifier.check_lite_size(-1)

    @staticmethod
    def fake_lite(path: Path, model: str | None = None, license_missing=False):
        with ZipFile(path, "w") as apk:
            apk.writestr("lib/arm64-v8a/libai-chat.so", "fake-test-only")
            apk.writestr("assets/open_source_licenses/Qwen3-Apache-2.0.txt", "notice")
            apk.writestr("assets/open_source_licenses/PdfBox-Android-Apache-2.0.txt", "notice")
            if not license_missing:
                apk.writestr("assets/open_source_licenses/llama.cpp-MIT.txt", "notice")
            if model:
                apk.writestr(model, "not-real-GGUF")

    def test_small_model_free_apk_passes(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "astra-lite.apk"
            self.fake_lite(path)
            verifier.main(path)

    def test_any_gguf_is_forbidden_even_unexpected_name_or_case(self):
        with TemporaryDirectory() as directory:
            for model in ("assets/qwen3-0.6b-q4km.gguf",
                          "assets/models/other-model.GGUF",
                          "models/custom.gguf"):
                path = Path(directory) / "astra-lite.apk"
                self.fake_lite(path, model)
                with self.assertRaisesRegex(RuntimeError, "must not bundle any GGUF"):
                    verifier.main(path)

    def test_missing_runtime_or_license_fails(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "astra-lite.apk"
            with ZipFile(path, "w") as apk:
                apk.writestr("assets/open_source_licenses/Qwen3-Apache-2.0.txt", "x")
                apk.writestr("assets/open_source_licenses/llama.cpp-MIT.txt", "x")
            with self.assertRaisesRegex(RuntimeError, "missing the Android llama.cpp engine"):
                verifier.main(path)
            self.fake_lite(path, license_missing=True)
            with self.assertRaisesRegex(RuntimeError, "missing license notices"):
                verifier.main(path)

    def test_every_lite_publication_checks_exact_staged_apk(self):
        checks = {
            ".github/workflows/lite.yml":
                "python3 scripts/verify_lite_apk.py dist/Astra-v0.12.2-LITE-offline-arm64.apk",
            ".github/workflows/owner-release.yml":
                "python3 scripts/verify_lite_apk.py dist/Astra-v0.12.2-LITE-owner-signed.apk",
            ".github/workflows/android.yml":
                "python3 ../scripts/verify_lite_apk.py ../dist/Astra-v0.12.1-LITE-offline-arm64-debug.apk",
        }
        for path, command in checks.items():
            workflow = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn(command, workflow, path)

    def test_release_docs_agree_on_decimal_units(self):
        for path in ("README.md", "docs/stable-release-acceptance.md"):
            doc = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn("300,000,000 bytes", doc, path)
            self.assertIn("300 MB", doc, path)
            self.assertNotIn("<150 MiB", doc, path)


if __name__ == "__main__":
    unittest.main()
