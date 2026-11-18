package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraEnglishCourseTest {
    private val memory = mutableMapOf<String, Int>()
    private fun course() = AstraEnglishCourse(
        { key, default -> memory[key] ?: default },
        { key, value -> memory[key] = value }
    )

    @Test fun sixteenRealEnglishLessonsAndQuizzesRunWithoutModel() {
        val course = course()
        assertEquals(16, AstraEnglishCourse.lessons.size)
        assertTrue(course.respond("english course").orEmpty()
            .contains("ENGLISH FOUNDATION · 16 OFFLINE LESSONS"))
        for (number in 1..16) {
            assertTrue(course.respond("english lesson $number").orEmpty()
                .contains("ENGLISH LESSON $number/16"))
            assertTrue(course.respond("english quiz $number").orEmpty()
                .contains("ENGLISH QUIZ $number/16"))
            val lesson = AstraEnglishCourse.lessons[number - 1]
            assertTrue(lesson.concept.isNotBlank())
            assertTrue(lesson.workedExample.isNotBlank())
            assertTrue(lesson.note.isNotBlank())
            assertTrue(lesson.question.isNotBlank())
            assertEquals(3, lesson.choices.distinct().size)
            assertTrue(lesson.answer in 'A'..'C')
        }
        val english = AstraStudyCatalog.find("english")!!
        assertEquals(16, english.topics.count { it.command.startsWith("english lesson ") })
        assertTrue(english.topics.all { !it.requiresModel })
    }

    @Test fun wrongThenCorrectAnswerRecordsAttemptAndMastery() {
        val course = course()
        course.respond("english lesson 1")
        course.respond("english quiz 1")
        assertTrue(course.respond("answer english A").orEmpty().contains("Not quite."))
        assertTrue(course.respond("english progress").orEmpty().contains("Quiz answers: 0/1"))
        course.respond("english quiz 1")
        assertTrue(course.respond("answer english B").orEmpty().contains("Correct."))
        assertTrue(course.respond("english progress").orEmpty().contains("Quiz answers: 1/2"))
        assertTrue(course.respond("english progress").orEmpty().contains("quizzes mastered: 1/16"))
    }

    @Test fun persistedProgressAndNextLessonBehaveCorrectly() {
        val course = course()
        course.respond("english lesson 3")
        val restored = course()
        assertTrue(restored.respond("next english lesson").orEmpty().contains("LESSON 4/16"))
        assertTrue(restored.respond("my english progress").orEmpty().contains("Lessons opened: 2/16"))
        assertTrue(restored.respond("english progress").orEmpty().contains("Last opened: 4/16"))
        restored.respond("english lesson 16")
        assertTrue(restored.respond("next english lesson").orEmpty().contains("LESSON 16/16"))
    }

    @Test fun commandBoundaryDoesNotHijackUnrelatedConversation() {
        val course = course()
        assertNull(course.respond("Add task finish English homework"))
        assertNull(course.respond("Please edit my essay"))
        assertNull(course.respond("What does Shakespeare mean?"))
        assertNull(course.respond("Summarize the document"))
        assertNull(course.respond("english".repeat(30)))
        assertTrue(course.respond("english lesson 99").orEmpty().contains("from 1 to 16"))
        assertTrue(course.respond("answer english A").orEmpty().contains("No English quiz"))
    }

    @Test fun invalidAnswerDoesNotDiscardTheActiveQuiz() {
        val course = course()
        course.respond("english quiz 2")
        assertTrue(course.respond("answer english Z").orEmpty().contains("Choose A, B or C"))
        assertTrue(course.respond("answer english C").orEmpty().contains("Correct."))
        assertTrue(course.respond("answer english C").orEmpty().contains("No English quiz"))
        assertFalse(course.respond("english progress").orEmpty().contains("MATHEMATICS"))
    }
}
