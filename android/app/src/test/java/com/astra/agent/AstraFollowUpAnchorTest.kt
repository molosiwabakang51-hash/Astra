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

    @Test fun arbitraryNumberedFollowUpsUseOnlySelectedItem() {
        val history = listOf(
            msg("You", "Show me several Python learning paths."),
            msg("Astra", """
                1. Variables: storing values and simple expressions.
                2. Functions: reusable pieces of logic with inputs.
                Learn how parameters and return values work.
                3. Modules: organize reusable code across files.
                4. Testing: check assumptions with repeatable assertions.
                5. Data: lists, dictionaries, and tabular examples.
            """.trimIndent())
        )
        val second = AstraFollowUpAnchor.enrich("Explain point 2", history)
        assertTrue(second.contains("USER-SELECTED ITEM 2"))
        assertTrue(second.contains("Functions: reusable pieces"))
        assertTrue(second.contains("parameters and return values"))
        assertFalse(second.contains("Testing: check assumptions"))
        val fifth = AstraFollowUpAnchor.enrich("What about item 5?", history)
        assertTrue(fifth.contains("USER-SELECTED ITEM 5"))
        assertTrue(fifth.contains("Data: lists"))
        assertFalse(fifth.contains("Functions: reusable pieces"))
        val third = AstraFollowUpAnchor.enrich("expand on step 3", history)
        assertTrue(third.contains("USER-SELECTED ITEM 3"))
        assertTrue(third.contains("Modules: organize"))
    }

    @Test fun numberedFollowUpsHandleBulletsAndNeverInventMissingItems() {
        val history = listOf(
            msg("You", "Give me a study plan."),
            msg("Astra", "- **1.** Read the chapter.\n- **2.** Solve practice problems.\n- **3.** Review mistakes.")
        )
        val item = AstraFollowUpAnchor.enrich("go into more detail for 2", history)
        assertTrue(item.contains("USER-SELECTED ITEM 2"))
        assertTrue(item.contains("Solve practice problems"))
        assertFalse(item.contains("Review mistakes"))
        val missing = AstraFollowUpAnchor.enrich("explain option 9", history)
        assertTrue(missing.contains("Item 9 was not located"))
        assertTrue(missing.contains("instead of guessing"))
    }

    @Test fun interruptedLatestAnswerNeverAnchorsToAnEarlierTopic() {
        val history = listOf(
            msg("You", "Explain leverage"),
            msg("Astra", "Leverage amplifies gains and losses."),
            msg("You", "Teach me how to calculate bond duration"),
            msg("Astra", "Bond duration uses discounted cash flows.\\n\\n[Response stopped]")
        )
        val question = "put this theory into practice"
        assertEquals(question, AstraFollowUpAnchor.enrich(question, history))
        assertEquals("explain point 2",
            AstraFollowUpAnchor.enrich("explain point 2", history))
    }

    @Test fun processKilledResponseDoesNotReuseAnOlderAssistantAnswer() {
        val history = listOf(
            msg("You", "Study English grammar"),
            msg("Astra", "English sentences have subjects and verbs."),
            msg("You", "Teach me finance"),
            msg("Astra", "The response was interrupted when Astra closed.")
        )
        assertEquals("go into more detail",
            AstraFollowUpAnchor.enrich("go into more detail", history))
    }

    @Test fun followUpDoesNotPairUnansweredRecentQuestionWithOldAnswer() {
        val history = listOf(
            msg("You", "Explain bonds"),
            msg("Astra", "Bonds pay interest."),
            msg("You", "Now teach French")
        )
        assertEquals("put it into practice",
            AstraFollowUpAnchor.enrich("put it into practice", history))
    }

    @Test fun completedNewExchangeStillAnchorsNormallyAfterInterruption() {
        val history = listOf(
            msg("You", "Explain leverage"),
            msg("Astra", "Leverage magnifies loss.\\n\\n[Response interrupted]"),
            msg("You", "Explain bonds"),
            msg("Astra", "Bonds are issued by borrowers.")
        )
        val result = AstraFollowUpAnchor.enrich(
            "go into more detail", history
        )
        assertTrue(result.contains("Explain bonds"))
        assertTrue(result.contains("Bonds are issued by borrowers"))
        assertFalse(result.contains("Leverage magnifies"))
    }

    @Test fun ordinaryQuestionsWithNumbersRemainUnmodified() {
        val history = listOf(
            msg("You", "Bond duration"),
            msg("Astra", "1. Interest rate risk.\n2. Convexity.")
        )
        for (prompt in listOf("What is 2 + 3?", "Find point 4 on the map",
            "Explain 2-factor authentication", "Expand 200 euros into pula",
            "what about 2026?")) {
            assertEquals(prompt, AstraFollowUpAnchor.enrich(prompt, history))
        }
        assertEquals("explain point 2",
            AstraFollowUpAnchor.enrich("explain point 2", emptyList()))
    }
}
