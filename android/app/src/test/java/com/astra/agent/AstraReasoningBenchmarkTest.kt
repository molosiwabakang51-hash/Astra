package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraReasoningBenchmarkTest {
    @Test fun allEightProbesHaveDistinctQuestionsAndNoEmbeddedAnswers() {
        val all = AstraReasoningBenchmark.Probe.entries
        assertEquals(8, all.size)
        assertEquals(8, all.map { it.prompt }.distinct().size)
        all.forEach {
            assertTrue(it.prompt.length in 40..400)
            assertFalse(it.prompt.contains("expected", ignoreCase = true))
        }
    }

    @Test fun exactNumericalResultsPassWithOrdinaryUnits() {
        val B = AstraReasoningBenchmark
        assertTrue(B.score(AstraReasoningBenchmark.Probe.WEIGHTED_RETURN, "6%"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.COMPOUNDING, "P2,332.80"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.COMPOUNDING, "2332.8"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.BOND_PRESENT_VALUE, "P 95.24"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.PROJECT_NPV, "4.13"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.PROBABILITY, "30%"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.ALGEBRA, "8"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.LOGICAL_DEDUCTION, "No."))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.FOREIGN_EXCHANGE_RISK, "NO"))
    }

    @Test fun wrongOrUnrequestedPrecisionDoesNotPass() {
        val B = AstraReasoningBenchmark
        assertFalse(B.score(AstraReasoningBenchmark.Probe.WEIGHTED_RETURN, "6"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.COMPOUNDING, "2,332.81"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.BOND_PRESENT_VALUE, "95.23"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.PROJECT_NPV, "4.14"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.PROBABILITY, "30"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.ALGEBRA, "9"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.FOREIGN_EXCHANGE_RISK, "YES"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.LOGICAL_DEDUCTION, "Yes, but maybe no"))
    }

    @Test fun citationsAndQuotedFalseStatementsCannotSupplyAnswers() {
        val B = AstraReasoningBenchmark
        assertFalse(B.score(AstraReasoningBenchmark.Probe.WEIGHTED_RETURN,
            "I cannot determine this.\n\nOpen-course references:\n[K1] 6%"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.FOREIGN_EXCHANGE_RISK,
            "YES\n\nRetrieved local book passages\n[K1] No"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.WEIGHTED_RETURN, "Not 6%."))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.WEIGHTED_RETURN, "6% or 7%"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.WEIGHTED_RETURN, "\"6%\""))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.WEIGHTED_RETURN,
            "6%\n\nOpen-course references:\n[K1] unrelated"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.WEIGHTED_RETURN,
            "6%\n\nRetrieved local book passages (verify originals):\n[K1] irrelevant"))
    }

    @Test fun privateThinkingTokensCannotInflateNumbersOrLeakInReport() {
        val B = AstraReasoningBenchmark
        assertFalse(B.score(AstraReasoningBenchmark.Probe.ALGEBRA,
            "<think>The answer could be 8.</think>9"))
        assertTrue(B.score(AstraReasoningBenchmark.Probe.ALGEBRA,
            "<think>Deliberation omitted</think>8"))
        assertFalse(B.score(AstraReasoningBenchmark.Probe.ALGEBRA,
            "<think>The answer is 8"))
        val outcome = B.outcome(AstraReasoningBenchmark.Probe.ALGEBRA,
            "<think>private explanation</think>8", 5L)
        assertEquals("8", outcome.sample)
        assertTrue(outcome.passed)
    }

    @Test fun reportRefusesDuplicateProbeDataAndMakesNoQualityPromise() {
        val B = AstraReasoningBenchmark
        val probe = AstraReasoningBenchmark.Probe.ALGEBRA
        val yes = B.outcome(probe, "8", 120L)
        assertTrue(yes.passed)
        val duplicated = runCatching {
            B.render(listOf(yes, yes), "Qwen3 1.7B")
        }.exceptionOrNull()
        assertTrue(duplicated is IllegalArgumentException)
        val report = B.render(listOf(yes), "Qwen3 1.7B")
        assertTrue(report.contains("1/1 narrowly scored"))
        assertTrue(report.contains("not establish general reasoning"))
        assertFalse(report.contains("private explanation"))
        assertFalse(report.contains("10/10 AI rating achieved"))
    }
}
