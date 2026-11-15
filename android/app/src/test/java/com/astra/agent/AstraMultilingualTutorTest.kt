package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraMultilingualTutorTest {
    private val numbers = mutableMapOf<String, Int>()
    private val strings = mutableMapOf<String, String>()
    private fun tutor() = AstraMultilingualTutor(
        { k, d -> numbers[k] ?: d },
        { k, v -> numbers[k] = v },
        { k, d -> strings[k] ?: d },
        { k, v -> strings[k] = v }
    )

    @Test fun allEightLanguagesOfferThreeOfflineLessonsAndQuizzes() {
        val coach = tutor()
        val catalog = coach.respond("languages")!!
        for (lang in AstraMultilingualTutor.Language.entries) {
            assertTrue("Missing " + lang.display, catalog.contains(lang.display.substringBefore(" (")))
            val first = coach.respond("learn " + lang.id)!!
            assertTrue(first.contains("lesson 1/3"))
            assertTrue(coach.respond("next language lesson")!!.contains("lesson 2/3"))
            assertTrue(coach.respond(lang.id + " lesson 3")!!.contains("lesson 3/3"))
            val quiz = coach.respond(lang.id + " quiz")!!
            assertTrue(quiz.contains("practice:"))
            assertTrue(coach.respond("answer hello")!!.startsWith("Correct."))
            assertTrue(coach.respond(lang.id + " progress")!!.contains("1 correct"))
        }
        assertTrue(coach.respond("language progress")!!.contains("8 correct"))
    }

    @Test fun BotswanaSetswanaHasCorrectContextualGreetingsAndSourcePhrases() {
        val coach = tutor()
        assertTrue(coach.respond("learn setswana")!!.contains("Dumela"))
        assertTrue(coach.respond("setswana lesson 2")!!.contains("O tsogile jang"))
        assertTrue(coach.respond("setswana lesson 3")!!.contains("Tsamaya sentle"))
        assertTrue(coach.respond("learn tswana")!!.contains("Dumela"))
    }

    @Test fun JapaneseAndMandarinShowNonLatinScriptsWithPronunciations() {
        val coach = tutor()
        assertTrue(coach.respond("learn japanese")!!.contains("こんにちは (konnichiwa)"))
        assertTrue(coach.respond("learn mandarin")!!.contains("你好 (nǐ hǎo)"))
    }

    @Test fun multipleLanguagesRetainIndependentPersistentProgress() {
        val a = tutor()
        a.respond("setswana quiz")
        assertTrue(a.respond("answer hello")!!.startsWith("Correct."))
        a.respond("french quiz")
        assertTrue(a.respond("answer hello")!!.startsWith("Correct."))
        val b = tutor()
        assertTrue(b.respond("setswana progress")!!.contains("1 correct"))
        assertTrue(b.respond("french progress")!!.contains("1 correct"))
        assertTrue(b.respond("language progress")!!.contains("2 correct"))
        assertEquals(1, numbers["lang_setswana_correct"])
        assertEquals(1, numbers["lang_french_correct"])
    }

    @Test fun wrongAnswersAndUnrelatedCommandsNeverIncrementProgress() {
        val a = tutor()
        a.respond("german quiz")
        assertNull(a.respond("add task buy bread"))
        assertNull(a.respond("open settings"))
        assertTrue(a.respond("answer nonsense")!!.contains("Not quite"))
        assertEquals(0, numbers["lang_german_correct"] ?: 0)
        assertTrue(a.respond("answer hello")!!.startsWith("Correct."))
        assertEquals(1, numbers["lang_german_correct"])
    }

    @Test fun switchingCoursesInvalidatesOldPendingQuestions() {
        val a = tutor()
        a.respond("spanish quiz")
        a.respond("learn swahili")
        assertEquals(0, numbers["lang_spanish_pending"])
        assertNull(a.respond("answer hello"))
        a.respond("swahili quiz")
        assertTrue(a.respond("skip language question")!!.contains("Skipped"))
        assertNull(a.respond("answer hello"))
    }

    @Test fun explicitEnglishTranslationsAcceptPunctuationAndCase() {
        val a = tutor()
        a.respond("portuguese quiz")
        assertTrue(a.respond("answer HELLO!!!")!!.startsWith("Correct."))
        a.respond("portuguese quiz")
        assertTrue(a.respond("answer goodbye")!!.startsWith("Correct."))
    }

    @Test fun noAdditionalLanguageCommandsTouchRussianProgressKeys() {
        val a = tutor()
        a.respond("setswana quiz")
        a.respond("answer hello")
        assertFalse(numbers.keys.any { it.startsWith("russian_") })
        assertEquals(32, AstraMultilingualTutor.progressKeys().size)
    }
}
