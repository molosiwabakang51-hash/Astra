package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Captures reported handset failures without network, model weights, or Android UI. */
class AstraTranscriptRegressionTest {
    @Test fun simpleHelpDoesNotFloodConversationWithCommandManual() {
        val reply = AstraHelp.concise()
        assertTrue(reply.contains("russian"))
        assertTrue(reply.contains("commands"))
        assertTrue(reply.length < 450)
        assertTrue(!reply.contains("LOCAL MEMORY"))
        assertTrue(!reply.contains("MISSION CONTROL"))
    }

    @Test fun shorthandRussianLaunchesLessonLocally() {
        val tutor = RussianTutor({ _, default -> default }, { _, _ -> })
        assertTrue(tutor.respond("russian")!!.contains("Привет"))
        assertTrue(tutor.respond("russian language")!!.contains("Спасибо"))
    }

    @Test fun shoppingListWorksAcrossReportedChatSequence() {
        val state = mutableMapOf<String, String>()
        val assistant = EverydayAssistant(
            { key, default -> state[key] ?: default },
            { key, value -> state[key] = value },
            { "2026-11-14" }
        )
        assertEquals("Added to shopping list: milk.",
            assistant.respond("add milk to shopping list", emptyList()))
        assertTrue(assistant.respond("shopping list", emptyList())!!.contains("1. milk"))
    }

    @Test fun learningEverythingNeverBecomesUnrelatedWikipediaResults() {
        assertNull(ResearchCommand.extract("research and learn about everything"))
        assertNull(ResearchCommand.extract("research all knowledge"))
        assertEquals("the solar system", ResearchCommand.extract("research the solar system"))
    }

    @Test fun noModelsAreClaimedInstalledWhenNoneExist() {
        assertEquals(AstraModelChoice.Variant.NONE,
            AstraModelChoice.choose(advancedAvailable = false, compactAvailable = false))
        assertEquals(AstraModelChoice.Variant.ADVANCED,
            AstraModelChoice.choose(advancedAvailable = true, compactAvailable = true))
    }
}
