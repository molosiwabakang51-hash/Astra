package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-only phase classification; actual process death requires Android hardware. */
class AstraInferenceCheckpointTest {
    @Test fun diagnosticStagesAreExplicitAndDoNotIncludePrivateInputs() {
        assertEquals("model preparation",
            AstraInferenceCheckpoint.phaseLabel(AstraInferenceCheckpoint.PREPARING))
        assertEquals("native engine initialization",
            AstraInferenceCheckpoint.phaseLabel(AstraInferenceCheckpoint.ENGINE_SETUP))
        assertEquals("native model loading",
            AstraInferenceCheckpoint.phaseLabel(AstraInferenceCheckpoint.MODEL_LOADING))
        assertEquals("token generation",
            AstraInferenceCheckpoint.phaseLabel(AstraInferenceCheckpoint.TOKEN_GENERATION))
    }

    @Test fun unknownOrCorruptPhaseCannotBecomeUntrustedDiagnosticText() {
        val secret = "private prompt: do not log my bank details"
        assertEquals("an unknown stage", AstraInferenceCheckpoint.phaseLabel(secret))
        assertFalse(AstraInferenceCheckpoint.phaseLabel(secret).contains(secret))
        assertEquals("an unknown stage", AstraInferenceCheckpoint.phaseLabel(null))
    }

    @Test fun phaseIdentifiersAreFixedLowInformationLabels() {
        val all = listOf(
            AstraInferenceCheckpoint.PREPARING,
            AstraInferenceCheckpoint.ENGINE_SETUP,
            AstraInferenceCheckpoint.MODEL_LOADING,
            AstraInferenceCheckpoint.TOKEN_GENERATION
        )
        assertEquals(4, all.distinct().size)
        assertTrue(all.all { it.matches(Regex("[a-z_]+")) })
    }
}
