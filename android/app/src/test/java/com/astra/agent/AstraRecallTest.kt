package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AstraRecallTest {
    @Test fun remembersOnlySavedItems() {
        val notes = listOf(
            "Bond duration measures price sensitivity to interest rates.",
            "Russian vocabulary: Спасибо means thank you."
        )
        val tasks = listOf("Review portfolio bond duration")
        val result = AstraRecall.respond("What did I say about bond duration?", notes, tasks)!!
        assertTrue(result.contains("Note 1: Bond duration"))
        assertTrue(result.contains("Task 1: Review portfolio"))
        assertTrue(result.contains("Searched on this device only"))
    }

    @Test fun notesOnlyFilterHonorsSource() {
        val notes = listOf("Study derivatives")
        val tasks = listOf("Study bond duration")
        val result = AstraRecall.respond("Find my notes about bond duration", notes, tasks)!!
        assertTrue(result.contains("No saved notes"))
        assertTrue(!result.contains("Task 1:"))
    }

    @Test fun summaryCountsWithoutExposingContents() {
        val summary = AstraRecall.respond(
            "memory overview",
            listOf("password should never be echoed in summary"),
            listOf("private task")
        )!!
        assertTrue(summary.contains("1 saved notes | 1 open tasks"))
        assertTrue(!summary.contains("password"))
        assertTrue(!summary.contains("private task"))
    }

    @Test fun noHallucinatedMemoriesAndIndependentQueries() {
        val notes = listOf("Pula money market fund")
        assertTrue(AstraRecall.respond("recall quantum physics", notes, emptyList())!!
            .contains("No saved notes or tasks"))
        assertNull(AstraRecall.respond("calculate 30 * 5", notes, emptyList()))
        assertTrue(AstraRecall.respond("recall the", notes, emptyList())!!
            .contains("Please name a topic"))
    }

    @Test fun phraseSearchHandlesUnicodeAndLimits() {
        val notes = listOf("Спасибо — Russian for Thank you", "Portfolio inflation-linked bonds")
        val result = AstraRecall.respond("recall спасибо", notes, emptyList())!!
        assertTrue(result.contains("Note 1:"))
        val tooLong = AstraRecall.respond("recall " + "x".repeat(200), notes, emptyList())!!
        assertTrue(tooLong.contains("160 characters"))
    }

    @Test fun randomizedSearchDoesNotMutateSourceLists() {
        val random = Random(20261114L)
        val notes = (1..128).map { "Note $it: bond duration, inflation and finance" }
        val tasks = (1..128).map { "Task $it: revise bond finance" }
        val beforeNotes = notes.toList()
        val beforeTasks = tasks.toList()
        repeat(10_000) {
            val alphabet = "abc ABC 123 ?! Привет"
            val suffix = buildString {
                repeat(random.nextInt(24)) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
            AstraRecall.respond("recall $suffix", notes, tasks)
        }
        assertEquals(beforeNotes, notes)
        assertEquals(beforeTasks, tasks)
    }
}
