package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraFollowUpAnchorTest {
    private fun msg(role: String, text: String) =
        AstraChatArchive.Message(role, text)

    @Test fun bondsFollowUpAnchorsNewestExchangeNotEarlierLeverage() {
        val history = listOf(
            msg("You", "What is leverage?"),
            msg("Astra", "Leverage can magnify exposure and losses."),
            msg("You", "What are bonds and how can I invest in American bonds while in Africa?"),
            msg("Astra", "To access U.S. Treasury bonds, verify a licensed international broker and currency risks.")
        )
        val prompt = AstraFollowUpAnchor.enrich("put this theory into practice", history)
        assertTrue(prompt.contains("Previous user question"))
        assertTrue(prompt.contains("American bonds while in Africa"))
        assertTrue(prompt.contains("U.S. Treasury bonds"))
        assertTrue(prompt.contains("CURRENT USER FOLLOW-UP: put this theory into practice"))
        assertFalse(prompt.contains("Leverage"))
        assertTrue(prompt.length < 1100)
    }

    @Test fun numericFollowUpSelectsFourthItemBeyondTruncatedSummary() {
        val outline = """
            Overview of financial management: detailed introduction to money decisions.
            1. Investing: allocating assets to expected long-run opportunities.
            Investing can be evaluated using time horizons, costs and risk limits.
            2. Personal finance: arranging savings, liquidity and responsible debt.
            Budgets track income, spending, obligations and emergency reserves.
            3. Corporate finance: selecting investment projects and funding.
            Working capital, cost of capital and governance affect firm value.
            4. Financial Markets: currencies, equity indices, bonds and derivatives.
            This includes the mechanics of liquidity, futures and market structure.
        """.trimIndent()
        val history = listOf(
            msg("You", "Teach me finance"),
            msg("Astra", outline)
        )
        val focused = AstraFollowUpAnchor.enrich("go into more detail for 4", history)
        assertTrue(focused.contains("USER-SELECTED ITEM 4"))
        assertTrue(focused.contains("Financial Markets: currencies"))
        assertTrue(focused.contains("mechanics of liquidity"))
        assertTrue(focused.contains("Explain specifically item 4"))
        assertFalse(focused.contains("Leverage can magnify exposure"))
    }

    @Test fun missingNumberedItemIsNeverInvented() {
        val history = listOf(
            msg("You", "Explain bonds"),
            msg("Astra", "Bonds are debt agreements with coupons and principal.")
        )
        val focused = AstraFollowUpAnchor.enrich("go into more detail for 4", history)
        assertTrue(focused.contains("Item 4 was not located"))
        assertTrue(focused.contains("instead of guessing"))
    }

    @Test fun unambiguousQueriesRemainUntouched() {
        val history = listOf(msg("You", "Hello"), msg("Astra", "Hello there"))
        assertEquals("What is bond duration?",
            AstraFollowUpAnchor.enrich("What is bond duration?", history))
        assertEquals("put this theory into practice",
            AstraFollowUpAnchor.enrich("put this theory into practice", emptyList()))
        assertEquals("put it into practice",
            AstraFollowUpAnchor.enrich("put it into practice",
                listOf(msg("You", "Hello"))))
    }

    @Test fun anchoredTextNeverIncludesArchivedOrOtherChatContent() {
        val history = listOf(
            msg("You", "Explain French grammar"),
            msg("Astra", "French grammar includes nouns and verb agreement.")
        )
        val prompt = AstraFollowUpAnchor.enrich("go into more detail", history)
        assertTrue(prompt.contains("French grammar"))
        assertFalse(prompt.contains("Shiller"))
        assertFalse(prompt.contains("Niall"))
    }
}
