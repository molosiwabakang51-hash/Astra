package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraBenchmarkTrendsTest {
    private val baseline = 1794768000000L

    private fun completed(answer: String = "SECRET_PROMPT_DO_NOT_STORE") =
        AstraLanguageBenchmark.Probe.entries.mapIndexed { i, probe ->
            AstraLanguageBenchmark.Outcome(
                probe, passed = i % 2 == 0,
                milliseconds = 100L + i,
                sample = answer
            )
        }

    @Test fun keepsOnlyNumericResultsNeverRawModelTextOrChat() {
        val stored = AstraBenchmarkTrends.append(
            "", completed(), "Qwen3 1.7B · Q4_K_M", baseline
        )
        val entry = AstraBenchmarkTrends.entries(stored).single()
        assertEquals("advanced", entry.model)
        assertEquals(10, entry.total)
        assertEquals(5, entry.passed)
        assertEquals(1045L, entry.elapsedMillis)
        val report = AstraBenchmarkTrends.render(stored)
        assertTrue(report.contains("Qwen3 1.7B"))
        assertTrue(report.contains("5/10"))
        assertFalse(stored.contains("SECRET_PROMPT"))
        assertFalse(report.contains("SECRET_PROMPT"))
        assertFalse(stored.contains("Q4_K_M"))
        assertFalse(report.contains("raw model output"))
    }

    @Test fun retainsEightLatestAndPreservesModelProvenance() {
        var data = ""
        repeat(12) { index ->
            data = AstraBenchmarkTrends.append(
                data, completed(),
                if (index % 2 == 0) "Qwen3 0.6B · Q4_K_M" else null,
                baseline + index * 60_000L
            )
        }
        val history = AstraBenchmarkTrends.entries(data)
        assertEquals(8, history.size)
        assertEquals(baseline + 4 * 60_000L, history.first().epochMillis)
        assertEquals(baseline + 11 * 60_000L, history.last().epochMillis)
        assertEquals("unverified", history.last().model)
        assertTrue(AstraBenchmarkTrends.render(data).contains("Model unverified"))
    }

    @Test fun rejectsCorruptOrInjectedRecordFieldsAndNeverRendersThem() {
        val bad = "TOKEN|/data/user/0/com.astra.agent/secret|100|1|1" +
            "\n1794768000000|advanced|77|10|0" +
            "\n1794768000000|advanced|5|10|99999999" +
            "\n1794768000000|compact|0|10|200"
        val history = AstraBenchmarkTrends.entries(bad)
        assertEquals(1, history.size)
        assertEquals(0, history.single().passed)
        val report = AstraBenchmarkTrends.render(bad)
        assertFalse(report.contains("TOKEN"))
        assertFalse(report.contains("/data/"))
        assertTrue(report.contains("0/10"))
    }

    @Test fun partialOrDuplicateProbeRunsCannotBePersistedAsComplete() {
        for (bad in listOf(
            completed().dropLast(1),
            completed().dropLast(1) + completed().first()
        )) {
            var rejected = false
            try {
                AstraBenchmarkTrends.append("", bad, null, baseline)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
        }
    }

    @Test fun emptyHistoryReportsNoMeasuredScore() {
        assertTrue(AstraBenchmarkTrends.render("").contains("No completed"))
        assertTrue(AstraBenchmarkTrends.entries("garbled").isEmpty())
    }
}
