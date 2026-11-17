package com.astra.agent

import android.app.ApplicationExitInfo
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraProcessExitReasonTest {
    @Test fun nativeFailureIsDistinguishedFromJavaFailure() {
        val native = AstraProcessExitReason.describe(
            ApplicationExitInfo.REASON_CRASH_NATIVE
        )
        val java = AstraProcessExitReason.describe(
            ApplicationExitInfo.REASON_CRASH
        )
        assertTrue(native.contains("native crash"))
        assertTrue(java.contains("Java/Kotlin"))
    }

    @Test fun lowMemoryTerminationIsNotMisclassifiedAsNativeCrash() {
        val reason = AstraProcessExitReason.describe(
            ApplicationExitInfo.REASON_LOW_MEMORY
        )
        assertTrue(reason.contains("low-memory"))
        assertTrue(!reason.contains("native crash"))
    }

    @Test fun processExitMustOccurDuringTheInterruptedInference() {
        val start = 1_700_000_000_000L
        val observed = start + 12_000L
        assertTrue(AstraProcessExitReason.matchesCheckpointExit(
            start, start + 4_000L, observed
        ))
        // Older native crashes, force-stops and process restarts must never
        // be presented as the cause of this newer chat interruption.
        assertTrue(!AstraProcessExitReason.matchesCheckpointExit(
            start, start - 1L, observed
        ))
        assertTrue(!AstraProcessExitReason.matchesCheckpointExit(
            start, start - 60_000L, observed
        ))
        assertTrue(!AstraProcessExitReason.matchesCheckpointExit(
            start, observed + 1L, observed
        ))
    }

    @Test fun unknownOrCorruptTimestampsCannotClaimAnExitCause() {
        val now = 1_700_000_000_000L
        assertTrue(!AstraProcessExitReason.matchesCheckpointExit(0L, now, now))
        assertTrue(!AstraProcessExitReason.matchesCheckpointExit(-1L, now, now))
        assertTrue(!AstraProcessExitReason.matchesCheckpointExit(now + 1L, now, now))
        assertTrue(!AstraProcessExitReason.matchesCheckpointExit(now, 0L, now))
        assertTrue(AstraProcessExitReason.matchesCheckpointExit(now, now, now))
    }

    @Test fun generalExitHistoryIsExplicitlyUnrelatedToCurrentChat() {
        val historical = AstraProcessExitReason.labelForContext(
            ApplicationExitInfo.REASON_CRASH_NATIVE, false
        )
        val correlated = AstraProcessExitReason.labelForContext(
            ApplicationExitInfo.REASON_CRASH_NATIVE, true
        )
        assertTrue(historical.contains("not linked to this chat"))
        assertTrue(correlated.contains("native crash"))
        assertTrue(!correlated.contains("not linked to this chat"))
    }

    @Test fun userRequestedExitIsNotClaimedAsCrash() {
        val reason = AstraProcessExitReason.describe(
            ApplicationExitInfo.REASON_USER_REQUESTED
        )
        assertTrue(reason.contains("not crash proof"))
    }
}
