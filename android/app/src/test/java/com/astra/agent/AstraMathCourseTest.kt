package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraMathCourseTest {
    private val memory = mutableMapOf<String, Int>()
    private fun course() = AstraMathCourse(
        { key, default -> memory[key] ?: default },
        { key, value -> memory[key] = value }
    )

    @Test fun sixteenGenuineLessonsAndQuizzesAreBundledOffline() {
        val course = course()
        assertEquals(16, AstraMathCourse.lessons.size)
        assertTrue(course.respond("math course").orEmpty().contains("16 OFFLINE LESSONS"))
        for (i in 1..16) {
            val lesson = course.respond("math lesson $i").orEmpty()
            assertTrue(lesson.contains("MATH LESSON $i/16"))
            assertTrue(lesson.contains("WORKED EXAMPLE"))
            assertTrue(lesson.contains("CHECK YOUR THINKING"))
            val quiz = course.respond("math quiz $i").orEmpty()
            assertTrue(quiz.contains("MATH QUIZ $i/16"))
            assertTrue(quiz.contains("A."))
            assertTrue(quiz.contains("B."))
            assertTrue(quiz.contains("C."))
            assertTrue(AstraMathCourse.lessons[i - 1].workedExample.isNotBlank())
        }
        assertEquals(16, AstraStudyCatalog.find("mathematics")!!.topics.count {
            it.command.startsWith("math lesson ")
        })
        assertTrue(AstraStudyCatalog.find("mathematics")!!.topics.all { !it.requiresModel })
    }

    @Test fun quizTracksAttemptsAndMasteryWithoutAnyExternalModel() {
        val course = course()
        assertTrue(course.respond("math lesson 1").orEmpty().contains("arithmetic", true))
        assertTrue(course.respond("math quiz 1").orEmpty().contains("6 + 4"))
        assertTrue(course.respond("answer math B").orEmpty().contains("Not quite."))
        assertTrue(course.respond("math progress").orEmpty().contains("Quiz answers: 0/1"))
        assertTrue(course.respond("math quiz 1").orEmpty().contains("MATH QUIZ 1/16"))
        assertTrue(course.respond("answer math A").orEmpty().contains("Correct."))
        assertTrue(course.respond("math progress").orEmpty().contains("Quiz answers: 1/2"))
        assertTrue(course.respond("math progress").orEmpty().contains("quizzes mastered: 1/16"))
    }

    @Test fun progressSurvivesNewCourseInstanceAndNextLessonAdvances() {
        val first = course()
        first.respond("math lesson 3")
        val next = course()
        assertTrue(next.respond("next math lesson").orEmpty().contains("MATH LESSON 4/16"))
        assertTrue(next.respond("math progress").orEmpty().contains("Lessons opened: 2/16"))
        assertTrue(next.respond("math progress").orEmpty().contains("Last opened: 4/16"))
    }

    @Test fun invalidCommandsDoNotHijackRegularMathOrPrivateTasks() {
        val course = course()
        assertNull(course.respond("Add task finish math homework"))
        assertNull(course.respond("calculate 12 * 5"))
        assertNull(course.respond("What is a bond?"))
        assertNull(course.respond("mathematics question outside the course"))
        assertTrue(course.respond("math lesson 99").orEmpty().contains("from 1 to 16"))
        assertTrue(course.respond("math lesson -3").orEmpty().contains("from 1 to 16"))
        assertTrue(course.respond("math lesson 2147483648").orEmpty().contains("from 1 to 16"))
        assertTrue(course.respond("answer math A").orEmpty().contains("No Maths quiz is active"))
        assertNull(course.respond("x".repeat(1000)))
    }

    @Test fun impossibleQuizInputDoesNotConsumePendingQuestion() {
        val course = course()
        course.respond("math quiz 5")
        assertTrue(course.respond("answer math Z").orEmpty().contains("Choose A, B or C"))
        assertTrue(course.respond("answer math B").orEmpty().contains("Correct."))
        assertTrue(course.respond("answer math B").orEmpty().contains("No Maths quiz is active"))
    }

    @Test fun endOfCourseDoesNotOverrunFiniteLessonCount() {
        val course = course()
        course.respond("math lesson 16")
        repeat(5) {
            assertTrue(course.respond("next math lesson").orEmpty().contains("MATH LESSON 16/16"))
        }
        assertTrue(course.respond("math progress").orEmpty().contains("Last opened: 16/16"))
    }

    @Test fun quizzesHaveOneCorrectOptionEach() {
        AstraMathCourse.lessons.forEach { lesson ->
            assertEquals(3, lesson.choices.size)
            assertEquals(3, lesson.choices.distinct().size)
            assertTrue(lesson.answer in 'A'..'C')
            assertTrue(lesson.explanation.length >= 20)
            assertFalse(lesson.workedExample.contains("??"))
        }
    }
}
