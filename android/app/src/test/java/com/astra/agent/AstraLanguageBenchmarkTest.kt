package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraLanguageBenchmarkTest {
    @Test fun arithmeticProbeRejectsIncorrectOrNonAnswer() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "42"))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "The answer is 42."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "43"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC,
            "The answer is 43, not 42."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "The offline model did not produce a usable answer."))
    }

    @Test fun citedSourcesCannotMakeAnIncorrectModelAnswerPass() {
        val misleading = "I cannot calculate that.\n\nOpen-course references:\n" +
            "[K1] A source that mentions 42."
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, misleading))
        val actualAnswer = "42.\n\nOpen-course references:\n[K1] Context"
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, actualAnswer))
    }

    @Test fun RussianTranslationSupportsCommonEnglishExpressions() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Thank you!"))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Thanks."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Hello."))
    }

    @Test fun multiTurnProbeNeedsRealCodewordRetention() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.CONTEXT, "NIMBORA"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.CONTEXT, "I cannot recall that."))
    }

    @Test fun GrammarProbeNeedsTwoDistinctCorrectCases() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.GRAMMAR,
            "Russian has nominative and genitive cases."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.GRAMMAR, "Nominative is one case."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.GRAMMAR, "Russian language has many cases."))
    }

    @Test fun reportIsConservativeAndIdentifiesActualModel() {
        val result = AstraLanguageBenchmark.outcome(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Thanks", 125L)
        assertTrue(result.passed)
        assertEquals(125L, result.milliseconds)
        val report = AstraLanguageBenchmark.render(listOf(result), "Qwen3 1.7B · Q4_K_M")
        assertTrue(report.contains("1/1"))
        assertTrue(report.contains("Qwen3 1.7B"))
        assertTrue(report.contains("not proof"))
        assertFalse(report.contains("Full training complete"))
    }

    @Test fun compoundInterestProbeChecksExactThreeYearGrowth() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.COMPOUND_INTEREST, "P1,331.00"))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.COMPOUND_INTEREST, "1331"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.COMPOUND_INTEREST, "P1,300"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.COMPOUND_INTEREST, "P1,330, not P1,331"))
    }

    @Test fun deductionProbeRejectsContradictoryNarrative() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.DEDUCTION, "No."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.DEDUCTION, "Yes"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.DEDUCTION, "Yes, but maybe no."))
    }

    @Test fun setswanaProbeAcceptsActualShortTranslationOnly() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.SETSWANA, "Hello."))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.SETSWANA, "Hi"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.SETSWANA, "Goodbye"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.SETSWANA, "Dumela does not mean hello"))
    }

    @Test fun negativeOrQuotedAnswersDoNotInflateTranslationOrMemoryScore() {
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Not thank you."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.CONTEXT, "I forgot NIMBORA"))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.CONTEXT, "\"NIMBORA\""))
    }

    @Test fun expandedReportHasSevenDistinctProbeSlots() {
        assertEquals(7, AstraLanguageBenchmark.Probe.entries.size)
        val outcomes = AstraLanguageBenchmark.Probe.entries.map { probe ->
            AstraLanguageBenchmark.outcome(probe, "", 100L)
        }
        val report = AstraLanguageBenchmark.render(outcomes, null)
        assertTrue(report.contains("0/7"))
        assertTrue(report.contains("Compound interest"))
        assertTrue(report.contains("Logical deduction"))
        assertTrue(report.contains("Setswana greeting"))
    }

    @Test fun errorOutputsNeverCountAsSuccessfulUnderstanding() {
        for (probe in AstraLanguageBenchmark.Probe.entries) {
            assertFalse(AstraLanguageBenchmark.score(probe, ""))
            assertFalse(AstraLanguageBenchmark.score(probe,
                "Offline model unavailable: import a GGUF file."))
        }
    }
}
