"""Static contract: owner-selected PPTX/ODT imports remain offline and bounded.

The JVM fixture suite performs the actual XML/ZIP extraction tests.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/com/astra/agent"
EXTRACT = (JAVA / "AstraDocumentText.kt").read_text(encoding="utf-8")
BOOKS = (JAVA / "AstraBookLibrary.kt").read_text(encoding="utf-8")
FOLDER = (JAVA / "AstraFolderAccess.kt").read_text(encoding="utf-8")
DOCS = (ROOT / "README.md").read_text(encoding="utf-8")


class OfficeDocumentSourceContract(unittest.TestCase):
    def test_formats_enter_actual_local_book_import_pipeline(self):
        for extension in ("pptx", "odt"):
            self.assertIn(f'"{extension}" to "{extension}"', EXTRACT)
            self.assertIn(f'"{extension}" -> officeArchive(input, "{extension}")', EXTRACT)
        self.assertIn("AstraDocumentText.format(title)", BOOKS)
        self.assertIn("AstraDocumentText.extract(format, input, pdfContext)", BOOKS)
        self.assertIn("val format = AstraDocumentText.format(filename) ?: return false", FOLDER)
        self.assertIn('if (format == "pdf" && size != null &&', FOLDER)
        self.assertIn("AstraPdfText.sizeAllowed(size)", FOLDER)

    def test_archive_and_xml_are_bounded_and_do_not_execute_objects(self):
        for gate in (
            "MAX_ARCHIVE_ENTRIES = 350",
            "MAX_ARCHIVE_SCAN = 20 * 1024 * 1024",
            "MAX_OFFICE_ENTRY = 8 * 1024 * 1024",
            "MAX_CHARS = 900_000",
            "readBounded(archive, minOf(MAX_OFFICE_ENTRY, remaining))",
            'disallow-doctype-decl',
            'external-general-entities',
            'external-parameter-entities',
            'name.replace',
            'path.split',
        ):
            self.assertIn(gate, EXTRACT)
        for forbidden in ("Runtime.getRuntime", "ProcessBuilder", "URL(", "HttpURLConnection"):
            self.assertNotIn(forbidden, EXTRACT)

    def test_only_readable_content_is_claimed(self):
        self.assertIn('kind == "pptx" && slidePath.matches(path)', EXTRACT)
        self.assertIn('kind == "odt" && path == "content.xml"', EXTRACT)
        self.assertIn("presentationml.presentation.main+xml", EXTRACT)
        self.assertIn("application/vnd.oasis.opendocument.text", EXTRACT)
        self.assertIn("PPTX and ODT limitations:", DOCS)
        self.assertIn("They do not read image-only slides", DOCS)
        self.assertIn("PDFs", DOCS)


if __name__ == "__main__":
    unittest.main()
