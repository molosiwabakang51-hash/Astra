package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class AstraVocabularyStoreTest {
    private fun store() = AstraVocabularyStore(
        Files.createTempDirectory("astra-user-words").toFile()
    )
    private fun bytes(text: String) =
        ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    @Test fun tsvImportAddsAndPersistsWithoutChangingOtherLanguages() {
        val dir = Files.createTempDirectory("astra-private-cards").toFile()
        val first = AstraVocabularyStore(dir)
        val imported = first.importTsv(
            "setswana", bytes("phrase\ttranslation\nMaru\tClouds\nPula\tRain\n")
        )
        assertEquals(2, imported.added)
        assertEquals(2, imported.totalCustomWords)
        assertEquals(0, imported.alreadyPresent)
        assertEquals(listOf(
            AstraVocabularyStore.Word("Maru", "Clouds"),
            AstraVocabularyStore.Word("Pula", "Rain")
        ), AstraVocabularyStore(dir).list("setswana"))
        assertTrue(first.list("french").isEmpty())
    }

    @Test fun duplicateVocabularyPreservesExistingTranslationAndGermanCase() {
        val s = store()
        s.importTsv("german", bytes("Essen\tFood\nessen\tTo eat\n"))
        val duplicate = s.importTsv(
            "german", bytes("Essen\tincorrect\nFreundschaft\tFriendship\n")
        )
        assertEquals(1, duplicate.added)
        assertEquals(1, duplicate.alreadyPresent)
        assertEquals(3, s.list("german").size)
        assertEquals("Food", s.list("german").first().translation)
    }

    @Test fun malformedInputIsRejectedAtomicallyWithNoPartialChanges() {
        val s = store()
        s.importTsv("japanese", bytes("空\tSky\n"))
        assertThrows(IllegalArgumentException::class.java) {
            s.importTsv("japanese", bytes("山\tMountain\nbroken without tab\n"))
        }
        assertEquals(1, s.list("japanese").size)
        assertEquals("空", s.list("japanese").single().phrase)
        assertThrows(IllegalArgumentException::class.java) {
            s.importTsv("../../../", bytes("bad\tDanger\n"))
        }
    }

    @Test fun extremelyLargeImportStopsAtBoundAndKeepsExistingData() {
        val s = store()
        s.importTsv("mandarin", bytes("汉语\tChinese language\n"))
        val excessive = (1..10_100)
            .joinToString("\n") { "word_$it\tmeaning_$it" } + "\n"
        assertThrows(IllegalArgumentException::class.java) {
            s.importTsv("mandarin", bytes(excessive))
        }
        assertEquals(1, s.list("mandarin").size)
    }

    @Test fun tenThousandNewWordsPerLanguageRemainPossible() {
        val s = store()
        val source = (1..AstraVocabularyStore.MAX_CUSTOM_WORDS_PER_LANGUAGE)
            .joinToString("\n") { "word_$it\tmeaning_$it" }
        val result = s.importTsv("spanish", bytes(source))
        assertEquals(10_000, result.added)
        assertEquals(10_000, s.list("spanish").size)
        assertEquals(0, s.list("russian").size)
        assertTrue(s.clear("spanish"))
        assertTrue(s.list("spanish").isEmpty())
    }

    @Test fun maximumImportedSetsReachRealLessonsBeyondOneThousand() {
        val s = store()
        val extras = (1..10_000).joinToString("\n") {
            "new_term_" + it + "\tMeaning " + it
        }
        s.importTsv("spanish", bytes(extras))
        s.importTsv("russian", bytes(extras))
        val state = mutableMapOf<String, Int>()
        val flags = mutableMapOf<String, String>()
        val multilingual = AstraMultilingualTutor(
            { key, def -> state[key] ?: def },
            { key, value -> state[key] = value },
            { key, def -> flags[key] ?: def },
            { key, value -> flags[key] = value }, s
        )
        val russian = RussianTutor(
            { key, def -> state[key] ?: def },
            { key, value -> state[key] = value }, s
        )
        assertTrue(multilingual.respond("spanish lesson 2521")!!
            .contains("new_term_10000"))
        assertTrue(multilingual.respond("spanish progress")!!
            .contains("10084 vocabulary cards"))
        assertTrue(russian.respond("russian lesson 2523")!!
            .contains("new_term_10000"))
        assertTrue(russian.respond("russian progress")!!
            .contains("10096 vocabulary cards"))
    }

    @Test fun existingUserCardsCanExtendMultilingualLessonCountsAndQuiz() {
        val s = store()
        s.importTsv("french", bytes(
            "Courage\tCourage\nRêve\tDream\nSoleil levant\tSunrise\n"
        ))
        val nums = mutableMapOf<String, Int>()
        val strs = mutableMapOf<String, String>()
        val tutor = AstraMultilingualTutor(
            { k, d -> nums[k] ?: d }, { k, v -> nums[k] = v },
            { k, d -> strs[k] ?: d }, { k, v -> strs[k] = v }, s
        )
        assertTrue(tutor.respond("french lesson 22")!!.contains("Courage"))
        assertTrue(tutor.respond("french progress")!!.contains("87 vocabulary cards"))
        nums["lang_french_next"] = 84
        assertTrue(tutor.respond("french quiz")!!.contains("Courage"))
        assertTrue(tutor.respond("answer courage")!!.startsWith("Correct."))
    }

    @Test fun importedRussianTermsExtendRealLessonsAfterOriginalNinetySix() {
        val s = store()
        s.importTsv("russian", bytes(
            "Самолёт\tAirplane\nНебо\tSky\nОстров\tIsland\n"
        ))
        val nums = mutableMapOf<String, Int>()
        val tutor = RussianTutor(
            { k, d -> nums[k] ?: d },
            { k, v -> nums[k] = v }, s
        )
        assertTrue(tutor.respond("russian lesson 23")!!.contains("Самолёт"))
        assertTrue(tutor.respond("russian lesson 24")!!.contains("Остров"))
        assertTrue(tutor.respond("russian progress")!!.contains("99 vocabulary cards"))
        nums["russian_next_question"] = 96
        assertTrue(tutor.respond("russian quiz")!!.contains("Самолёт"))
        assertTrue(tutor.respond("answer airplane")!!.startsWith("Correct."))
    }

    @Test fun invalidUtf8IsRejectedAndNothingIsWritten() {
        val s = store()
        val malformed = ByteArrayInputStream(
            byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x09, 0x41)
        )
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            s.importTsv("swahili", malformed)
        }
        assertTrue(s.list("swahili").isEmpty())
        assertFalse(s.list("french").isNotEmpty())
    }
}
