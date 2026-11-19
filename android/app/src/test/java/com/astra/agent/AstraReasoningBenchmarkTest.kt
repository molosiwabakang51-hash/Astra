package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraReasoningBenchmarkTest {
    @Test fun pairedBenchmarkUsesIdenticalQuestionsWithOnlyModeSwitchChanged() {
        for (probe in AstraReasoningBenchmark.Probe.entries) {
            val question = AstraReasoningBenchmark.prompt(probe)
            assertEquals(probe.question, question)
            assertFalse(AstraReasoningMode.enabled(question))
            assertTrue(AstraConversationContext.userPrompt(
                question, emptyList(), false, thinkingRequested = false
            ).endsWith("\n/no_think"))
            assertTrue(AstraConversationContext.userPrompt(
                question, emptyList(), false, thinkingRequested = true
            ).endsWith("\n/think"))
            assertEquals(1792, AstraGenerationPolicy.maxAnswerTokens(
                question, advancedModel = true, thinkingRequested = true))
            assertEquals(768, AstraGenerationPolicy.maxAnswerTokens(
                question, advancedModel = true, thinkingRequested = false))
            val cap = AstraReasoningBenchmark.COMPARISON_TOKEN_CAP
            assertEquals(768, cap)
            val fastBudget = AstraGenerationPolicy.effectiveBudget(
                AstraGenerationPolicy.maxAnswerTokens(question, true,
                    thinkingRequested = false), cap)
            val carefulBudget = AstraGenerationPolicy.effectiveBudget(
                AstraGenerationPolicy.maxAnswerTokens(question, true,
                    thinkingRequested = true), cap)
            assertEquals(cap, fastBudget)
            assertEquals(cap, carefulBudget)
        }
    }

    @Test fun logicalEntailmentRequiresAnUnambiguousAnswer() {
        val p = AstraReasoningBenchmark.Probe.ENTAILMENT
        assertTrue(AstraReasoningBenchmark.score(p, "NO."))
        assertTrue(AstraReasoningBenchmark.score(p, "<think>private</think>No"))
        for (wrong in listOf(
            "YES", "No, actually yes", "Yes, not no", "Some stones are heavy",
            "<think>No</think>", ""
        )) {
            assertFalse("Should not score: $wrong",
                AstraReasoningBenchmark.score(p, wrong))
        }
    }

    @Test fun multistepProfitRequiresCorrectSignedValue() {
        val p = AstraReasoningBenchmark.Probe.PROFIT_LOSS
        for (correct in listOf("-20", "-P20", "-20.00", "−20")) {
            assertTrue(correct, AstraReasoningBenchmark.score(p, correct))
        }
        for (wrong in listOf("20", "-40", "Loss of P20 or maybe profit",
            "It is 20, not -20", "P480", "")) {
            assertFalse(wrong, AstraReasoningBenchmark.score(p, wrong))
        }
    }

    @Test fun completeReportDisclosesBothAccuracyAndLatencyWithoutGeneralRating() {
        val logic = AstraReasoningBenchmark.Probe.ENTAILMENT
        val profit = AstraReasoningBenchmark.Probe.PROFIT_LOSS
        val a = AstraReasoningBenchmark.sample(logic, AstraReasoningBenchmark.Mode.FAST,
            "YES", 250L)
        val b = AstraReasoningBenchmark.sample(logic, AstraReasoningBenchmark.Mode.CAREFUL,
            "NO", 1500L)
        val c = AstraReasoningBenchmark.sample(profit, AstraReasoningBenchmark.Mode.FAST,
            "-20", 350L)
        val d = AstraReasoningBenchmark.sample(profit, AstraReasoningBenchmark.Mode.CAREFUL,
            "20", 2500L)
        val samples = listOf(a, b, c, d)
        assertTrue(AstraReasoningBenchmark.isComplete(samples))
        val report = AstraReasoningBenchmark.render(samples, "Qwen3 1.7B · Q4_K_M")
        assertTrue(report.contains("Fast: 1/2"))
        assertTrue(report.contains("Careful: 1/2"))
        assertTrue(report.contains("Fast average elapsed: 300 ms"))
        assertTrue(report.contains("Careful average elapsed: 2000 ms"))
        assertTrue(report.contains("1500 ms"))
        assertTrue(report.contains("Qwen3 1.7B"))
        assertTrue(report.contains("not a 10/10 rating"))
        assertFalse(report.contains("private"))
        assertEquals(1500L, b.milliseconds)
    }

    @Test fun incompleteDuplicateOrUnknownModelNeverReceivesABScore() {
        val logic = AstraReasoningBenchmark.Probe.ENTAILMENT
        val a = AstraReasoningBenchmark.sample(logic, AstraReasoningBenchmark.Mode.FAST,
            "NO", 100L)
        val b = AstraReasoningBenchmark.sample(logic, AstraReasoningBenchmark.Mode.CAREFUL,
            "NO", 500L)
        val duplicate = listOf(a, b, a, b)
        assertFalse(AstraReasoningBenchmark.isComplete(duplicate))
        val partial = listOf(a, b)
        assertFalse(AstraReasoningBenchmark.isComplete(partial))
        for (samples in listOf(duplicate, partial)) {
            val report = AstraReasoningBenchmark.render(samples, "Qwen3 1.7B · Q4_K_M")
            assertTrue(report.contains("NO SCORE"))
            assertFalse(report.contains("Fast: 2/2"))
            assertFalse(report.contains("Careful: 2/2"))
        }
        val complete = listOf(a, b,
            AstraReasoningBenchmark.sample(AstraReasoningBenchmark.Probe.PROFIT_LOSS,
                AstraReasoningBenchmark.Mode.FAST, "-20", 100L),
            AstraReasoningBenchmark.sample(AstraReasoningBenchmark.Probe.PROFIT_LOSS,
                AstraReasoningBenchmark.Mode.CAREFUL, "-20", 300L))
        assertTrue(AstraReasoningBenchmark.isComplete(complete))
        assertTrue(AstraReasoningBenchmark.render(complete, null).contains("NO SCORE"))
        assertTrue(AstraReasoningBenchmark.render(complete,
            "Qwen3 0.6B · Q4_K_M").contains("NO SCORE"))
    }

    @Test fun citedSourcesCannotSupplyAnOtherwiseWrongAnswer() {
        val probe = AstraReasoningBenchmark.Probe.ENTAILMENT
        assertTrue(AstraReasoningBenchmark.score(
            probe, "NO.\n\nOpen-course references:\n[K1] unrelated note"))
        assertFalse(AstraReasoningBenchmark.score(
            probe, "YES.\n\nOpen-course references:\n[K1] NO"))
        assertFalse(AstraReasoningBenchmark.score(
            probe, "<think>NO</think>YES"))
        assertTrue(AstraReasoningBenchmark.score(
            probe, "NO\nRetrieved local book passages (verify originals):\n[K1] YES"))
    }
}
