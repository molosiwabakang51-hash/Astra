package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraInferenceTimingTest {
    @Test fun recordsOnlyMonotonicNumericDurations() {
        val start = 2_000_000_000L
        val first = start + 850_000_000L
        val finished = start + 2_300_000_000L
        val sample = AstraInferenceTiming.sample(start, first, finished, 42)!!
        assertEquals(850, sample.firstNativeChunkMs)
        assertEquals(2300, sample.requestTotalMs)
        assertEquals(42, sample.nativeChunks)
        val description = AstraInferenceTiming.describe(sample)
        assertTrue(description.contains("850 ms"))
        assertTrue(description.contains("2300 ms"))
        assertTrue(description.contains("42 native output chunks"))
        assertTrue(description.contains("not tokens"))
        assertFalse(description.contains("/storage/"))
    }

    @Test fun failedOrInterruptedGenerationProducesNoSuccessRecord() {
        assertNull(AstraInferenceTiming.sample(100, null, 900, 5))
        assertNull(AstraInferenceTiming.sample(100, 101, 99, 5))
        assertNull(AstraInferenceTiming.sample(100, 101, 900, 0))
        assertNull(AstraInferenceTiming.sample(100, 99, 900, 5))
        assertEquals(
            1L, AstraInferenceTiming.sample(-2_000_000, -1_000_000, 0, 1)!!
                .firstNativeChunkMs
        )
        assertTrue(AstraInferenceTiming.describe(null).contains("No successful"))
    }

    @Test fun doesNotInventRatesFromChunkCounts() {
        val report = AstraInferenceTiming.describe(
            AstraInferenceTiming.sample(0, 2_000_000, 10_000_000, 100)
        )
        assertFalse(report.contains("tokens/second"))
        assertFalse(report.contains("tokens/sec"))
        assertTrue(report.contains("first native output"))
    }
}
