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

    @Test fun allEightLanguagesOfferTwentyOneOfflineLessonsAndEightFourCards() {
        val coach = tutor()
        val catalog = coach.respond("languages")!!
        for (lang in AstraMultilingualTutor.Language.entries) {
            assertTrue("Missing " + lang.display, catalog.contains(lang.display.substringBefore(" (")))
            val first = coach.respond("learn " + lang.id)!!
            assertTrue(first.contains("lesson 1/21"))
            assertTrue(coach.respond("next language lesson")!!.contains("lesson 2/21"))
            assertTrue(coach.respond(lang.id + " lesson 3")!!.contains("lesson 3/21"))
            val final = coach.respond(lang.id + " lesson 21")!!
            assertTrue(final.contains("lesson 21/21"))
            assertTrue(final.contains("Course lessons complete"))
            assertTrue(coach.respond("next language lesson")!!.contains("lesson 21/21"))
            assertTrue(coach.respond(lang.id + " lesson 22")!!.contains("has 21 lessons"))
            val quiz = coach.respond(lang.id + " quiz")!!
            assertTrue(quiz.contains("practice:"))
            assertTrue(coach.respond("answer hello")!!.startsWith("Correct."))
            assertTrue(coach.respond(lang.id + " progress")!!.contains("84 vocabulary cards"))
            assertTrue(coach.respond(lang.id + " progress")!!.contains("1 correct"))
        }
        assertTrue(coach.respond("language progress")!!.contains("8 correct"))
    }

    @Test fun everyPackHasSeventyTwoWellFormedDistinctExtraTerms() {
        for (lang in AstraMultilingualTutor.Language.entries) {
            val additions = AstraExtendedVocabulary.additional(lang.id)
            assertEquals(lang.id, 72, additions.size)
            assertEquals(lang.id, 72, additions.map { it.target }.distinct().size)
            assertTrue(additions.all { it.target.isNotBlank() && it.english.isNotBlank() })
            assertEquals("mother", additions.first().english)
            assertEquals("peace", additions.last().english)
        }
    }

    @Test fun everyCardIsActuallyReachableThroughQuizAfterThousandsOfSteps() {
        val coach = tutor()
        for (lang in AstraMultilingualTutor.Language.entries) {
            val seen = mutableSetOf<String>()
            repeat(84) {
                val prompt = coach.respond(lang.id + " quiz")!!
                val phrase = prompt.substringAfter("What does '").substringBefore("' mean")
                assertTrue(lang.id + " duplicate flashcard " + it, seen.add(phrase))
                assertTrue(coach.respond("skip language question")!!.startsWith("Skipped."))
            }
            assertEquals(84, seen.size)
            assertTrue(coach.respond(lang.id + " quiz")!!.contains(
                when (lang) {
                    AstraMultilingualTutor.Language.SETSWANA -> "Dumela"
                    AstraMultilingualTutor.Language.FRENCH -> "Bonjour"
                    AstraMultilingualTutor.Language.SPANISH -> "Hola"
                    AstraMultilingualTutor.Language.PORTUGUESE -> "Olá"
                    AstraMultilingualTutor.Language.GERMAN -> "Hallo"
                    AstraMultilingualTutor.Language.SWAHILI -> "Jambo"
                    AstraMultilingualTutor.Language.JAPANESE -> "こんにちは"
                    AstraMultilingualTutor.Language.MANDARIN -> "你好"
                }
            ))
        }
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

    @Test fun localDictionaryTranslatesKnownWordsBothWaysWithoutNetwork() {
        val coach = tutor()
        assertTrue(coach.respond("how do you say hello in french")!!
            .contains("Bonjour — Hello"))
        assertTrue(coach.respond("translate mother to spanish")!!
            .contains("madre — mother"))
        assertTrue(coach.respond("what does こんにちは mean in japanese")!!
            .contains("こんにちは — Hello"))
        assertTrue(coach.respond("translate hola from spanish")!!
            .contains("Hola — Hello"))
        assertTrue(coach.respond("German for food")!!.contains("Essen — food"))
        assertTrue(coach.respond("what does Essen mean in German")!!
            .contains("Essen — food"))
        assertTrue(coach.respond("what does essen mean in German")!!
            .contains("essen — to eat"))
        assertTrue(coach.respond("translate astrophysics to french")!!
            .contains("No matching offline"))
        assertNull(coach.respond("What is bond convexity?"))
    }

    @Test fun ownerImportedCardsBecomeOfflineDictionaryEntriesImmediately() {
        val store = AstraVocabularyStore(
            java.nio.file.Files.createTempDirectory("astra-local-dictionary").toFile()
        )
        store.importTsv("setswana", java.io.ByteArrayInputStream(
            "Maru\\tClouds\\n".replace("\\t", "\t")
                .replace("\\n", "\n").toByteArray(Charsets.UTF_8)
        ))
        val coach = AstraMultilingualTutor(
            { key, def -> numbers[key] ?: def },
            { key, value -> numbers[key] = value },
            { key, def -> strings[key] ?: def },
            { key, value -> strings[key] = value }, store
        )
        assertTrue(coach.respond("how do you say clouds in setswana")!!
            .contains("Maru — Clouds"))
        assertTrue(coach.respond("what does Maru mean in Setswana")!!
            .contains("Maru — Clouds"))
        assertTrue(coach.respond("setswana progress")!!
            .contains("85 vocabulary cards"))
    }

    @Test fun noAdditionalLanguageCommandsTouchRussianProgressKeys() {
        val a = tutor()
        a.respond("setswana quiz")
        a.respond("answer hello")
        assertFalse(numbers.keys.any { it.startsWith("russian_") })
        assertEquals(32, AstraMultilingualTutor.progressKeys().size)
    }
}
