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

    private fun officeZip(vararg entries: Pair<String, String>): ByteArray {
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
            "manual.xml", "notes.DOCX", "course.epub", "study.md", "handout.RTF",
            "course.pptx", "finance.ODT")) {
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
        val file = officeZip("[Content_Types].xml" to "<Types/>", "word/document.xml" to xml)
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
                officeZip("../document.xml" to "<w:p>bad</w:p>")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("docx", ByteArrayInputStream(
                officeZip("[Content_Types].xml" to "<Types/>")))
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

    @Test fun pptxExtractsSlideTextInPresentationNumberOrderAndIndexesLocally() {
        val contentTypes = """<?xml version="1.0"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Override PartName="/ppt/presentation.xml"
                ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
            </Types>"""
        val intro = """<?xml version="1.0"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <a:p><a:r><a:t>Bond duration describes interest-rate risk in portfolios.</a:t></a:r></a:p>
              <a:p><a:r><a:t>Modified duration measures first-order sensitivity.</a:t></a:r></a:p>
            </p:sld>"""
        val second = """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <a:p><a:r><a:t>Convexity measures curvature in bond prices.</a:t></a:r></a:p>
              <a:p><a:r><a:t>Higher convexity can reduce large price-estimation errors.</a:t></a:r></a:p>
            </p:sld>"""
        val presentation = officeZip(
            "[Content_Types].xml" to contentTypes,
            "ppt/presentation.xml" to "<p:presentation/>",
            "ppt/slides/slide2.xml" to second,
            "ppt/slides/slide1.xml" to intro,
            "ppt/notesSlides/notesSlide1.xml" to
                "<a:t>PRIVATE SPEAKER NOTES THAT ARE NOT INDEXED</a:t>"
        )
        val plain = AstraDocumentText.extract("pptx", ByteArrayInputStream(presentation))
        assertTrue(plain.indexOf("Slide 1:") < plain.indexOf("Slide 2:"))
        assertTrue(plain.contains("Modified duration measures first-order sensitivity"))
        assertTrue(plain.contains("Convexity measures curvature"))
        assertFalse(plain.contains("PRIVATE SPEAKER NOTES"))
        val library = library()
        val imported = library.importBook("finance.pptx", ByteArrayInputStream(presentation))
        assertEquals("pptx", imported.format)
        assertTrue(library.search("modified duration interest-rate sensitivity").isNotEmpty())
    }

    @Test fun odtIndexesBodyHeadingsParagraphsAndTablesWithoutMetadata() {
        val odt = """<?xml version="1.0" encoding="UTF-8"?>
            <office:document-content
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
              <office:meta><text:p>PRIVATE METADATA MUST NOT BE INDEXED</text:p></office:meta>
              <office:body><office:text>
                <text:h>Russian grammar and cases</text:h>
                <text:p>The genitive case indicates possession and negation in Russian.</text:p>
                <text:p>Case endings differ with noun gender and declension.
                  <text:span>Vocabulary practice</text:span> improves recognition.
                  <text:tab/>Studying daily helps recall.</text:p>
              </office:text></office:body>
            </office:document-content>"""
        val file = officeZip(
            "mimetype" to "application/vnd.oasis.opendocument.text",
            "content.xml" to odt,
            "meta.xml" to "<meta>PRIVATE DOCUMENT TITLE</meta>"
        )
        val extracted = AstraDocumentText.extract("odt", ByteArrayInputStream(file))
        assertTrue(extracted.contains("Russian grammar and cases"))
        assertTrue(extracted.contains("genitive case indicates possession"))
        assertTrue(extracted.contains("Vocabulary practice"))
        assertFalse(extracted.contains("PRIVATE METADATA"))
        assertFalse(extracted.contains("<text:p>"))
        val book = library()
        assertEquals("odt", book.importBook("russian.odt", ByteArrayInputStream(file)).format)
        assertTrue(book.search("Russian genitive case possession").isNotEmpty())
    }

    @Test fun officeArchiveValidatesFormatRejectsTraversalAndDisablesExternalEntities() {
        val slide = """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                  <a:p><a:r><a:t>Normal presentation text</a:t></a:r></a:p></p:sld>"""
        val types = "<Types ContentType=" +
            "\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>"
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("pptx", ByteArrayInputStream(
                officeZip("ppt/slides/slide1.xml" to slide)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("pptx", ByteArrayInputStream(
                officeZip("../ppt/slides/slide1.xml" to slide)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("odt", ByteArrayInputStream(
                officeZip("mimetype" to "application/epub+zip",
                    "content.xml" to "<office:text>not an ODT</office:text>")))
        }
        val xxe = """<!DOCTYPE p:sld [<!ENTITY stolen SYSTEM "file:///etc/passwd">]>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <a:p><a:t>&stolen;</a:t></a:p>
            </p:sld>"""
        assertThrows(Exception::class.java) {
            AstraDocumentText.extract("pptx", ByteArrayInputStream(officeZip(
                "[Content_Types].xml" to types,
                "ppt/presentation.xml" to "<p:presentation/>",
                "ppt/slides/slide1.xml" to xxe
            )))
        }
    }

    @Test fun officeArchivesCannotExceedBoundedEntryCountOrInflationBudget() {
        val many = Array(351) { n -> "unused/resource$n.bin" to "x" }
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("odt", ByteArrayInputStream(officeZip(*many)))
        }
        val oversized = "A".repeat(8 * 1024 * 1024 + 1)
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("odt", ByteArrayInputStream(officeZip(
                "mimetype" to "application/vnd.oasis.opendocument.text",
                "content.xml" to oversized
            )))
        }
    }


    @Test fun veryLongSlideAndLaterSlideNeverExceedOutputBudget() {
        val types = "<Types ContentType=" +
            "\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>"
        val open = """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                   <a:p><a:t>"""
        val close = "</a:t></a:p></p:sld>"
        val file = officeZip(
            "[Content_Types].xml" to types,
            "ppt/presentation.xml" to "<presentation/>",
            "ppt/slides/slide1.xml" to (open + "A".repeat(900_000) + close),
            "ppt/slides/slide2.xml" to (open + "B".repeat(1_000) + close)
        )
        val text = AstraDocumentText.extract("pptx", ByteArrayInputStream(file))
        assertTrue(text.length <= 900_000)
        assertTrue(text.startsWith("Slide 1:"))
        assertFalse(text.contains("Slide 2:"))
    }


}
