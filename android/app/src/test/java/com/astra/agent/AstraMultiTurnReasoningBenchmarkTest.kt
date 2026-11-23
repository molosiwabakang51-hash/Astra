package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AstraMultiTurnReasoningBenchmarkTest {
    @Test fun syntheticCasesAreDistinctAndRequireThreeTurns() {
        assertEquals(4, AstraMultiTurnReasoningBenchmark.scenarios.size)
        assertEquals(4, AstraMultiTurnReasoningBenchmark.scenarios.map {
            it.title
        }.distinct().size)
        assertTrue(AstraMultiTurnReasoningBenchmark.scenarios.all {
            it.prompts.size == 3 &&
                it.prompts.all { prompt -> prompt.isNotBlank() }
        })
    }

    @Test fun scoredFinalAnswersAreStrictAndDoNotRewardNarrativeGuesses() {
        for (case in AstraMultiTurnReasoningBenchmark.scenarios) {
            val expected = case.expected
            assertTrue(case.title,
                AstraMultiTurnReasoningBenchmark.score(case, expected))
            assertFalse(case.title,
                AstraMultiTurnReasoningBenchmark.score(
                    case, "The answer is " + expected
                ))
            assertFalse(case.title,
                AstraMultiTurnReasoningBenchmark.score(
                    case, "I think " + expected + " but maybe not."
                ))
            assertFalse(case.title,
                AstraMultiTurnReasoningBenchmark.score(
                    case, "<think>guessed</think>ACK"
                ))
        }
        val numeric = AstraMultiTurnReasoningBenchmark.scenarios.first()
        assertTrue(AstraMultiTurnReasoningBenchmark.score(numeric, "3.0"))
        assertFalse(AstraMultiTurnReasoningBenchmark.score(numeric, "7"))
    }

    @Test fun strictReportDoesNotExposeResponsesOrUnfinishedRuns() {
        val cases = AstraMultiTurnReasoningBenchmark.scenarios
        val outcomes = cases.mapIndexed { index, case ->
            AstraMultiTurnReasoningBenchmark.Outcome(
                case, index < 2, 100L + index
            )
        }
        val report = AstraMultiTurnReasoningBenchmark.render(
            outcomes, "Verified Qwen3 1.7B"
        )
        assertTrue(report.contains("2/4 strict final-answer checks"))
        assertTrue(report.contains("No private source data"))
        assertTrue(report.contains("earn no 100-point"))
        assertFalse(report.contains("guessed"))
        assertFalse(report.contains("ALPHA sells shoes"))
        try {
            AstraMultiTurnReasoningBenchmark.render(outcomes.take(2), null)
            fail("Incomplete multi-turn run must not claim a complete score.")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun cannotScoreDuplicateOrNonSyntheticCaseSet() {
        val scenario = AstraMultiTurnReasoningBenchmark.scenarios.first()
        val repeats = List(4) {
            AstraMultiTurnReasoningBenchmark.Outcome(scenario, true, 1)
        }
        try {
            AstraMultiTurnReasoningBenchmark.render(repeats, "Qwen3")
            fail("Duplicate scenario must not inflate the check count.")
        } catch (_: IllegalArgumentException) {}
    }
}
