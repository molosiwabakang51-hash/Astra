package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AstraDocumentTextTest {
    private fun bytes(content: String) = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
    private fun library() = AstraBookLibrary(Files.createTempDirectory("astra-document-test").toFile())

    private fun docx(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test fun supportedFormatsUseRealDocumentExtensions() {
        for (name in listOf("lecture.HTML", "table.csv", "book.tsv", "study.json",
            "manual.xml", "notes.DOCX", "course.epub", "study.md")) {
            assertTrue(name, AstraDocumentText.format(name) != null)
        }
        for (name in listOf("bad.pdf", "photo.jpg", "audio.mp3",
            "document.doc", "script.exe", ".docx", "file.docx.exe")) {
            assertEquals(name, null, AstraDocumentText.format(name))
        }
    }

    @Test fun htmlExcludesScriptsAndProducesSearchablePassages() {
        val lib = library()
        val html = "<html><body><h1>Fixed income</h1><p>" +
            "Bond duration measures interest-rate sensitivity. ".repeat(8) +
            "</p><script>stealPrivateHistory()</script></body></html>"
        val book = lib.importBook("finance.html", bytes(html))
        assertEquals("html", book.format)
        val passages = lib.search("bond duration sensitivity")
        assertTrue(passages.isNotEmpty())
        assertFalse(passages.first().text.contains("stealPrivateHistory"))
        assertFalse(passages.first().text.contains("<p>"))
    }

    @Test fun csvPreservesHeadersQuotedCommasAndFinancialValues() {
        val lib = library()
        val csv = "Asset,Duration,Yield,Description\n" +
            "\"US Treasury, 10Y\",8.2,4.1%,\"Bond exposure, interest-rate risk\"\n" +
            "UK Gilts,6.5,3.6%,Fixed income duration and market prices\n" +
            "UK Gilts,6.4,3.8%,Fixed income duration and coupon sensitivity\n"
        val book = lib.importBook("portfolio.csv", bytes(csv))
        assertEquals("csv", book.format)
        val results = lib.search("Treasury duration yield")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().text.contains("Duration: 8.2"))
        assertTrue(results.first().text.contains("US Treasury, 10Y"))
    }

    @Test fun tsvProvidesSearchableStructuredRows() {
        val tsv = "Concept\tDefinition\n" +
            "Convexity\tBond price curvature with changing yields\n" +
            "Duration\tFirst-order price sensitivity to interest rate moves\n"
        val extracted = AstraDocumentText.extract("tsv", bytes(tsv))
        assertTrue(extracted.contains("Concept: Convexity"))
        assertTrue(extracted.contains("Definition: Bond price curvature"))
    }

    @Test fun jsonIndexesNestedOwnerDataAsUntrustedText() {
        val lib = library()
        val json = """{"portfolio":{"bonds":[{"issuer":"US Treasury",
            "duration":8.4,"notes":"US government bond duration risk"},
            {"issuer":"Botswana Government","duration":6.3}]}}"""
        val book = lib.importBook("portfolio.json", bytes(json))
        assertEquals("json", book.format)
        assertTrue(lib.search("government bond duration").isNotEmpty())
    }

    @Test fun rejectsDeepOrMalformedJsonBeforeExpensiveParsing() {
        val deeplyNested = "[".repeat(30) + "0" + "]".repeat(30)
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("json", bytes(deeplyNested))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("json", bytes("{\"hello\":"))
        }
    }

    @Test fun xmlExtractsPlainTextAndRejectsExternalEntities() {
        val xml = "<?xml version=\"1.0\"?><curriculum><unit>" +
            "Currency exchange rate volatility and investment returns are linked." +
            "</unit><unit>Hedging can mitigate foreign currency exchange risk.</unit></curriculum>"
        val extracted = AstraDocumentText.extract("xml", bytes(xml))
        assertTrue(extracted.contains("Currency exchange rate volatility"))
        assertTrue(extracted.contains("Hedging can mitigate"))
        val hostile = "<!DOCTYPE data [<!ENTITY ex SYSTEM \"file:///etc/passwd\">]>" +
            "<data>&ex;</data>"
        assertThrows(Exception::class.java) {
            AstraDocumentText.extract("xml", bytes(hostile))
        }
    }

    @Test fun docxExtractsRealWordTextWithParagraphBoundaries() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>Bond pricing depends on cash flows and yields.</w:t></w:r></w:p>
              <w:p><w:r><w:t>Modified duration measures yield sensitivity.</w:t></w:r>
              <w:r><w:t> Government securities have varying maturities.</w:t></w:r></w:p>
              </w:body></w:document>"""
        val file = docx("[Content_Types].xml" to "<Types/>", "word/document.xml" to xml)
        val lib = library()
        val book = lib.importBook("bonds.docx", ByteArrayInputStream(file))
        assertEquals("docx", book.format)
        assertTrue(lib.search("modified duration yield sensitivity").isNotEmpty())
        val extracted = AstraDocumentText.extract("docx", ByteArrayInputStream(file))
        assertTrue(extracted.contains("Bond pricing depends"))
        assertTrue(extracted.contains("Modified duration measures"))
    }

    @Test fun refusesDocxTraversalAndMissingBody() {
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("docx", ByteArrayInputStream(
                docx("../document.xml" to "<w:p>bad</w:p>")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("docx", ByteArrayInputStream(
                docx("[Content_Types].xml" to "<Types/>")))
        }
    }

    @Test fun malformedHtmlAndJsonNeverRunExecutableContents() {
        val html = "<div>Bond yields</div><script>throw new Error('run')</script>" +
            "<p>Duration hedge</p>"
        val text = AstraDocumentText.extract("html", bytes(html))
        assertFalse(text.contains("throw new Error"))
        assertTrue(text.contains("Duration hedge"))
        val json = """{"task":"=HYPERLINK(\"bad\")","risk":"Currency conversion risk"}"""
        assertTrue(AstraDocumentText.extract("json", bytes(json)).contains("Currency conversion risk"))
    }
}
