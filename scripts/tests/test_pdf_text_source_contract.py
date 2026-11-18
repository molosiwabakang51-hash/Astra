"""Source review guards for optional, owner-initiated PDF text parsing.

A real Android phone is required to verify PDFBox native resources/glyphs.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
PDF = (JAVA / "AstraPdfText.kt").read_text(encoding="utf-8")
DOC = (JAVA / "AstraDocumentText.kt").read_text(encoding="utf-8")
BOOKS = (JAVA / "AstraBookLibrary.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
GRADLE = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
PREPARE = (ROOT / "scripts/prepare_offline_android.py").read_text(encoding="utf-8")
VERIFY_LITE = (ROOT / "scripts/verify_lite_apk.py").read_text(encoding="utf-8")
VERIFY_FULL = (ROOT / "scripts/verify_offline_apk.py").read_text(encoding="utf-8")


class OfflinePdfSourceContractTest(unittest.TestCase):
    def test_android_pdfbox_is_pinned_and_initialized_locally(self):
        self.assertIn('implementation("com.tom-roush:pdfbox-android:2.0.27.0")', GRADLE)
        self.assertIn("PDFBoxResourceLoader.init(context.applicationContext)", PDF)
        self.assertIn("PDDocument.load(bytes).use", PDF)
        self.assertIn("PDFTextStripper()", PDF)
        self.assertIn('"pdf" to "pdf"', DOC)
        self.assertIn('"pdf" -> AstraPdfText.extract(', DOC)
        self.assertIn("pdfContext = applicationContext", MAIN)
        self.assertIn("AstraDocumentText.extract(format, input, pdfContext)", BOOKS)

    def test_pdf_input_pages_and_output_are_bounded_and_mark_truncation(self):
        self.assertIn("MAX_PDF_BYTES = 4 * 1024 * 1024", PDF)
        self.assertIn("MAX_PAGES = 80", PDF)
        self.assertIn("MAX_CHARS = 900_000", PDF)
        self.assertIn("BoundedTextWriter", PDF)
        self.assertIn("if (writer.truncated)", PDF)
        self.assertIn("[PDF index is partial", PDF)
        self.assertIn("No selectable text found in this PDF", PDF)
        self.assertIn("Password-protected or encrypted PDFs are unsupported", PDF)
        for forbidden in ("HttpURLConnection", "http://", "https://", "ProcessBuilder", "Runtime.getRuntime"):
            self.assertNotIn(forbidden, PDF)

    def test_apache_notice_will_be_present_in_all_distributed_variants(self):
        marker = "PdfBox-Android-Apache-2.0.txt"
        self.assertIn(marker, PREPARE)
        self.assertIn(marker, VERIFY_LITE)
        self.assertIn(marker, VERIFY_FULL)
        self.assertIn("PdfBox-Android-NOTICE.txt", VERIFY_LITE)
        self.assertIn("PdfBox-Android-NOTICE.txt", VERIFY_FULL)
        notice = (ROOT / "android/app/src/main/assets/open_source_licenses/PdfBox-Android-NOTICE.txt").read_text(encoding="utf-8")
        self.assertIn("Apache PDFBox", notice)
        self.assertIn("Adobe Glyph List", notice)
        self.assertIn("PDFBox-Android 2.0.27.0", PREPARE)
        self.assertIn("LITE APK", (ROOT / "README.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
