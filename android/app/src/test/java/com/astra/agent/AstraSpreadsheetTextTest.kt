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

class AstraSpreadsheetTextTest {
    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((path, content) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private val shared = """
        <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <si><t>Issuer</t></si>
          <si><t>US Treasury 10-year government bond</t></si>
        </sst>""".trimIndent()

    private val worksheet = """
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c>
              <c r="B1" t="inlineStr"><is><t>Modified duration</t></is></c>
              <c r="C1" t="inlineStr"><is><t>Risk note</t></is></c></row>
            <row r="2"><c r="A2" t="s"><v>1</v></c>
              <c r="B2"><v>7.5</v></c>
              <c r="C2" t="inlineStr"><is><t>Interest rate sensitivity</t></is></c>
              <c r="D2"><f>1+1</f><v>2</v></c></row>
          </sheetData>
        </worksheet>""".trimIndent()

    @Test fun importsSheetsAndLateSharedStringsWithoutCalculatingFormulas() {
        // A valid ZIP may place worksheets before sharedStrings.xml.
        val input = archive(
            "xl/worksheets/sheet1.xml" to worksheet,
            "xl/sharedStrings.xml" to shared
        )
        assertEquals("xlsx", AstraDocumentText.format("finance.XLSX"))
        val text = AstraDocumentText.extract("xlsx", ByteArrayInputStream(input))
        assertTrue(text.contains("SHEET 1:"))
        assertTrue(text.contains("A: US Treasury 10-year government bond"))
        assertTrue(text.contains("B: 7.5"))
        assertTrue(text.contains("C: Interest rate sensitivity"))
        assertTrue(text.contains("D: [formula omitted]"))
        assertFalse(text.contains("D: 2"))
        assertTrue(text.contains("dates may be Excel serial numbers"))
    }

    @Test fun importedWorkbookRemainsSearchableInPermanentPrivateLibrary() {
        val directory = Files.createTempDirectory("astra-xlsx-library").toFile()
        val lib = AstraBookLibrary(directory)
        val input = archive(
            "xl/worksheets/sheet1.xml" to worksheet,
            "xl/sharedStrings.xml" to shared
        )
        val book = lib.importBook("finance.xlsx", ByteArrayInputStream(input))
        assertEquals("xlsx", book.format)
        assertTrue(lib.search("Treasury government bond duration").isNotEmpty())
        assertEquals(1, lib.listBooks().size)
        // Simulate a later app launch: indexes remain on the phone's disk
        // and can be searched by a new library instance without reimport.
        val reopened = AstraBookLibrary(directory)
        assertEquals(1, reopened.listBooks().size)
        assertTrue(reopened.search("Treasury government bond duration").isNotEmpty())
    }

    @Test fun rejectsArchiveTraversalAndExternalXmlEntities() {
        val malicious = archive("../xl/worksheets/sheet1.xml" to worksheet)
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("xlsx", ByteArrayInputStream(malicious))
        }
        val dtd = """<!DOCTYPE worksheet [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is>
              <t>&x;</t></is></c></row></sheetData></worksheet>"""
        val injected = archive("xl/worksheets/sheet1.xml" to dtd)
        assertThrows(Exception::class.java) {
            AstraDocumentText.extract("xlsx", ByteArrayInputStream(injected))
        }
    }

    @Test fun legacyXlsAndExecutableExtensionsRemainUnsupported() {
        assertEquals(null, AstraDocumentText.format("finance.xls"))
        assertEquals(null, AstraDocumentText.format("finance.xlsx.exe"))
    }

    @Test fun oversizedUncompressedEntriesFailEvenWhenIgnored() {
        val large = "X".repeat(6 * 1024 * 1024 + 1)
        val bomb = archive(
            "xl/media/unrelated.txt" to large,
            "xl/worksheets/sheet1.xml" to worksheet
        )
        assertThrows(IllegalArgumentException::class.java) {
            AstraDocumentText.extract("xlsx", ByteArrayInputStream(bomb))
        }
    }
}
