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

    @Test fun rejectedCompletedRangeIsCheckedBeforeDiscardingSavedModel() {
        val decision = AstraDownloadRange.rejectedResume(
            416, 1_280_000_000L, 1_150_000_000L, 1_350_000_000L
        )
        assertEquals(AstraDownloadRange.RejectedResume.VERIFY_COMPLETE, decision)
        assertEquals(
            AstraDownloadRange.RejectedResume.VERIFY_COMPLETE,
            AstraDownloadRange.rejectedResume(
                416, 1_150_000_000L, 1_150_000_000L, 1_350_000_000L
            )
        )
        assertEquals(
            AstraDownloadRange.RejectedResume.VERIFY_COMPLETE,
            AstraDownloadRange.rejectedResume(
                416, 1_350_000_000L, 1_150_000_000L, 1_350_000_000L
            )
        )
    }

    @Test fun rejectedIncompleteOrOversizedRangeMustNotRetrySameOffset() {
        val decision = AstraDownloadRange.RejectedResume.DISCARD_SAVED_PARTIAL
        assertEquals(decision, AstraDownloadRange.rejectedResume(
            416, 440_000_000L, 1_150_000_000L, 1_350_000_000L
        ))
        assertEquals(decision, AstraDownloadRange.rejectedResume(
            416, 1_350_000_001L, 1_150_000_000L, 1_350_000_000L
        ))
        assertEquals(decision, AstraDownloadRange.rejectedResume(
            416, 1L, 1_150_000_000L, 1_350_000_000L
        ))
    }

    @Test fun normalResponsesOrFreshDownloadNeverDiscardSavedPartialByMistake() {
        val none = AstraDownloadRange.RejectedResume.NONE
        assertEquals(none, AstraDownloadRange.rejectedResume(
            206, 500L, 1_150_000_000L, 1_350_000_000L
        ))
        assertEquals(none, AstraDownloadRange.rejectedResume(
            200, 500L, 1_150_000_000L, 1_350_000_000L
        ))
        assertEquals(none, AstraDownloadRange.rejectedResume(
            416, 0L, 1_150_000_000L, 1_350_000_000L
        ))
    }

    @Test fun corruptOffsetAndSizeBoundsFailClosed() {
        assertTrue(runCatching {
            AstraDownloadRange.rejectedResume(
                416, -1L, 1_150_000_000L, 1_350_000_000L
            )
        }.isFailure)
        assertTrue(runCatching {
            AstraDownloadRange.rejectedResume(416, 10L, 500L, 400L)
        }.isFailure)
        assertTrue(runCatching {
            AstraDownloadRange.rejectedResume(416, 10L, 0L, 100L)
        }.isFailure)
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
