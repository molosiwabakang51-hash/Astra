package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraInferenceSessionTest {
    @Test fun firstRequestMustLoadAndSubsequentTurnCanReuseModel() {
        val session = AstraInferenceSession()
        assertTrue(session.requiresLoad(10, true))
        session.markLoaded(10)
        assertFalse(session.requiresLoad(10, true))
        assertTrue(session.requiresLoad(11, true))
    }

    @Test fun cancelledGenerationForcesReloadEvenInSameConversation() {
        val session = AstraInferenceSession()
        session.markLoaded(8)
        session.invalidate()
        assertTrue(session.requiresLoad(8, true))
        session.markLoaded(8)
        assertFalse(session.requiresLoad(8, true))
    }

    @Test fun loadingRequiresNativeModelReadyEvenWhenGenerationMatches() {
        val session = AstraInferenceSession()
        session.markLoaded(3)
        assertTrue(session.requiresLoad(3, false))
        assertFalse(session.requiresLoad(3, true))
    }

    @Test fun multipleFailuresDoNotClearInvalidation() {
        val session = AstraInferenceSession()
        session.markLoaded(-3)
        session.invalidate()
        session.invalidate()
        assertTrue(session.requiresLoad(-3, true))
        assertTrue(session.requiresLoad(4, true))
    }
}
