package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraMasterclassTest {
    private val memory = mutableMapOf<String, Int>()
    private fun course() = AstraMasterclass(
        { key, default -> memory[key] ?: default },
        { key, value -> memory[key] = value }
    )

    @Test fun authoredExtendedWorkshopsAreSubstantive() {
        assertEquals(14, AstraMasterclass.units("finance").size)
        assertEquals(14, AstraMasterclass.units("math").size)
        assertEquals(15, AstraMasterclass.units("english").size)
        assertEquals(14, AstraMasterclass.units("trading").size)
        assertEquals(57, AstraMasterclass.tracks.values.sumOf { it.size })
        for ((id, chapters) in AstraMasterclass.tracks) {
            assertTrue(id.isNotBlank())
            assertEquals(chapters.size, chapters.map { it.title }.toSet().size)
            for (unit in chapters) {
                assertTrue(unit.explanation.length >= 180)
                assertTrue(unit.workedCase.length >= 95)
                assertTrue(unit.independentExercise.length >= 80)
                assertEquals(3, unit.choices.distinct().size)
                assertTrue(unit.answer in 'A'..'C')
                assertTrue(unit.rationale.isNotBlank())
            }
        }
    }

    @Test fun eachWorkshopCanBeReadAndTestedOffline() {
        val engine = course()
        for ((subject, chapters) in AstraMasterclass.tracks) {
            val roadmap = engine.respond(subject + " workshop")!!
            assertTrue(roadmap.contains(chapters.size.toString() + " CHAPTERS"))
            for (i in chapters.indices) {
                val n = i + 1
                val lesson = engine.respond(subject + " workshop " + n)!!
                assertTrue(subject + " " + n,
                    lesson.contains("WORKSHOP " + n + "/" + chapters.size))
                assertTrue(lesson.contains("DETAILED THEORY"))
                assertTrue(lesson.contains("WORKED CASE"))
                assertTrue(lesson.contains("INDEPENDENT PRACTICE"))
                val quiz = engine.respond(subject + " workshop quiz " + n)!!
                assertTrue(quiz.contains("WORKSHOP QUIZ " + n + "/" + chapters.size))
                for (choice in listOf("A.", "B.", "C.")) assertTrue(quiz.contains(choice))
            }
        }
    }

    @Test fun progressIsNotSharedBetweenCourses() {
        val engine = course()
        engine.respond("finance workshop 1")
        engine.respond("finance workshop quiz 1")
        val answer = AstraMasterclass.units("finance")[0].answer
        assertTrue(engine.respond("finance workshop answer " + answer).orEmpty()
            .contains("Correct."))
        assertTrue(engine.respond("finance workshop progress").orEmpty()
            .contains("Chapters opened: 1/14"))
        assertTrue(engine.respond("finance workshop progress").orEmpty()
            .contains("Mastery quizzes passed: 1/14"))
        assertTrue(engine.respond("math workshop progress").orEmpty()
            .contains("Chapters opened: 0/14"))
        assertTrue(engine.respond("english workshop progress").orEmpty()
            .contains("Mastery quizzes passed: 0/15"))
        assertTrue(engine.respond("trading workshop progress").orEmpty()
            .contains("Quiz attempts: 0"))
    }

    @Test fun bondPriceWorkedCaseIsArithmeticallyCorrect() {
        val price = 50.0 / 1.06 + 1050.0 / (1.06 * 1.06)
        assertEquals(981.67, price, 0.01)
        val bondLesson = AstraMasterclass.units("finance")
            .first { it.title.contains("Bond valuation") }
        assertTrue(bondLesson.workedCase.contains("P981.67"))
        assertTrue(bondLesson.workedCase.contains("50/1.06"))
    }

    @Test fun quizRecoversOnRestartAndInvalidAnswersPreserveIt() {
        val first = course()
        first.respond("english workshop 2")
        first.respond("english workshop quiz 2")
        assertTrue(first.respond("english workshop answer Z").orEmpty()
            .contains("still open"))
        val correct = AstraMasterclass.units("english")[1].answer
        val incorrect = listOf('A', 'B', 'C').first { it != correct }
        val restored = course()
        assertTrue(restored.respond("english workshop answer " + incorrect).orEmpty()
            .contains("Not quite."))
        assertTrue(restored.respond("english workshop progress").orEmpty()
            .contains("Mastery quizzes passed: 0/15"))
        assertTrue(restored.respond("english workshop answer " + correct).orEmpty()
            .contains("No english workshop quiz"))
        assertTrue(restored.respond("english workshop next").orEmpty()
            .contains("WORKSHOP 3/15"))
    }

    @Test fun unrelatedRequestsAreNotIntercepted() {
        val engine = course()
        assertNull(engine.respond("analyse this market price today"))
        assertNull(engine.respond("show my trading account"))
        assertNull(engine.respond("read all local files"))
        assertNull(engine.respond("finance workshop delete progress"))
        assertNull(engine.respond("history workshop"))
        assertNull(engine.respond("x".repeat(200)))
        assertTrue(engine.respond("finance workshop 100").orEmpty().contains("1 to 14"))
        assertTrue(engine.respond("english workshop quiz -1").orEmpty().contains("1 to 15"))
    }

    @Test fun originalFoundationLessonsUseIsolatedProgressKeys() {
        val read = { key: String, default: Int -> memory[key] ?: default }
        val write = { key: String, value: Int -> memory[key] = value }
        val math = AstraMathCourse(read, write)
        val english = AstraEnglishCourse(read, write)
        val trading = AstraTradingCourse(read, write)
        math.respond("math lesson 1")
        assertTrue(math.respond("math progress").orEmpty().contains("Lessons opened: 1/16"))
        assertTrue(english.respond("english progress").orEmpty().contains("Lessons opened: 0/16"))
        assertTrue(trading.respond("trading progress").orEmpty().contains("Lessons opened: 0/16"))
        english.respond("english lesson 2")
        trading.respond("trading lesson 3")
        assertTrue(math.respond("math progress").orEmpty().contains("Lessons opened: 1/16"))
        assertTrue(english.respond("english progress").orEmpty().contains("Lessons opened: 1/16"))
        assertTrue(trading.respond("trading progress").orEmpty().contains("Lessons opened: 1/16"))
        assertFalse(memory.keys.any { it.isBlank() })
        assertTrue(memory.keys.any { it.startsWith("astra_math_") })
        assertTrue(memory.keys.any { it.startsWith("astra_english_") })
        assertTrue(memory.keys.any { it.startsWith("astra_trading_") })
    }
}
