package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AstraModelChoiceTest {
    @Test fun advancedModelWinsEvenWhenCompactIsInstalled() {
        assertEquals(
            AstraModelChoice.Variant.ADVANCED,
            AstraModelChoice.choose(advancedAvailable = true, compactAvailable = true)
        )
    }

    @Test fun compactModelIsRecoveryFallback() {
        assertEquals(
            AstraModelChoice.Variant.COMPACT,
            AstraModelChoice.choose(advancedAvailable = false, compactAvailable = true)
        )
    }

    @Test fun advancedModelWorksWithoutCompactWeights() {
        assertEquals(
            AstraModelChoice.Variant.ADVANCED,
            AstraModelChoice.choose(advancedAvailable = true, compactAvailable = false)
        )
    }

    @Test fun exposesRealModelNameWithoutPrivatePath() {
        assertEquals("Qwen3 1.7B · Q4_K_M",
            AstraModelChoice.loadedName("/data/user/0/com.astra.agent/files/Qwen3-1.7B-Q4_K_M.gguf"))
        assertEquals("Qwen3 0.6B · Q4_K_M",
            AstraModelChoice.loadedName("/private/qwen3-0.6b-q4km.gguf"))
        assertEquals(null, AstraModelChoice.loadedName(null))
        assertEquals(null, AstraModelChoice.loadedName("/private/unknown.bin"))
    }

    @Test fun noWeightsNeverPretendThatInferenceIsAvailable() {
        assertEquals(
            AstraModelChoice.Variant.NONE,
            AstraModelChoice.choose(advancedAvailable = false, compactAvailable = false)
        )
    }
    @Test fun preferredModelIsFollowedByOfflineRecoveryCandidate() {
        assertEquals(
            listOf(AstraModelChoice.Variant.ADVANCED, AstraModelChoice.Variant.COMPACT),
            AstraModelChoice.candidates(advancedAvailable = true, compactAvailable = true)
        )
    }

    @Test fun candidateListNeverContainsUnavailableModels() {
        assertEquals(
            listOf(AstraModelChoice.Variant.COMPACT),
            AstraModelChoice.candidates(advancedAvailable = false, compactAvailable = true)
        )
        assertEquals(
            listOf(AstraModelChoice.Variant.ADVANCED),
            AstraModelChoice.candidates(advancedAvailable = true, compactAvailable = false)
        )
        assertEquals(
            emptyList<AstraModelChoice.Variant>(),
            AstraModelChoice.candidates(advancedAvailable = false, compactAvailable = false)
        )
    }
}
