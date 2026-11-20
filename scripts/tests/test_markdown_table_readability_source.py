"""Owner assistant Markdown tables should be readable without changing stored chat."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android/app/src/main/java/com/astra/agent"
MARKUP = (APP / "AstraMessageMarkup.kt").read_text(encoding="utf-8")
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")


class MarkdownTablesSourceTests(unittest.TestCase):
    def test_bounded_table_rendering_requires_complete_valid_structure(self):
        for assertion in (
            "private fun tableCells(", "private fun parseTable(",
            "separators.any { !tableDivider.matches(it) }",
            "rows.size == 24", "if (rows.isEmpty()) return null",
            "val table = if (!inFence) parseTable(lines, index) else null",
            "appendInline(table.headings[column])",
            "appendInline(cell)",
            "table.consumedLines",
        ):
            self.assertIn(assertion, MARKUP)

    def test_user_messages_and_copy_share_speech_remain_original_data(self):
        self.assertIn("AstraMessageMarkup.render(message.text)", MAIN)
        self.assertIn("if (isUser) androidx.compose.ui.text.AnnotatedString(message.text)", MAIN)
        self.assertIn("copyText(message.text)", MAIN)
        self.assertIn("shareText(message.text)", MAIN)
        for forbidden in (
            "HttpURLConnection", "URL(", "contentResolver", "ProcessBuilder",
            "Runtime.getRuntime", "WebView(", "loadUrl(", "exec("
        ):
            self.assertNotIn(forbidden, MARKUP)


if __name__ == "__main__":
    unittest.main()
