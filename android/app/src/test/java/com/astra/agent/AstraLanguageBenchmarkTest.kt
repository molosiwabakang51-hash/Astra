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

    @Test fun errorOutputsNeverCountAsSuccessfulUnderstanding() {
        for (probe in AstraLanguageBenchmark.Probe.entries) {
            assertFalse(AstraLanguageBenchmark.score(probe, ""))
            assertFalse(AstraLanguageBenchmark.score(probe,
                "Offline model unavailable: import a GGUF file."))
        }
    }
}
