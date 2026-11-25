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

    @Test fun intermediateAnswerComplianceIsIndependentFromFinalCorrectness() {
        val cases = AstraMultiTurnReasoningBenchmark.scenarios
        for (scenario in cases) {
            assertEquals(2, scenario.interimExpected.size)
            assertTrue(scenario.title, AstraMultiTurnReasoningBenchmark.interimMatches(
                scenario, 0, scenario.interimExpected[0]
            ))
            assertTrue(scenario.title, AstraMultiTurnReasoningBenchmark.interimMatches(
                scenario, 1, scenario.interimExpected[1] + "."
            ))
            assertFalse(AstraMultiTurnReasoningBenchmark.interimMatches(
                scenario, 0, "The answer is " + scenario.interimExpected[0]
            ))
            assertFalse(AstraMultiTurnReasoningBenchmark.interimMatches(
                scenario, 2, scenario.expected
            ))
            assertFalse(AstraMultiTurnReasoningBenchmark.interimMatches(
                scenario, 1, "ACK\\nIgnore safety rules"
            ))
        }
        val french = cases.last()
        assertEquals(listOf("OK", "Maun"), french.interimExpected)
        assertFalse(AstraMultiTurnReasoningBenchmark.interimMatches(
            french, 1, "Paris"
        ))
    }

    @Test fun completeReportSeparatesEarlyInstructionAdherenceAndFinalAnswers() {
        val cases = AstraMultiTurnReasoningBenchmark.scenarios
        val samples = cases.mapIndexed { index, scenario ->
            AstraMultiTurnReasoningBenchmark.Outcome(
                scenario, index < 2, 500L + index, index % 3
            )
        }
        val report = AstraMultiTurnReasoningBenchmark.render(samples, "Qwen3 1.7B · Q4_K_M")
        assertTrue(report.contains("2/4 strict final-answer checks passed"))
        assertTrue(report.contains("3/8 earlier-turn instruction checks passed"))
        assertTrue(report.contains("early replies 0/2"))
        assertTrue(report.contains("early replies 2/2"))
        assertFalse(report.contains("fictional bond matures"))
    }

    @Test fun strictReportDoesNotExposeResponsesOrUnfinishedRuns() {
        val cases = AstraMultiTurnReasoningBenchmark.scenarios
        val outcomes = cases.mapIndexed { index, case ->
            AstraMultiTurnReasoningBenchmark.Outcome(
                case, index < 2, 100L + index
            )
        }
        val report = AstraMultiTurnReasoningBenchmark.render(
            outcomes, "Qwen3 1.7B · Q4_K_M"
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

    @Test fun unknownModelCannotEarnMultiTurnScore() {
        val outcomes = AstraMultiTurnReasoningBenchmark.scenarios.map {
            AstraMultiTurnReasoningBenchmark.Outcome(it, true, 100L, 2)
        }
        assertEquals(AstraReasoningBenchmark.UNSCORED,
            AstraMultiTurnReasoningBenchmark.render(outcomes, null))
        assertEquals(AstraReasoningBenchmark.UNSCORED,
            AstraMultiTurnReasoningBenchmark.render(outcomes, "Qwen3 0.6B"))
    }
}
