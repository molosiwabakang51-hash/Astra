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

    @Test fun nativeChunksCanSplitAnEmojiAcrossTwoUpdates() {
        val out = AstraStreamUnicode.StreamingBuffer(16)
        val emoji = "🧠"
        assertFalse(out.append("Answer " + emoji.substring(0, 1)))
        assertEquals("Answer ", out.snapshot())
        assertFalse(out.append(emoji.substring(1) + " done"))
        assertEquals("Answer 🧠 done", out.snapshot())
        assertEquals("Answer 🧠 done", out.finish())
    }

    @Test fun unfinishedNativeEmojiIsNeverPersistedAsMalformedUtf16() {
        val out = AstraStreamUnicode.StreamingBuffer(12)
        assertFalse(out.append("Hello" + "🚀".substring(0, 1)))
        assertEquals("Hello", out.snapshot())
        assertEquals("Hello�", out.finish())
        assertFalse(out.finish().last().isHighSurrogate())
    }

    @Test fun unmatchedLowSurrogatesAreReplacedWithoutSplittingNonLatinText() {
        val emoji = "🌍"
        val out = AstraStreamUnicode.StreamingBuffer(30)
        assertFalse(out.append("Привет " + emoji.substring(1)))
        assertFalse(out.append(" 世界"))
        assertEquals("Привет � 世界", out.finish())
        assertFalse(out.snapshot().any { (it.isHighSurrogate() || it.isLowSurrogate()) })
    }

    @Test fun aPairThatCannotFitNeverLeavesASingleHighSurrogate() {
        val buffer = AstraStreamUnicode.StreamingBuffer(5)
        assertFalse(buffer.append("abcd" + "🚀".substring(0, 1)))
        assertEquals("abcd", buffer.snapshot())
        assertTrue(buffer.append("🚀".substring(1) + " more"))
        assertEquals("abcd", buffer.finish())
        assertTrue(buffer.append("again")) // truncation remains latched
    }

    @Test fun outputCapIsPermanentAfterTruncation() {
        val out = AstraStreamUnicode.StreamingBuffer(5)
        assertTrue(out.append("123456"))
        assertEquals("12345", out.snapshot())
        assertTrue(out.append("untrusted later text"))
        assertEquals("12345", out.finish())
    }

    @Test fun splitCharacterAtCapIsFollowedByStableUnicodeSafeAnswer() {
        val out = AstraStreamUnicode.StreamingBuffer(16_000)
        val prefix = "a".repeat(15_999)
        assertFalse(out.append(prefix))
        assertFalse(out.append("🧠".substring(0, 1)))
        assertEquals(prefix, out.snapshot())
        assertTrue(out.append("🧠".substring(1) + " further generation"))
        val final = out.finish()
        assertEquals(prefix, final)
        assertFalse(final.any { (it.isHighSurrogate() || it.isLowSurrogate()) })
    }

    @Test fun multipleSplitEmojisAndNormalNonEnglishTextRemainIntact() {
        val out = AstraStreamUnicode.StreamingBuffer(80)
        for (piece in listOf("Français: ", "🎓".substring(0, 1),
                "🎓".substring(1), " русский ", "👩‍🎓".substring(0, 1),
                "👩‍🎓".substring(1))) {
            assertFalse(out.append(piece))
        }
        assertEquals("Français: 🎓 русский 👩‍🎓", out.finish())
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
