package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraChatViewportTest {
    @Test fun defaultFollowingDoesNotBreakWhenResponseGrows() {
        assertTrue(AstraChatViewport.nextFollowState(true, false, false))
        assertTrue(AstraChatViewport.nextFollowState(true, false, true))
    }

    @Test fun draggingAlwaysDetachesEvenWhenPreviouslyFollowing() {
        assertFalse(AstraChatViewport.nextFollowState(true, true, false))
        assertFalse(AstraChatViewport.nextFollowState(true, true, true))
    }

    @Test fun olderMessagesStayVisibleDuringStreaming() {
        assertFalse(AstraChatViewport.nextFollowState(false, false, true))
        assertTrue(AstraChatViewport.showReturnToLatest(false, true))
    }

    @Test fun scrollingBackToEndResumesFollowing() {
        assertTrue(AstraChatViewport.nextFollowState(false, false, false))
        assertFalse(AstraChatViewport.showReturnToLatest(false, false))
    }

    @Test fun theReturnButtonIsHiddenUnlessReaderHasDetached() {
        assertFalse(AstraChatViewport.showReturnToLatest(true, true))
        assertFalse(AstraChatViewport.showReturnToLatest(true, false))
        assertFalse(AstraChatViewport.showReturnToLatest(false, false))
    }
}
