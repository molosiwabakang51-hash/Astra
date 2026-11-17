package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class AstraRtfTextTest {
    private fun stream(value: String) =
        ByteArrayInputStream(value.toByteArray(StandardCharsets.ISO_8859_1))

    @Test fun extractsPlainTextParagraphsAndLiteralBracesWithoutFormattingNoise() {
        val rtf = """{\rtf1\ansi{\fonttbl{\f0 Times New Roman;}}\deff0
          \b Fixed income\b0 \par
          Bond duration measures a bond's price sensitivity to yield changes.
          \par When yields rise, bond prices typically fall.
          \par Literal \{braces\} and escaped \\ slash.}"""
        val result = AstraRtfText.extract(stream(rtf))
        assertTrue(result.contains("Fixed income"))
        assertTrue(result.contains("Bond duration measures"))
        assertTrue(result.contains("When yields rise"))
        assertTrue(result.contains("Literal {braces} and escaped \\ slash"))
        assertFalse(result.contains("Times New Roman"))
        assertFalse(result.contains("fonttbl"))
    }

    @Test fun extractsUnicodeAndCp1252EscapesWithoutDuplicateFallbackCharacters() {
        val rtf = """{\rtf1\ansi Russian: \u1046? \u1080? \u1074? \par
          French: caf\'e9 and apostrophe \u8217? end.}"""
        val result = AstraRtfText.extract(stream(rtf))
        assertTrue(result.contains("Жив"))
        assertTrue(result.contains("café"))
        assertTrue(result.contains("apostrophe ’ end"))
        assertFalse(result.contains("Ж?"))
    }

    @Test fun ignoresOpaqueEmbeddedBinaryAndUnknownDestinations() {
        val rtf = """{\rtf1\ansi Safe explanation of compound interest.
          {\*\generator never-index-this-metadata;}
          {\pict\bin7 {bad\}x}
          \par Interest earns interest over successive periods.}"""
        val result = AstraRtfText.extract(stream(rtf))
        assertTrue(result.contains("Safe explanation"))
        assertTrue(result.contains("Interest earns interest"))
        assertFalse(result.contains("never-index"))
        assertFalse(result.contains("bad"))
    }

    @Test fun importsIndexableRtfIntoExistingPrivateBookLibrary() {
        val directory = Files.createTempDirectory("astra-rtf-library").toFile()
        val library = AstraBookLibrary(directory)
        val document = """{\rtf1\ansi\b Income and duration notes\b0\par
            Modified duration describes approximate bond sensitivity to interest
            rate changes. Convexity improves the approximation for large moves.
            Bonds may lose value when market yields rise.}"""
        val book = library.importBook("bond-notes.rtf", stream(document))
        assertEquals("rtf", book.format)
        assertTrue(library.search("modified duration bond sensitivity").isNotEmpty())
    }

    @Test fun rejectsMalformedRtfAndOversizedInputWithoutSilentIndexing() {
        for (content in listOf(
            "not an rtf document",
            """{\rtf1\ansi broken """,
            """{\rtf1\ansi } }""",
            """{\rtf1\ansi \u999999? bad}""",
            """{\rtf1\ansi \bin99999 bad}"""
        )) {
            assertThrows(Exception::class.java) { AstraRtfText.extract(stream(content)) }
        }
        val depthBomb = "{\\rtf1" + "{".repeat(40) + "x" + "}".repeat(41)
        assertThrows(IllegalArgumentException::class.java) {
            AstraRtfText.extract(stream(depthBomb))
        }
        val oversized = "{\\rtf1 " + "a".repeat(2 * 1024 * 1024) + "}"
        assertThrows(IllegalArgumentException::class.java) {
            AstraRtfText.extract(stream(oversized))
        }
    }
}
