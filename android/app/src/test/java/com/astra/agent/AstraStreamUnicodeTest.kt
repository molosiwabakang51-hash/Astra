package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraStreamUnicodeTest {
    @Test fun refusesToCutSupplementaryEmojiInHalf() {
        val out = StringBuilder("a".repeat(15_999))
        val capped = AstraStreamUnicode.appendCapped(out, "🚀 and more", 16_000)
        assertTrue(capped)
        assertEquals(15_999, out.length)
        assertFalse(Character.isHighSurrogate(out.last()))
    }

    @Test fun fullyIncludesEmojiWhenBothUtf16UnitsFit() {
        val out = StringBuilder("abc")
        val capped = AstraStreamUnicode.appendCapped(out, "🚀", 5)
        assertFalse(capped)
        assertEquals("abc🚀", out.toString())
        assertEquals(5, out.length)
    }

    @Test fun limitsLongMessagesWithoutDroppingValidNonEnglishText() {
        val out = StringBuilder()
        assertFalse(AstraStreamUnicode.appendCapped(out, "Привет 世界 ", 70))
        assertTrue(AstraStreamUnicode.appendCapped(out, "bond yield and duration", 19))
        assertEquals("Привет 世界 bond yiel", out.toString())
    }

    @Test fun emptyAndZeroCapacityChunksRemainSafe() {
        val out = StringBuilder("hello")
        assertFalse(AstraStreamUnicode.appendCapped(out, "", 5))
        assertTrue(AstraStreamUnicode.appendCapped(out, "world", 5))
        assertEquals("hello", out.toString())
        assertThrows(IllegalArgumentException::class.java) {
            AstraStreamUnicode.appendCapped(StringBuilder(), "data", 0)
        }
    }
}
