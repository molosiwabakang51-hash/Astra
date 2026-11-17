package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Pure JVM safety tests; retained Android URI permissions require a handset. */
class AstraFolderRulesTest {
    @Test fun onlySupportedReadingFilesCanBeIndexed() {
        for (name in listOf(
            "finance.txt", "bond duration.MD", "course.markdown",
            "Russki.epub", "setswana reading.EPUB",
            "finance.csv", "notes.json", "manual.DOCX", "webpage.html",
            "financial_data.xml", "curriculum.tsv", "vocabulary.tsv", "lecture.rtf"
        )) {
            assertTrue(name, AstraFolderRules.bookNameAllowed(name, 1024L))
        }
        for (name in listOf(
            "model.gguf", "private.pdf", "backup.zip", "app.apk",
            "fakes.txt.exe", "no extension", ".txt",
            "../secret.md", "folder\\escape.txt", "bad\u0000title.md", ""
        )) {
            assertFalse(name, AstraFolderRules.bookNameAllowed(name, 1024L))
        }
    }

    @Test fun refusesDeclaredOversizeOrEmptyFilesWithoutReadingThem() {
        val max = 10L * 1024 * 1024
        assertTrue(AstraFolderRules.bookNameAllowed("book.epub", max))
        assertFalse(AstraFolderRules.bookNameAllowed("book.epub", max + 1))
        assertFalse(AstraFolderRules.bookNameAllowed("book.txt", 0))
        assertFalse(AstraFolderRules.bookNameAllowed("book.txt", -1))
        // Some SAF document providers omit sizes. The book importer also
        // enforces its own bounded input limits for such sources.
        assertTrue(AstraFolderRules.bookNameAllowed("book.md", null))
    }

    @Test fun malformedFilenameFuzzNeverBypassesExtensionAndLengthChecks() {
        val random = Random(20261115L)
        val alphabet = "abc123:/\\.\u0000\n\t"
        repeat(12_000) {
            val value = buildString {
                repeat(random.nextInt(220)) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
            val allowed = AstraFolderRules.bookNameAllowed(value, 100L)
            if (allowed) {
                assertTrue(value.length in 5..180)
                assertTrue(value.none { it.isISOControl() || it == '/' || it == '\\' })
                assertTrue(AstraDocumentText.format(value) != null)
            }
        }
    }
}
