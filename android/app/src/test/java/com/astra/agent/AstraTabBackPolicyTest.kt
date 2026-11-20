package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AstraTabBackPolicyTest {
    @Test fun AndroidBackAtChatRootHasNoFakePreviousScreen() {
        assertNull(AstraTabBackPolicy.previous(0, emptyList()))
        assertNull(AstraTabBackPolicy.previous(0, listOf(0)))
    }

    @Test fun SettingsTasksBackReturnsThroughActualScreenHistory() {
        assertEquals(
            AstraTabBackPolicy.Target(4, 1),
            AstraTabBackPolicy.previous(1, listOf(0, 4))
        )
        assertEquals(
            AstraTabBackPolicy.Target(0, 0),
            AstraTabBackPolicy.previous(4, listOf(0))
        )
    }

    @Test fun BackFromChatReturnsToSettingsRatherThanClosingAstra() {
        // Chat -> Settings -> Chat. The old handler ignored the history
        // when Chat was current and allowed Android to exit the app.
        val fromChat = AstraTabBackPolicy.previous(0, listOf(0, 4))
        assertEquals(AstraTabBackPolicy.Target(4, 1), fromChat)
        val fromSettings = AstraTabBackPolicy.previous(
            fromChat!!.tab,
            listOf(0, 4).take(fromChat.remainingHistory)
        )
        assertEquals(AstraTabBackPolicy.Target(0, 0), fromSettings)
    }

    @Test fun StudyToChatAlsoReturnsToStudyBeforeRoot() {
        assertEquals(
            AstraTabBackPolicy.Target(5, 1),
            AstraTabBackPolicy.previous(0, listOf(0, 5))
        )
    }

    @Test fun DuplicateOrInvalidHistoryCannotTrapBackOnSameScreen() {
        assertEquals(
            AstraTabBackPolicy.Target(4, 1),
            AstraTabBackPolicy.previous(0, listOf(0, 4, 0, -1, 0, 99))
        )
        assertNull(AstraTabBackPolicy.previous(0, listOf(0, -1, 99)))
        assertEquals(
            AstraTabBackPolicy.Target(0, 0),
            AstraTabBackPolicy.previous(5, listOf(5, -1, 5))
        )
    }

    @Test fun SavedNonChatTabCanReturnToChatWithoutSessionHistory() {
        for (tab in 1..5) {
            assertEquals(
                AstraTabBackPolicy.Target(0, 0),
                AstraTabBackPolicy.previous(tab, emptyList())
            )
        }
    }

    @Test fun RepeatedBackWalksMultiScreenJourneyWithoutLooping() {
        var current = 5
        var history = listOf(0, 4, 1)
        val visited = mutableListOf<Int>()
        while (true) {
            val next = AstraTabBackPolicy.previous(current, history) ?: break
            visited += next.tab
            history = history.take(next.remainingHistory)
            current = next.tab
        }
        assertEquals(listOf(1, 4, 0), visited)
        assertEquals(emptyList<Int>(), history)
    }
}
