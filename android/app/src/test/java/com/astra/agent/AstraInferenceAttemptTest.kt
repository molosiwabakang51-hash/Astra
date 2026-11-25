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

    @Test fun cappedResponseIsIncompleteEvenWithVisibleText() {
        val result = AstraInterruptedReply.budgetLimited("The first step is")
        assertTrue(result.contains("The first step is"))
        assertTrue(AstraInterruptedReply.isIncomplete(result))
        assertTrue(AstraInterruptedReply.isIncomplete(AstraInterruptedReply.budgetLimited("")))
        assertFalse(AstraInterruptedReply.isIncomplete("The first step is complete."))
        val limit = AstraInferenceAttempt.start()
            .finish(AstraInferenceAttempt.Outcome.TOKEN_LIMIT, 0, 100)
        assertTrue(AstraInferenceAttempt.describe(limit).contains("incomplete response"))
        assertFalse(AstraInferenceAttempt.describe(limit).contains("visible reply produced"))
    }

    @Test fun noBenchmarkCreditsCorrectButIncompleteNativeReplies() {
        fun capped(answer: String) = AstraInterruptedReply.budgetLimited(answer)
        assertFalse(AstraLanguageBenchmark.score(AstraLanguageBenchmark.Probe.ARITHMETIC, capped("42")))
        assertFalse(AstraReasoningBenchmark.score(AstraReasoningBenchmark.Probe.LOGICAL_DEDUCTION, capped("NO")))
        val scenario = AstraMultiTurnReasoningBenchmark.scenarios.first()
        assertFalse(AstraMultiTurnReasoningBenchmark.interimMatches(scenario, 0, capped("ACK")))
        assertFalse(AstraMultiTurnReasoningBenchmark.score(scenario, capped("3")))
        // A source footer must never conceal an incomplete-generation marker.
        assertFalse(AstraReasoningBenchmark.score(AstraReasoningBenchmark.Probe.LOGICAL_DEDUCTION,
            capped("NO\n\nOpen-course references:\nexample")))
    }
}
