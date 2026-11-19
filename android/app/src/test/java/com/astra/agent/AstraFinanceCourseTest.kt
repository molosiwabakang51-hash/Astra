package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraFinanceCourseTest {
    private fun course(memory: MutableMap<String, Int>): AstraFinanceCourse =
        AstraFinanceCourse(
            readInt = { key, default -> memory[key] ?: default },
            writeInt = { key, value -> memory[key] = value }
        )

    @Test fun twentyDistinctCorrectlyConfiguredOfflineLessonsExist() {
        val lessons = AstraFinanceCourse.lessons
        assertEquals(20, lessons.size)
        assertEquals(20, lessons.map { it.title }.toSet().size)
        lessons.forEachIndexed { index, lesson ->
            assertTrue("Lesson ${index + 1} lacks a substantive explanation",
                lesson.explanation.length > 85)
            assertTrue(lesson.workedExample.length > 65)
            assertTrue(lesson.caution.isNotBlank())
            assertEquals(3, lesson.choices.size)
            assertTrue(lesson.answer in 'A'..'C')
            assertTrue(lesson.quiz.endsWith("?") || lesson.quiz.endsWith(":"))
        }
        val finance = AstraStudyCatalog.find("finance")!!
        for (index in 1..20) {
            val topic = finance.topics.single { it.command == "finance lesson $index" }
            assertTrue(topic.title.startsWith("Lesson $index — "))
            assertFalse(topic.requiresModel)
        }
        assertTrue(finance.topics.any { it.command == "finance course" })
        assertTrue(finance.topics.any { it.command == "finance quiz" })
        assertTrue(finance.topics.any { it.command == "finance progress" })
        assertTrue(finance.topics.any { it.command == "explain bond duration" })
    }

    @Test fun financeSyllabusAndEveryLessonWorkInAirplaneModeWithoutModel() {
        val saved = mutableMapOf<String, Int>()
        val tutor = course(saved)
        val syllabus = tutor.respond("finance course")!!
        assertTrue(syllabus.contains("20 OFFLINE LESSONS"))
        assertTrue(syllabus.contains("20. Stress testing"))
        assertTrue(tutor.respond("learn finance")!!.contains("OFFLINE LESSONS"))
        for (n in 1..20) {
            val output = tutor.respond("finance lesson $n")!!
            assertTrue(output.contains("FINANCE LESSON $n/20"))
            assertTrue(output.contains("WORKED EXAMPLE"))
            assertTrue(output.contains("CHECK THE ASSUMPTIONS"))
        }
        val progress = tutor.respond("finance progress")!!
        assertTrue(progress.contains("Lessons opened: 20/20"))
        assertTrue(progress.contains("Last opened: 20/20"))
        assertTrue(progress.contains("Lesson quizzes mastered: 0/20"))
        assertTrue(tutor.respond("next finance lesson")!!.contains("LESSON 20/20"))
    }

    @Test fun exactOneAnswerPerQuizAndMasterySurviveReinstantiation() {
        val saved = mutableMapOf<String, Int>()
        val tutor = course(saved)
        assertTrue(tutor.respond("answer finance B")!!.contains("No finance quiz"))
        for (n in 1..20) {
            val prompt = tutor.respond("finance quiz $n")!!
            assertTrue(prompt.contains("FINANCE QUIZ $n/20"))
            assertTrue(prompt.contains("A."))
            assertTrue(prompt.contains("B."))
            assertTrue(prompt.contains("C."))
            val answer = AstraFinanceCourse.lessons[n - 1].answer
            assertTrue(tutor.respond("answer finance $answer")!!.startsWith("Correct."))
            assertTrue(tutor.respond("answer finance $answer")!!.contains("No finance quiz"))
        }
        val restored = course(saved)
        val progress = restored.respond("my finance progress")!!
        assertTrue(progress.contains("Lesson quizzes mastered: 20/20"))
        assertTrue(progress.contains("Quiz answers: 20/20 correct"))
    }

    @Test fun incorrectAnswerCountsAttemptButDoesNotMarkLessonMastered() {
        val saved = mutableMapOf<String, Int>()
        val tutor = course(saved)
        assertNotNull(tutor.respond("finance quiz 3"))
        assertTrue(tutor.respond("answer finance A")!!.startsWith("Not quite."))
        val progress = tutor.respond("finance progress")!!
        assertTrue(progress.contains("Lesson quizzes mastered: 0/20"))
        assertTrue(progress.contains("Quiz answers: 0/1 correct"))
        tutor.respond("finance quiz 3")
        assertTrue(tutor.respond("answer finance C")!!.startsWith("Correct."))
        assertTrue(tutor.respond("finance progress")!!.contains("Quiz answers: 1/2 correct"))
    }

    @Test fun quizRequiresExplicitAtoCAndSupportsSkip() {
        val tutor = course(mutableMapOf())
        assertTrue(tutor.respond("finance quiz")!!.contains("FINANCE QUIZ 1/20"))
        assertTrue(tutor.respond("answer finance D")!!.contains("Choose A, B or C"))
        assertTrue(tutor.respond("answer finance")!!.contains("Choose A, B or C"))
        assertTrue(tutor.respond("skip finance quiz")!!.contains("skipped"))
        assertTrue(tutor.respond("answer finance A")!!.contains("No finance quiz"))
    }

    @Test fun invalidNumbersAndUnrelatedCommandsNeverInterceptNormalChat() {
        val tutor = course(mutableMapOf())
        assertTrue(tutor.respond("finance lesson 0")!!.contains("from 1 to 20"))
        assertTrue(tutor.respond("finance lesson 21")!!.contains("from 1 to 20"))
        assertTrue(tutor.respond("finance quiz 9999")!!.contains("from 1 to 20"))
        assertTrue(tutor.respond("teach me finance")!!.contains("20 OFFLINE LESSONS"))
        assertNull(tutor.respond("what did I say about my portfolio?"))
        assertNull(tutor.respond("done 1"))
        assertNull(tutor.respond("add task study finance"))
        assertNull(tutor.respond("explain bond duration"))
        assertNull(tutor.respond("What is the latest exchange rate?"))
        assertNull(tutor.respond("I want a personalized recommendation"))
        assertNull(tutor.respond("answer thanks"))
    }

    @Test fun exactFinancialWorkedExamplesAreConservativeAndConsistent() {
        val tutor = course(mutableMapOf())
        assertTrue(tutor.respond("finance lesson 3")!!.contains("P1,331"))
        assertTrue(tutor.respond("finance lesson 4")!!.contains("P1,000"))
        assertTrue(tutor.respond("finance lesson 5")!!.contains("P1,735.54"))
        assertTrue(tutor.respond("finance lesson 6")!!.contains("2.86%"))
        assertTrue(tutor.respond("finance lesson 8")!!.contains("7%"))
        assertTrue(tutor.respond("finance lesson 13")!!.contains("−4.8%"))
        assertTrue(tutor.respond("finance lesson 15")!!.contains("P41.32"))
        assertTrue(tutor.respond("finance lesson 16")!!.contains("−5.5%"))
        assertTrue(tutor.respond("finance lesson 20")!!.contains("25%"))
    }
}
