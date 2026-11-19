package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraLessonSpeechTest {
    private val french = """
        French lesson 2/21 — Conversation and common requests

        Oui — Yes
        Non — No
        Comment ça va ? — How are you?
        Je vais bien — I am fine

        Type 'next language lesson' or 'french quiz' to practise.
    """.trimIndent()

    @Test fun onlyFrenchExamplesAreSpokenNotEnglishExplanations() {
        val result = AstraLessonSpeech.fromLesson(french, SpeechLanguage.ENGLISH_US)!!
        assertEquals(SpeechLanguage.FRENCH, result.language)
        assertEquals(4, result.phraseCount)
        assertTrue(result.spokenText.contains("Comment ça va ?"))
        assertTrue(result.spokenText.contains("Je vais bien"))
        assertFalse(result.spokenText.contains("How are you"))
        assertFalse(result.spokenText.contains("next language lesson"))
        assertFalse(result.spokenText.contains("Conversation and common requests"))
    }

    @Test fun userChosenRegionalAccentIsPreserved() {
        val canadian = AstraLessonSpeech.fromLesson(
            french, SpeechLanguage.FRENCH_CANADA
        )!!
        assertEquals(SpeechLanguage.FRENCH_CANADA, canadian.language)
        val mexico = AstraLessonSpeech.fromLesson(
            "Spanish lesson 1/21 — Greetings\n\nHola — Hello",
            SpeechLanguage.SPANISH_MEXICO
        )!!
        assertEquals(SpeechLanguage.SPANISH_MEXICO, mexico.language)
        val brazil = AstraLessonSpeech.fromLesson(
            "Portuguese lesson 1/21 — Greetings\n\nOlá — Hello",
            SpeechLanguage.PORTUGUESE_BRAZIL
        )!!
        assertEquals(SpeechLanguage.PORTUGUESE_BRAZIL, brazil.language)
        assertEquals(SpeechLanguage.FRENCH, AstraLessonSpeech.fromLesson(
            french, SpeechLanguage.ENGLISH_UK
        )!!.language)
    }

    @Test fun russianCyrillicIsSpokenWithoutTransliterationOrEnglish() {
        val result = AstraLessonSpeech.fromLesson(
            """
            Russian lesson 4 — Numbers and useful nouns

            Один (odin) — one
            Два (dva) — two
            Книга (kniga, book) — book

            Practise in Russian.
            """.trimIndent(), SpeechLanguage.ENGLISH_US
        )!!
        assertEquals(SpeechLanguage.RUSSIAN, result.language)
        assertEquals(3, result.phraseCount)
        assertTrue(result.spokenText.contains("Один"))
        assertTrue(result.spokenText.contains("Книга"))
        assertFalse(result.spokenText.contains("odin"))
        assertFalse(result.spokenText.contains("kniga"))
        assertFalse(result.spokenText.contains("book"))
    }

    @Test fun detectsAllNineAuthoredVocabularyCourses() {
        val examples = mapOf(
            "Setswana (Botswana)" to Pair(SpeechLanguage.SETSWANA, "Dumela"),
            "French" to Pair(SpeechLanguage.FRENCH, "Bonjour"),
            "Russian" to Pair(SpeechLanguage.RUSSIAN, "Привет"),
            "Spanish" to Pair(SpeechLanguage.SPANISH, "Hola"),
            "Portuguese" to Pair(SpeechLanguage.PORTUGUESE, "Olá"),
            "German" to Pair(SpeechLanguage.GERMAN, "Guten Tag"),
            "Swahili" to Pair(SpeechLanguage.SWAHILI, "Habari"),
            "Japanese" to Pair(SpeechLanguage.JAPANESE, "こんにちは"),
            "Mandarin Chinese" to Pair(SpeechLanguage.MANDARIN, "你好")
        )
        for ((course, expected) in examples) {
            val text = "$course lesson 1/21 — Greetings\n\n" + expected.second + " — Hello"
            val result = AstraLessonSpeech.fromLesson(text, SpeechLanguage.ENGLISH_US)
            assertEquals(course, expected.first, result?.language)
            assertEquals(course, expected.second, result?.spokenText?.removeSuffix("."))
        }
    }

    @Test fun generalChatAndDeepExplanationsAreNotIncorrectlyClassified() {
        for (input in listOf(
            "French is an interesting language.",
            "Please answer my trading question in French.",
            "French lesson 2/21 — Greetings\n\nThere are no vocabulary rows.",
            "RUSSIAN · WORKSHOP 1/18\n\nA long explanation with Cyrillic.",
            "Spanish lesson 1/21 — Greetings\n\nNo valid translation",
            "German lesson 0/21 — Invalid\n\nHallo — Hello",
            "French lesson 1/21 — Greetings\n\n1 — One",
            "Finance lesson 1/20 — Markets\n\nBond — A debt instrument",
            "French lesson 1/21 — Greetings\n\nBad phrase\u0000 — Hello"
        )) {
            assertNull(input, AstraLessonSpeech.fromLesson(
                input, SpeechLanguage.ENGLISH_US
            ))
        }
    }

    @Test fun longInputsAndManyCardsStayBounded() {
        assertNull(AstraLessonSpeech.fromLesson("x".repeat(5000),
            SpeechLanguage.ENGLISH_US))
        val many = "French lesson 1/21 — Test\n\n" +
            (1..50).joinToString("\n") { "Merci$it — Thanks$it" }
        val result = AstraLessonSpeech.fromLesson(many, SpeechLanguage.FRENCH)!!
        assertEquals(24, result.phraseCount)
        assertTrue(result.spokenText.length <= 700)
    }
}
