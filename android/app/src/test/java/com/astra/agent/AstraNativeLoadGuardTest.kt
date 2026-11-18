package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraNativeLoadGuardTest {
    @Test fun androidReportedLowMemoryAlwaysDefersANewNativeLoad() {
        assertTrue(AstraNativeLoadGuard.shouldDefer(true, 6_000_000_000L, 100_000_000L))
        assertTrue(AstraNativeLoadGuard.shouldDefer(true, -1L, -1L))
    }

    @Test fun belowDeviceSpecificThresholdDefersEvenIfFlagLags() {
        assertTrue(AstraNativeLoadGuard.shouldDefer(false, 199L, 200L))
        assertTrue(AstraNativeLoadGuard.shouldDefer(false, 0L, 200L))
    }

    @Test fun normalAndUnknownMemoryMetricsDoNotBlockTheOwner() {
        assertFalse(AstraNativeLoadGuard.shouldDefer(false, 200L, 200L))
        assertFalse(AstraNativeLoadGuard.shouldDefer(false, 201L, 200L))
        assertFalse(AstraNativeLoadGuard.shouldDefer(false, -1L, 200L))
        assertFalse(AstraNativeLoadGuard.shouldDefer(false, 300L, -1L))
        assertFalse(AstraNativeLoadGuard.shouldDefer(false, 300L, 0L))
    }

    @Test fun failureMessageIsActionableAndDoesNotExposeDeviceIdentifiers() {
        val advice = AstraNativeLoadGuard.RETRY_ADVICE
        assertTrue(advice.contains("Close other apps"))
        assertTrue(advice.contains("model and conversations"))
        assertFalse(advice.contains("/data/"))
        assertFalse(advice.contains("phone model"))
    }
}
