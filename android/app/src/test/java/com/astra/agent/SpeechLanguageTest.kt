package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguageTest {
    @Test fun tagSelectionIsStable() {
        assertEquals(SpeechLanguage.ENGLISH_UK, SpeechLanguage.fromTag("en-GB"))
        assertEquals(SpeechLanguage.RUSSIAN, SpeechLanguage.fromTag("ru-RU"))
        assertEquals(SpeechLanguage.ENGLISH_US, SpeechLanguage.fromTag("invalid"))
        assertEquals(SpeechLanguage.ENGLISH_US, SpeechLanguage.fromTag(null))
    }
}
