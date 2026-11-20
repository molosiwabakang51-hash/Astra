"""Every owner-selected text/archive import must bound zero-byte SAF stream reads."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
GUARD = (APP / "AstraProgressInputStream.kt").read_text(encoding="utf-8")
BOOK = (APP / "AstraBookLibrary.kt").read_text(encoding="utf-8")
DOC = (APP / "AstraDocumentText.kt").read_text(encoding="utf-8")
RTF = (APP / "AstraRtfText.kt").read_text(encoding="utf-8")
XLSX = (APP / "AstraSpreadsheetText.kt").read_text(encoding="utf-8")


class DocumentZeroProgressSourceTests(unittest.TestCase):
    def test_zero_progress_stream_has_bounded_owner_safe_error(self):
        self.assertIn("MAX_EMPTY_READS = 16", GUARD)
        self.assertIn("++consecutiveEmptyReads > MAX_EMPTY_READS", GUARD)
        self.assertIn("if (read != 0)", GUARD)
        self.assertIn("consecutiveEmptyReads = 0", GUARD)
        self.assertIn("Copy the document to Downloads", GUARD)
        self.assertNotIn("content://", GUARD)
        self.assertNotIn("http://", GUARD)

    def test_guard_is_applied_before_text_archive_and_pdf_parsers(self):
        self.assertIn("val protectedInput = AstraProgressInputStream(input)", BOOK)
        self.assertIn("epubText(protectedInput)", BOOK)
        self.assertIn("readBounded(protectedInput, MAX_INPUT_BYTES)", BOOK)
        self.assertIn("AstraDocumentText.extract(format, protectedInput", BOOK)
        self.assertIn("val protectedInput = AstraProgressInputStream(input)", DOC)
        self.assertIn("docx(protectedInput)", DOC)
        self.assertIn("officeArchive(protectedInput, \"pptx\")", DOC)
        self.assertIn("AstraPdfText.extract(", DOC)
        self.assertIn("protectedInput\n", DOC)
        self.assertIn("val protectedInput = AstraProgressInputStream(input)", RTF)
        self.assertIn("ZipInputStream(AstraProgressInputStream(input).buffered())", XLSX)

    def test_existing_bounds_and_no_macro_execution_are_preserved(self):
        self.assertIn("MAX_ZIP_ENTRIES = 600", BOOK)
        self.assertIn("MAX_ARCHIVE_ENTRIES = 350", DOC)
        self.assertIn("MAX_BYTES = 16 * 1024 * 1024", XLSX)
        self.assertIn("MAX_BYTES = 2 * 1024 * 1024", RTF)
        self.assertNotIn("exec(", GUARD)
        self.assertNotIn("Runtime.getRuntime", GUARD)


if __name__ == "__main__":
    unittest.main()
