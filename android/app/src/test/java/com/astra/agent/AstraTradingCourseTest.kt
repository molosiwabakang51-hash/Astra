package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraTradingCourseTest {
    private val values = mutableMapOf<String, Int>()
    private fun course() = AstraTradingCourse(
        { key, default -> values[key] ?: default },
        { key, value -> values[key] = value }
    )

    @Test fun allLessonsHaveDistinctQuizzesAndRealWorkedExamples() {
        assertEquals(16, AstraTradingCourse.lessons.size)
        val course = course()
        assertTrue(course.respond("trading course").orEmpty()
            .contains("DAY TRADING FOUNDATION · 16 OFFLINE LESSONS"))
        assertTrue(course.respond("teach me day trading").orEmpty()
            .contains("16 OFFLINE LESSONS"))
        for (number in 1..16) {
            assertTrue(course.respond("trading lesson $number").orEmpty()
                .contains("TRADING LESSON $number/16"))
            val quiz = course.respond("trading quiz $number").orEmpty()
            assertTrue(quiz.contains("TRADING QUIZ $number/16"))
            for (letter in listOf("A.", "B.", "C.")) assertTrue(quiz.contains(letter))
            val lesson = AstraTradingCourse.lessons[number - 1]
            assertTrue(lesson.title.isNotBlank())
            assertTrue(lesson.concept.isNotBlank())
            assertTrue(lesson.workedExample.isNotBlank())
            assertTrue(lesson.note.isNotBlank())
            assertTrue(lesson.explanation.isNotBlank())
            assertEquals(3, lesson.choices.distinct().size)
            assertTrue(lesson.answer in 'A'..'C')
        }
        val trading = AstraStudyCatalog.find("day_trading")!!
        assertEquals(16, trading.topics.count { it.command.startsWith("trading lesson ") })
        assertTrue(trading.topics.all { !it.requiresModel })
    }

    @Test fun quizGradingTracksPrivateMasteryAcrossSessions() {
        val first = course()
        first.respond("trading lesson 1")
        first.respond("trading quiz 1")
        assertTrue(first.respond("answer trading A").orEmpty().contains("Not quite."))
        assertTrue(first.respond("trading progress").orEmpty().contains("Quiz answers: 0/1"))
        val restored = course()
        restored.respond("trading quiz 1")
        assertTrue(restored.respond("answer trading B").orEmpty().contains("Correct."))
        assertTrue(restored.respond("trading progress").orEmpty().contains("Quiz answers: 1/2"))
        assertTrue(restored.respond("trading progress").orEmpty().contains("quizzes mastered: 1/16"))
    }

    @Test fun invalidAnswersAndOutOfRangeLessonsFailWithoutConsumingQuiz() {
        val course = course()
        assertTrue(course.respond("trading lesson 17").orEmpty().contains("from 1 to 16"))
        assertTrue(course.respond("trading lesson -1").orEmpty().contains("from 1 to 16"))
        assertTrue(course.respond("answer trading A").orEmpty().contains("No Trading quiz"))
        course.respond("trading quiz 2")
        assertTrue(course.respond("answer trading Z").orEmpty().contains("Choose A, B or C"))
        assertTrue(course.respond("answer trading A").orEmpty().contains("Correct."))
        assertTrue(course.respond("answer trading A").orEmpty().contains("No Trading quiz"))
    }

    @Test fun progressAndEndOfCourseRemainBounded() {
        val course = course()
        course.respond("trading lesson 15")
        assertTrue(course.respond("next trading lesson").orEmpty().contains("LESSON 16/16"))
        repeat(3) {
            assertTrue(course.respond("next trading lesson").orEmpty().contains("LESSON 16/16"))
        }
        assertTrue(course.respond("my trading progress").orEmpty().contains("Last opened: 16/16"))
        assertFalse(course.respond("trading progress").orEmpty().contains("ENGLISH COURSE"))
    }

    @Test fun unrelatedConversationsAreNeverMisrouted() {
        val course = course()
        assertNull(course.respond("add task learn about trading"))
        assertNull(course.respond("Should I buy shares now?"))
        assertNull(course.respond("What is my portfolio risk?"))
        assertNull(course.respond("Analyze this live market chart"))
        assertNull(course.respond("calculate 5 * 7"))
        assertNull(course.respond("a".repeat(1000)))
    }

    @Test fun calculationsAreEducationalAndRiskWarningsAreVisible() {
        val course = course()
        val risk = course.respond("trading lesson 9").orEmpty()
        assertTrue(risk.contains("50 shares"))
        assertTrue(risk.contains("gaps", ignoreCase = true))
        val leverage = course.respond("trading lesson 12").orEmpty()
        assertTrue(leverage.contains("5×"))
        assertTrue(leverage.contains("10%"))
        assertTrue(leverage.contains("NOT INVESTMENT ADVICE"))
    }
}
