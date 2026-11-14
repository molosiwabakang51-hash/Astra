package com.astra.agent

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EverydayAssistantTest {
    private val state = mutableMapOf<String, String>()
    private var today = "2026-11-14"
    private fun assistant() = EverydayAssistant(
        { key, default -> state[key] ?: default },
        { key, value -> state[key] = value },
        { today }
    )

    @Test fun shoppingPersistsAndPreservesUnicode() {
        val a = assistant()
        assertTrue(a.respond("add crème fraîche to shopping list", emptyList())!!.contains("Added"))
        assertTrue(a.respond("add crème fraîche to shopping list", emptyList())!!.contains("already"))
        val b = assistant()
        assertTrue(b.respond("shopping list", emptyList())!!.contains("crème fraîche"))
        assertTrue(b.respond("remove 1 from shopping list", emptyList())!!.contains("Removed"))
        assertTrue(b.respond("shopping list", emptyList())!!.contains("empty"))
    }

    @Test fun habitsResetAtDayBoundary() {
        val a = assistant()
        a.respond("add habit walk", emptyList())
        a.respond("habit done walk", emptyList())
        assertTrue(a.respond("habits", emptyList())!!.contains("1/1"))
        today = "2026-11-15"
        assertTrue(assistant().respond("habits", emptyList())!!.contains("0/1"))
    }

    @Test fun dailyOverviewReusesExistingTasks() {
        val a = assistant()
        a.respond("add habit read", emptyList())
        val result = a.respond("plan my day", listOf("Review bond pricing"))!!
        assertTrue(result.contains("Review bond pricing"))
        assertTrue(result.contains("read"))
    }

    @Test fun unrelatedRequestsPassThrough() {
        assertNull(assistant().respond("calculate 2 + 2", emptyList()))
    }

    @Test fun wrongHabitIsHandled() {
        assertTrue(assistant().respond("habit done fly", emptyList())!!.contains("not found"))
    }
}
