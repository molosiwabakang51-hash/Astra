package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraWorkshopReviewTest {
    @Test fun wrongAnswersResetStreakAndAreImmediatelyDue() {
        val prior = AstraWorkshopReview.State(4, 14, 25014)
        val after = AstraWorkshopReview.afterAnswer(
            todayUtcDay = 25005, correct = false, previous = prior
        )
        assertEquals(0, after.streak)
        assertEquals(0, after.intervalDays)
        assertEquals(25005, after.dueUtcDay)
        assertTrue(AstraWorkshopReview.due(25005, after))
    }

    @Test fun scheduledSuccessesGraduallyExtendTheInterval() {
        var time = 25000
        var state: AstraWorkshopReview.State? = null
        val expected = listOf(1, 3, 7, 14, 30, 60, 120, 120)
        for ((index, interval) in expected.withIndex()) {
            state = AstraWorkshopReview.afterAnswer(time, true, state)
            assertEquals(index + 1, state.streak)
            assertEquals(interval, state.intervalDays)
            assertEquals(time + interval, state.dueUtcDay)
            assertFalse(AstraWorkshopReview.due(time, state))
            time += interval
            assertTrue(AstraWorkshopReview.due(time, state))
        }
    }

    @Test fun repeatingAnswersBeforeReviewDateDoesNotAdvanceSchedule() {
        val first = AstraWorkshopReview.afterAnswer(25000, true, null)
        val repeated = AstraWorkshopReview.afterAnswer(25000, true, first)
        assertEquals(first, repeated)
        assertEquals(first, AstraWorkshopReview.afterAnswer(25000, true, repeated))
        val onTime = AstraWorkshopReview.afterAnswer(25001, true, first)
        assertEquals(3, onTime.intervalDays)
        assertEquals(25004, onTime.dueUtcDay)
    }

    @Test fun chapterReviewSurvivesRestartAndNeverCrossesSubjects() {
        val memory = mutableMapOf<String, Int>()
        var today = 25000
        fun engine() = AstraMasterclass(
            readInt = { key, default -> memory[key] ?: default },
            writeInt = { key, value -> memory[key] = value },
            todayUtcDay = { today }
        )
        val first = engine()
        assertTrue(first.respond("french workshop review")!!.contains("Due now: 0"))
        assertTrue(first.respond("french workshop review next")!!.contains("No french"))
        first.respond("french workshop 1")
        first.respond("french workshop quiz 1")
        val correct = AstraMasterclass.units("french")[0].answer
        assertTrue(first.respond("french workshop answer " + correct)!!.contains("Correct."))
        assertEquals(25001, memory["astra_masterclass_french_review_due_1"])
        assertTrue(engine().respond("french workshop review plan")!!.contains("In 1 day"))
        assertTrue(engine().respond("french workshop review")!!.contains("Due now: 0"))
        assertTrue(engine().respond("physics workshop review")!!.contains("Due now: 0"))
        today = 25001
        assertTrue(engine().respond("french workshop review")!!.contains("Due now: 1"))
        val next = engine().respond("french workshop review next")!!
        assertTrue(next.contains("WORKSHOP QUIZ 1/16"))
        assertTrue(engine().respond("french workshop answer " + correct)!!.contains("Correct."))
        assertEquals(25004, memory["astra_masterclass_french_review_due_1"])
        assertTrue(engine().respond("french workshop review")!!.contains("Due now: 0"))
    }

    @Test fun incorrectQuizCreatesAnImmediateRecoveryPractice() {
        val memory = mutableMapOf<String, Int>()
        val engine = AstraMasterclass(
            { key, fallback -> memory[key] ?: fallback },
            { key, value -> memory[key] = value },
            { 25000 }
        )
        engine.respond("math workshop quiz 1")
        val correct = AstraMasterclass.units("math")[0].answer
        val wrong = listOf('A', 'B', 'C').first { it != correct }
        assertTrue(engine.respond("math workshop answer " + wrong)!!.contains("Not quite"))
        assertTrue(engine.respond("math workshop review")!!.contains("Due now: 1"))
        assertTrue(engine.respond("math workshop review next")!!.contains("WORKSHOP QUIZ 1/14"))
        assertTrue(engine.respond("math workshop answer " + correct)!!.contains("Correct."))
        assertTrue(engine.respond("math workshop review plan")!!.contains("In 1 day"))
    }

    @Test fun oldMasteredQuizProgressBecomesDueWithoutBeingErased() {
        val old = mutableMapOf("astra_masterclass_finance_mastered_0" to 1)
        val engine = AstraMasterclass(
            { key, fallback -> old[key] ?: fallback },
            { key, value -> old[key] = value },
            { 25000 }
        )
        assertTrue(engine.respond("finance workshop review")!!.contains("Due now: 1"))
        assertTrue(engine.respond("finance workshop review next")!!.contains("QUIZ 1/14"))
        assertEquals(1, old["astra_masterclass_finance_mastered_0"])
        assertTrue(engine.respond("finance workshop progress")!!
            .contains("Mastery quizzes passed: 1/14"))
    }
}
