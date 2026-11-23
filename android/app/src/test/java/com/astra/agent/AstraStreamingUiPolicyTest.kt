package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraStreamingUiPolicyTest {
    @Test fun firstAvailablePartialIsDisplayedImmediately() {
        assertTrue(AstraStreamingUiPolicy.intervalElapsed(51L, 0L))
        assertTrue(AstraStreamingUiPolicy.hasNewVisibleText("H", ""))
    }

    @Test fun frequentNativeChunksAreCombinedIntoFewerChatRedraws() {
        val start = 2_000_000_000L
        assertFalse(AstraStreamingUiPolicy.intervalElapsed(
            start + 200_000_000L, start
        ))
        assertFalse(AstraStreamingUiPolicy.intervalElapsed(
            start + 549_999_999L, start
        ))
        assertTrue(AstraStreamingUiPolicy.intervalElapsed(
            start + 550_000_000L, start
        ))
        assertFalse(AstraStreamingUiPolicy.intervalElapsed(
            start - 10L, start
        ))
    }

    @Test fun blankAndRepeatedTextDoNotScheduleLayoutWork() {
        assertFalse(AstraStreamingUiPolicy.hasNewVisibleText("", ""))
        assertFalse(AstraStreamingUiPolicy.hasNewVisibleText("  ", ""))
        assertFalse(AstraStreamingUiPolicy.hasNewVisibleText("Loading", "Loading"))
        assertTrue(AstraStreamingUiPolicy.hasNewVisibleText("Loading...", "Loading"))
    }

    @Test fun firstAndFinalNativeAnswersRemainUnaffected() {
        // These are only UI-publication checks: the full streaming buffer
        // and returned final answer are never truncated or rate-limited.
        val interim = "A short explanation."
        val final = interim + " Therefore the answer is 100."
        assertTrue(AstraStreamingUiPolicy.hasNewVisibleText(final, interim))
        assertTrue(AstraStreamingUiPolicy.intervalElapsed(1L, 0L))
    }
}
