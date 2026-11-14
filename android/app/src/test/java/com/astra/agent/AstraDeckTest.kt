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

    @Test fun compactHudCompletionHandlesEmptyAndExtremeCounts() {
        assertEquals(0, AstraDeck.completionPercent(0, 0))
        assertEquals(25, AstraDeck.completionPercent(3, 1))
        assertEquals(100, AstraDeck.completionPercent(0, 12))
        assertEquals(0, AstraDeck.completionPercent(-5, -7))
        assertEquals(50, AstraDeck.completionPercent(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun shortcutsUseExistingAssistantCommands() {
        assertTrue(AstraDeck.prompts.contains("Show tasks"))
        assertTrue(AstraDeck.prompts.contains("Russian quiz"))
        assertTrue(AstraDeck.prompts.contains("Show notes"))
        assertTrue(AstraDeck.prompts.distinct().size == AstraDeck.prompts.size)
    }
}
