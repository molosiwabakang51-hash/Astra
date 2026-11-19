package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraVoiceLanguageCommandTest {
    @Test fun allSupportedAndroidSpeechLocalesHaveMatchingAiLanguage() {
        val names = listOf(
            "English", "British English", "Russian", "Setswana", "French",
            "Spanish", "Portuguese", "German", "Swahili", "Japanese", "Mandarin",
            "French Canada", "Spanish Mexico", "Portuguese Brazil",
            "Arabic", "Hindi", "Korean", "Italian", "Turkish", "Urdu"
        )
        assertEquals(SpeechLanguage.entries.size, names.size)
        for (name in names) {
            val selected = AstraVoiceLanguageCommand.parse("speak in " + name)
            assertTrue("No matched language: " + name, selected != null)
            assertTrue(selected!!.speech.tag.isNotBlank())
            assertTrue(selected.reply.id.isNotBlank())
            assertTrue(AstraVoiceLanguageCommand.testPhrase(selected.speech).isNotBlank())
        }
    }

    @Test fun spokenUserCommandsSupportPhrasingAndSomeNativeLanguages() {
        val cases = mapOf(
            "Speak in French" to SpeechLanguage.FRENCH,
            "Talk to me in German" to SpeechLanguage.GERMAN,
            "Listen in Russian" to SpeechLanguage.RUSSIAN,
            "Use Setswana for voice" to SpeechLanguage.SETSWANA,
            "Please use French for voice" to SpeechLanguage.FRENCH,
            "Listen to me in Spanish" to SpeechLanguage.SPANISH,
            "Set speech language to Japanese" to SpeechLanguage.JAPANESE,
            "Switch voice to Mandarin Chinese" to SpeechLanguage.MANDARIN,
            "Parle français" to SpeechLanguage.FRENCH,
            "Háblame en español" to SpeechLanguage.SPANISH,
            "Sprich Deutsch" to SpeechLanguage.GERMAN,
            "Говори по-русски" to SpeechLanguage.RUSSIAN
        )
        for ((text, expected) in cases) {
            assertEquals(text, expected, AstraVoiceLanguageCommand.parse(text)?.speech)
        }
    }

    @Test fun selectingLocaleDoesNotPretendSpeechPacksAreInstalled() {
        val french = AstraVoiceLanguageCommand.parse("speak in french")!!
        val missing = AstraVoiceLanguageCommand.confirmation(french, false)
        assertTrue(missing.contains("not confirmed"))
        assertTrue(missing.contains("Install"))
        val installed = AstraVoiceLanguageCommand.confirmation(french, true)
        assertTrue(installed.contains("non-network"))
        assertTrue(installed.contains("recognition depends"))
        assertTrue(AstraVoiceLanguageCommand.testPhrase(french.speech).contains("Bonjour"))
        assertFalse(AstraVoiceLanguageCommand.testPhrase(french.speech).contains("voice test"))
    }

    @Test fun speechCommandsDoNotInterceptGeneralConversations() {
        for (text in listOf(
            "speak about finance",
            "what can you tell me about leverage",
            "listen to music",
            "use French to translate this passage",
            "reply in Arabic",
            "speak in madeuplanguage",
            "add task speak French",
            "speak in " + "x".repeat(250)
        )) {
            assertNull(text, AstraVoiceLanguageCommand.parse(text))
        }
    }
}
