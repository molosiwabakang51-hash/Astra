package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraInferenceAttemptTest {
    @Test fun terminalTimeoutSurvivesGenericErrorAndFinally() {
        val attempt = AstraInferenceAttempt.start()
            .advance(AstraInferenceAttempt.Phase.TOKEN_GENERATION)
            .finish(AstraInferenceAttempt.Outcome.TIMEOUT, -2_000_000, 3_000_000)
        assertEquals(5L, attempt.elapsedMs)
        assertEquals(attempt, attempt.finish(AstraInferenceAttempt.Outcome.FAILED, 0, 8_000_000))
        assertEquals(attempt, attempt.finish(AstraInferenceAttempt.Outcome.NO_GENERATION, 0, 9_000_000))
        assertTrue(AstraInferenceAttempt.describe(attempt).contains("generation deadline exceeded"))
    }

    @Test fun lowMemoryAndEmptyReplyAreNotSuccessfulGeneration() {
        val low = AstraInferenceAttempt.start().advance(AstraInferenceAttempt.Phase.MODEL_LOADING)
            .finish(AstraInferenceAttempt.Outcome.LOW_MEMORY, 0, 1_000_000)
        val empty = AstraInferenceAttempt.start().advance(AstraInferenceAttempt.Phase.RESPONSE_FILTERING)
            .finish(AstraInferenceAttempt.Outcome.EMPTY_REPLY, 0, 4_000_000)
        assertTrue(AstraInferenceAttempt.describe(low).contains("memory admission refused"))
        assertTrue(AstraInferenceAttempt.describe(empty).contains("no visible reply"))
        assertFalse(AstraInferenceAttempt.describe(empty).contains("visible reply produced"))
    }

    @Test fun stagesCannotRegressAndTerminalAttemptCannotAdvance() {
        val generating = AstraInferenceAttempt.start().advance(AstraInferenceAttempt.Phase.TOKEN_GENERATION)
        assertEquals(generating, generating.advance(AstraInferenceAttempt.Phase.ENGINE_SETUP))
        val cancelled = generating.finish(AstraInferenceAttempt.Outcome.CANCELLED, 0, 100)
        assertEquals(cancelled, cancelled.advance(AstraInferenceAttempt.Phase.RESPONSE_FILTERING))
        assertNull(AstraInferenceAttempt.start().finish(AstraInferenceAttempt.Outcome.FAILED, 100, 99).elapsedMs)
        assertTrue(AstraInferenceAttempt.describe(null).contains("No native inference attempt"))
    }
}
