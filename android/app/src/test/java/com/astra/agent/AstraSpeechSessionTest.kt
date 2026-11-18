package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These are pure session/lifecycle-policy checks. Android microphone hardware
 * and installed language packs must still be tested on the owner's phone.
 */
class AstraSpeechSessionTest {
    @Test fun aCurrentResultCanBeDeliveredExactlyOnce() {
        val session = AstraSpeechSession()
        val id = session.begin()
        assertTrue(session.isPending())
        assertTrue(session.accepts(id))
        assertTrue(session.complete(id))
        assertFalse(session.isPending())
        assertFalse(session.accepts(id))
        assertFalse(session.complete(id))
    }

    @Test fun cancellationInvalidatesAllQueuedCallbacks() {
        val session = AstraSpeechSession()
        val original = session.begin()
        session.cancel()
        assertFalse(session.isPending())
        assertFalse(session.accepts(original))
        assertFalse(session.complete(original))
        val restarted = session.begin()
        assertTrue(restarted != original)
        assertFalse(session.complete(original))
        assertTrue(session.accepts(restarted))
    }

    @Test fun newerDictationCannotReceiveOldSessionResultsOrErrors() {
        val session = AstraSpeechSession()
        val first = session.begin()
        val second = session.begin()
        assertFalse(session.accepts(first))
        assertFalse(session.complete(first))
        assertTrue(session.accepts(second))
        assertTrue(session.complete(second))
        assertFalse(session.complete(second))
    }

    @Test fun cancelledMicrophoneDoesNotLeavePendingRecognition() {
        val session = AstraSpeechSession()
        repeat(250) {
            val id = session.begin()
            session.cancel()
            assertFalse(session.accepts(id))
            assertFalse(session.isPending())
        }
    }

    @Test fun microphoneAndTextToSpeechAreForegroundAndUnlockOnly() {
        assertTrue(AstraSpeechPrivacy.mayUseVoice(true, false, false))
        assertTrue(AstraSpeechPrivacy.mayUseVoice(true, true, true))
        assertFalse(AstraSpeechPrivacy.mayUseVoice(true, true, false))
        assertFalse(AstraSpeechPrivacy.mayUseVoice(false, true, true))
        assertFalse(AstraSpeechPrivacy.mayUseVoice(false, false, true))
        assertFalse(AstraSpeechPrivacy.mayUseVoice(false, false, false))
    }
}
