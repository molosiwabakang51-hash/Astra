package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraDownloadRangeTest {
    @Test fun acceptingFullResponseStartsFromZeroEvenWithOldPartial() {
        val plan = AstraDownloadRange.plan(200, 50, null, 150)
        assertFalse(plan.append)
        assertEquals(150L, plan.totalBytes)
    }

    @Test fun correctPartialContentRangeResumesWithoutDuplication() {
        val plan = AstraDownloadRange.plan(206, 50, "bytes 50-149/150", 100)
        assertTrue(plan.append)
        assertEquals(150L, plan.totalBytes)
    }

    @Test fun mismatchedRangeIsRejectedBeforeWriting() {
        val outcome = runCatching {
            AstraDownloadRange.plan(206, 50, "bytes 0-99/150", 100)
        }
        assertTrue(outcome.isFailure)
    }

    @Test fun mismatchedRangeLengthIsRejected() {
        val outcome = runCatching {
            AstraDownloadRange.plan(206, 50, "bytes 50-149/150", 80)
        }
        assertTrue(outcome.isFailure)
    }

    @Test fun missingOrUnexpectedResponsesFailClosed() {
        assertTrue(runCatching {
            AstraDownloadRange.plan(200, 0, null, -1)
        }.isFailure)
        assertTrue(runCatching {
            AstraDownloadRange.plan(302, 0, null, 100)
        }.isFailure)
        assertTrue(runCatching {
            AstraDownloadRange.plan(206, 10, "not-a-range", 100)
        }.isFailure)
    }
}
