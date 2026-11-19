package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AstraModelChoiceTest {
    @Test fun advancedModelIsTheOnlySupportedConversationEngine() {
        assertEquals(AstraModelChoice.Variant.ADVANCED,
            AstraModelChoice.choose(advancedAvailable = true))
        assertEquals(listOf(AstraModelChoice.Variant.ADVANCED),
            AstraModelChoice.candidates(advancedAvailable = true))
    }

    @Test fun absentAdvancedModelFailsClosedRatherThanUsingCompactWeights() {
        assertEquals(AstraModelChoice.Variant.NONE,
            AstraModelChoice.choose(advancedAvailable = false))
        assertEquals(emptyList<AstraModelChoice.Variant>(),
            AstraModelChoice.candidates(advancedAvailable = false))
    }

    @Test fun labelNeverPresentsLegacyCompactAsAConversationEngine() {
        assertEquals("Qwen3 1.7B · Q4_K_M",
            AstraModelChoice.loadedName("/private/Qwen3-1.7B-Q4_K_M.gguf"))
        assertEquals(null,
            AstraModelChoice.loadedName("/private/qwen3-0.6b-q4km.gguf"))
        assertEquals(null, AstraModelChoice.loadedName(null))
        assertEquals(null, AstraModelChoice.loadedName("/private/unknown.bin"))
    }
}
