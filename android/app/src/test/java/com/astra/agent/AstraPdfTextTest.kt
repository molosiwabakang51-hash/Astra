package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure boundedness tests only. Actual PDFBox parsing, font/glyph assets and
 * selectable text still require an ARM64 Android device smoke test.
 */
class AstraPdfTextTest {
    @Test fun pdfInputMustStaySmallAndNeverAcceptEmptyDocuments() {
        assertFalse(AstraPdfText.sizeAllowed(-1))
        assertFalse(AstraPdfText.sizeAllowed(0))
        assertTrue(AstraPdfText.sizeAllowed(1))
        assertTrue(AstraPdfText.sizeAllowed(4L * 1024 * 1024))
        assertFalse(AstraPdfText.sizeAllowed(4L * 1024 * 1024 + 1))
    }

    @Test fun pageSelectionIsBoundedWithoutOverstatingFullDocumentCoverage() {
        assertEquals(0, AstraPdfText.pagesToRead(-1))
        assertEquals(0, AstraPdfText.pagesToRead(0))
        assertEquals(1, AstraPdfText.pagesToRead(1))
        assertEquals(80, AstraPdfText.pagesToRead(80))
        assertEquals(80, AstraPdfText.pagesToRead(10_000))
    }

    @Test fun outputWriterNeverAllocatesPastDeclaredTextLimit() {
        val writer = AstraPdfText.BoundedTextWriter(10)
        writer.write("abcde", 0, 5)
        writer.write("0123456789".toCharArray(), 2, 5)
        writer.write("DO_NOT_APPEND", 0, 13)
        assertEquals("abcde23456", writer.text())
        assertTrue(writer.truncated)
        writer.flush()
        writer.close()
        assertEquals("abcde23456", writer.text())
    }

    @Test fun zeroCapacityWriterDoesNotLeakSourceText() {
        val writer = AstraPdfText.BoundedTextWriter(0)
        writer.write("private", 0, 7)
        assertTrue(writer.text().isEmpty())
        assertTrue(writer.truncated)
    }
}
