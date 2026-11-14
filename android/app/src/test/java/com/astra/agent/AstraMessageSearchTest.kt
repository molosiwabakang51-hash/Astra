package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraMessageSearchTest {
    @Test fun matchesAllTokensIgnoringCaseAndExtraSpaces() {
        val messages = listOf(
            "How do I calculate Bond Duration?",
            "What is convexity?",
            "DURATION is important for a BOND."
        )
        assertEquals(listOf(0, 2), AstraMessageSearch.find(messages, "  bond   DURATION  "))
        assertEquals(listOf(1), AstraMessageSearch.find(messages, "CONVEXITY"))
    }

    @Test fun blankAndNonmatchingQueriesHaveNoHits() {
        val messages = listOf("Astra", "Important notes")
        assertTrue(AstraMessageSearch.find(messages, "").isEmpty())
        assertTrue(AstraMessageSearch.find(messages, " \n \t ").isEmpty())
        assertTrue(AstraMessageSearch.find(messages, "unknown").isEmpty())
    }

    @Test fun searchPreservesIndicesAndInputOrder() {
        val messages = listOf("first task", "a bond", "another bond", "last task")
        assertEquals(listOf(1, 2), AstraMessageSearch.find(messages, "bond"))
        assertEquals(
            listOf("first task", "a bond", "another bond", "last task"),
            messages
        )
    }

    @Test fun unicodeSearchWorksWithoutChangingMessageText() {
        val messages = listOf("Hello", "Спасибо", "Ke a leboga")
        assertEquals(listOf(1), AstraMessageSearch.find(messages, "спасибо"))
        assertEquals(listOf(2), AstraMessageSearch.find(messages, "LEBOGA"))
    }

    @Test fun excerptKeepsRelevantPartOfLongMessage() {
        val source = "a".repeat(250) + " Bond Duration " + "b".repeat(250)
        val excerpt = AstraMessageSearch.excerpt(source, "Duration", 80)
        assertTrue(excerpt.contains("Duration"))
        assertTrue(excerpt.startsWith("…"))
        assertTrue(excerpt.endsWith("…"))
        assertTrue(excerpt.length <= 82)
    }

    @Test fun shortExcerptFlattensWhitespaceWithoutTruncating() {
        assertEquals(
            "Bond duration is useful",
            AstraMessageSearch.excerpt("Bond \n duration \t is useful", "bond")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun excerptRejectsUnreasonablySmallLimit() {
        AstraMessageSearch.excerpt("example", "ex", 5)
    }
}
