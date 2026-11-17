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

    @Test fun userRequestedExitIsNotClaimedAsCrash() {
        val reason = AstraProcessExitReason.describe(
            ApplicationExitInfo.REASON_USER_REQUESTED
        )
        assertTrue(reason.contains("not crash proof"))
    }
}
