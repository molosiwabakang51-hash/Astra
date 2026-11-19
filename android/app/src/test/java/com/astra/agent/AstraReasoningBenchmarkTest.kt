package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraReasoningBenchmarkTest {
    @Test fun onlyExplicitCarefulModeSelectsNativeThinking() {
        for (probe in AstraReasoningBenchmark.Probe.entries) {
            val quick = AstraReasoningBenchmark.prompt(probe, AstraReasoningBenchmark.Mode.FAST)
            val careful = AstraReasoningBenchmark.prompt(
                probe, AstraReasoningBenchmark.Mode.CAREFUL)
            assertFalse(AstraReasoningMode.enabled(quick))
            assertTrue(AstraReasoningMode.enabled(careful))
            assertTrue(AstraConversationContext.userPrompt(
                quick, emptyList(), false).endsWith("\n/no_think"))
            assertTrue(AstraConversationContext.userPrompt(
                careful, emptyList(), false).endsWith("\n/think"))
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

    @Test fun reportDisclosesRealModelAndNeverClaimsGeneralIntelligenceScore() {
        val p = AstraReasoningBenchmark.Probe.ENTAILMENT
        val a = AstraReasoningBenchmark.sample(p, AstraReasoningBenchmark.Mode.FAST,
            "YES", 250L)
        val b = AstraReasoningBenchmark.sample(p, AstraReasoningBenchmark.Mode.CAREFUL,
            "NO", 1500L)
        val report = AstraReasoningBenchmark.render(listOf(a, b), "Qwen3 1.7B · Q4_K_M")
        assertTrue(report.contains("Fast: 0/1"))
        assertTrue(report.contains("Careful: 1/1"))
        assertTrue(report.contains("1500 ms"))
        assertTrue(report.contains("Qwen3 1.7B"))
        assertTrue(report.contains("not a 10/10 rating"))
        assertFalse(report.contains("private"))
        assertEquals(1500L, b.milliseconds)
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
