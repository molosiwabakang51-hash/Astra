package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianTutorTest {
    private val state = mutableMapOf<String, Int>()
    private fun tutor() = RussianTutor(
        { key, default -> state[key] ?: default },
        { key, value -> state[key] = value }
    )

    @Test fun bareRussianRoutesToRealOfflineLesson() {
        assertTrue(tutor().respond("russian")!!.contains("Привет"))
        assertTrue(tutor().respond("Russian language")!!.contains("Привет"))
    }

    @Test fun lessonsAreAvailableOffline() {
        assertTrue(tutor().respond("Teach me Russian")!!.contains("Привет"))
        assertTrue(tutor().respond("Russian lesson 2")!!.contains("До свидания"))
    }

    @Test fun practiceProgressSurvivesTutorRecreation() {
        val first = tutor()
        assertTrue(first.respond("Russian quiz")!!.contains("Спасибо"))
        assertTrue(first.respond("answer thank you")!!.startsWith("Correct."))
        val second = tutor()
        assertTrue(second.respond("Russian progress")!!.contains("1 correct"))
        assertTrue(second.respond("Russian quiz")!!.contains("Да"))
    }

    @Test fun wrongAnswerDoesNotAdvanceProgress() {
        val coach = tutor()
        coach.respond("Russian quiz")
        assertTrue(coach.respond("answer banana")!!.contains("Not quite"))
        assertTrue(coach.respond("Russian progress")!!.contains("0 correct"))
        assertTrue(coach.respond("answer thanks")!!.startsWith("Correct."))
    }

    @Test fun bareAnswerPromptsWithoutLosingCurrentQuestion() {
        val coach = tutor()
        coach.respond("Russian quiz")
        assertTrue(coach.respond("answer")!!.contains("Please give the English meaning"))
        assertTrue(coach.respond("answer thank you")!!.startsWith("Correct."))
    }

    @Test fun answerWithoutActiveQuizIsExplained() {
        assertTrue(tutor().respond("answer")!!.contains("no active Russian question"))
    }

    @Test fun allTwentyThreeOfflineLessonsAreAvailableWithSequentialNavigation() {
        val coach = tutor()
        assertTrue(coach.respond("Russian course")!!.contains("23 offline lessons and 96 vocabulary cards"))
        assertTrue(coach.respond("russian lesson 1")!!.contains("Привет"))
        assertTrue(coach.respond("next Russian lesson")!!.contains("До свидания"))
        assertTrue(coach.respond("next Russian lesson")!!.contains("33 letters"))
        assertTrue(coach.respond("next Russian lesson")!!.contains("Один"))
        assertTrue(coach.respond("next Russian lesson")!!.contains("Родительный"))
        assertTrue(coach.respond("next Russian lesson")!!.contains("написать"))
        assertTrue(coach.respond("next Russian lesson")!!.contains("Russian lesson 7/23"))
        assertTrue(coach.respond("russian lesson 23")!!.contains("Russian lesson 23/23"))
        assertTrue(coach.respond("next Russian lesson")!!.contains("Russian lesson 23/23"))
        assertTrue(coach.respond("russian lesson 24")!!.contains("23 lessons"))
        assertTrue(tutor().respond("Russian progress")!!.contains("23/23"))
    }

    @Test fun RussianShorthandOpensTheMatchingCourseModule() {
        assertTrue(tutor().respond("Russian alphabet")!!.contains("Cyrillic"))
        assertTrue(tutor().respond("Russian grammar")!!.contains("six grammatical cases"))
        assertTrue(tutor().respond("Russian verbs")!!.contains("imperfective"))
        assertTrue(tutor().respond("Russian numbers")!!.contains("Четыре"))
    }

    @Test fun vocabularyPracticeExtendsBeyondOriginalSixCards() {
        val coach = tutor()
        val firstSix = listOf("thank you", "yes", "no", "hi", "please", "goodbye")
        for (translation in firstSix) {
            assertTrue(coach.respond("Russian quiz")!!.contains("Russian practice:"))
            assertTrue(coach.respond("answer " + translation)!!.startsWith("Correct."))
        }
        assertTrue(coach.respond("Russian quiz")!!.contains("Доброе утро"))
        assertTrue(coach.respond("answer good morning")!!.startsWith("Correct."))
        assertTrue(coach.respond("Russian progress")!!.contains("7 correct"))
        assertTrue(coach.respond("Russian progress")!!.contains("96 vocabulary cards"))
    }

    @Test fun russianCourseContainsNinetySixActualPracticeCards() {
        val coach = tutor()
        val found = mutableSetOf<String>()
        repeat(96) {
            val question = coach.respond("Russian quiz")!!
            val term = question.substringAfter("What does '").substringBefore("' mean")
            assertTrue("Repeated Russian word: " + term, found.add(term))
            assertTrue(coach.respond("skip russian question")!!.startsWith("Skipped."))
        }
        assertEquals(96, found.size)
        assertTrue(coach.respond("Russian quiz")!!.contains("Спасибо"))
        assertTrue(coach.respond("russian course")!!.contains("96 vocabulary cards"))
    }

    @Test fun unrelatedCommandsAreNotConsumed() {
        val coach = tutor()
        coach.respond("Russian quiz")
        assertNull(coach.respond("add task revise bonds"))
        assertNotNull(coach.respond("skip Russian question"))
        assertTrue(coach.respond("answer hi")!!.contains("no active Russian question"))
    }
}
