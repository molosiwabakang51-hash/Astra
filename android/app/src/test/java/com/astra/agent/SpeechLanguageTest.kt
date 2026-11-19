package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguageTest {
    @Test fun eightNewLanguageTagsRoundTripAndUseExplicitLocales() {
        val expected = mapOf(
            "tn-BW" to SpeechLanguage.SETSWANA,
            "fr-FR" to SpeechLanguage.FRENCH,
            "es-ES" to SpeechLanguage.SPANISH,
            "pt-PT" to SpeechLanguage.PORTUGUESE,
            "de-DE" to SpeechLanguage.GERMAN,
            "sw-KE" to SpeechLanguage.SWAHILI,
            "ja-JP" to SpeechLanguage.JAPANESE,
            "zh-CN" to SpeechLanguage.MANDARIN
        )
        expected.forEach { (tag, language) ->
            assertEquals(language, SpeechLanguage.fromTag(tag))
            assertEquals(language, SpeechLanguage.fromTag(tag.lowercase()))
            assertEquals(tag, language.tag)
        }
        assertEquals(20, SpeechLanguage.entries.size)
        assertEquals(SpeechLanguage.FRENCH_CANADA, SpeechLanguage.fromTag("fr-CA"))
        assertEquals(SpeechLanguage.SPANISH_MEXICO, SpeechLanguage.fromTag("es-MX"))
        assertEquals(SpeechLanguage.PORTUGUESE_BRAZIL, SpeechLanguage.fromTag("pt-BR"))
        assertEquals(SpeechLanguage.ARABIC, SpeechLanguage.fromTag("ar-SA"))
        assertEquals(SpeechLanguage.HINDI, SpeechLanguage.fromTag("hi-IN"))
    }

    @Test fun tagSelectionIsStable() {
        assertEquals(SpeechLanguage.ENGLISH_UK, SpeechLanguage.fromTag("en-GB"))
        assertEquals(SpeechLanguage.RUSSIAN, SpeechLanguage.fromTag("ru-RU"))
        assertEquals(SpeechLanguage.ENGLISH_US, SpeechLanguage.fromTag("invalid"))
        assertEquals(SpeechLanguage.ENGLISH_US, SpeechLanguage.fromTag(null))
    }
}
