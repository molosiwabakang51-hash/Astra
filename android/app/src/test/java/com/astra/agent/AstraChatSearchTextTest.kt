package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraChatSearchTextTest {
    @Test fun accentedAndUnaccentedLatinTermsMatchBothDirections() {
        assertTrue(AstraChatSearchText.contains("Études de marché", "etudes"))
        assertTrue(AstraChatSearchText.contains("resume of data", "résumé"))
        val value = "Une tasse de café coûte P30."
        val found = AstraChatSearchText.find(value, "CAFE")!!
        assertEquals("café", value.substring(found.start, found.endExclusive))
    }

    @Test fun composedAndDecomposedUnicodeYieldSafeOriginalOffsets() {
        val source = "📚 cafe\u0301 — résumé 🧠"
        val match = AstraChatSearchText.find(source, "café")!!
        assertEquals("cafe", source.substring(match.start, match.endExclusive))
        // The intact accent remains in source and in wider chat previews.
        assertEquals('\u0301', source[match.endExclusive])
        assertTrue(AstraChatSearchText.contains(source, "RESUME"))
        assertTrue(AstraChatSearchText.contains("Ёж изучает финансы", "еж"))
        val extra = "🧠".repeat(35) + "café" + "🐈".repeat(20)
        val hit = AstraChatSearchText.find(extra, "CAFE")!!
        assertEquals("café", extra.substring(hit.start, hit.endExclusive))
        assertFalse(Character.isLowSurrogate(extra[hit.start]))
        assertFalse(Character.isHighSurrogate(extra[hit.endExclusive - 1]))
    }

    @Test fun originalExactSearchAndNoFuzzyInventedTransliteration() {
        val text = "I paid 50 Pula in Maun"
        val hit = AstraChatSearchText.find(text, "PULA")!!
        assertEquals("Pula", text.substring(hit.start, hit.endExclusive))
        assertNull(AstraChatSearchText.find(text, "not-present"))
        assertNull(AstraChatSearchText.find("Москва", "Moskva"))
        // Script-specific vowel signs carry meaning and must not be stripped
        // as though they were optional Latin accents.
        assertNull(AstraChatSearchText.find("किताब", "कतब"))
        assertNull(AstraChatSearchText.find("مُدَرِّس", "مدرس"))
        assertNull(AstraChatSearchText.find("café", " \u0301"))
        assertNull(AstraChatSearchText.find(text, ""))
    }
}
