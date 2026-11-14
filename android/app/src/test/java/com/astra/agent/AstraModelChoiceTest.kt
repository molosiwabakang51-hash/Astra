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

    @Test fun noWeightsNeverPretendThatInferenceIsAvailable() {
        assertEquals(
            AstraModelChoice.Variant.NONE,
            AstraModelChoice.choose(advancedAvailable = false, compactAvailable = false)
        )
    }
}
