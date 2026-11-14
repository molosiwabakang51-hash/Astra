package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraDeckTest {
    @Test fun statusReflectsRealState() {
        assertEquals("LOCAL SYSTEM READY", AstraDeck.status(false, false))
        assertEquals("VOICE INPUT ACTIVE", AstraDeck.status(true, false))
        assertEquals("RESEARCH IN PROGRESS", AstraDeck.status(false, true))
        assertEquals("VOICE INPUT ACTIVE", AstraDeck.status(true, true))
        assertEquals("AWAITING YOUR RESPONSE", AstraDeck.status(false, false, true))
        assertEquals("RESEARCH IN PROGRESS", AstraDeck.status(false, true, true))
        assertEquals("VOICE INPUT ACTIVE", AstraDeck.status(true, true, true))
    }

    @Test fun shortcutsUseExistingAssistantCommands() {
        assertTrue(AstraDeck.prompts.contains("Show tasks"))
        assertTrue(AstraDeck.prompts.contains("Russian quiz"))
        assertTrue(AstraDeck.prompts.contains("Show notes"))
        assertTrue(AstraDeck.prompts.distinct().size == AstraDeck.prompts.size)
    }
}
