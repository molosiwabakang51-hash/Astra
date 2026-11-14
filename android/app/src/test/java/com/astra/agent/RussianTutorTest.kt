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

    @Test fun unrelatedCommandsAreNotConsumed() {
        val coach = tutor()
        coach.respond("Russian quiz")
        assertNull(coach.respond("add task revise bonds"))
        assertNotNull(coach.respond("skip Russian question"))
        assertEquals(null, coach.respond("answer hi"))
    }
}
