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
        assertTrue(session.requiresLoad(3, true))
        session.markLoaded(3)
        assertFalse(session.requiresLoad(3, true))
    }

    @Test fun lostNativeReadinessRequiresFreshLoadEvenIfReadyReturns() {
        val session = AstraInferenceSession()
        session.markLoaded(7)
        assertFalse(session.requiresLoad(7, true))
        assertTrue(session.requiresLoad(7, false))
        // A transient native-ready signal must not silently re-use
        // an unknown KV-cache after the engine went unavailable.
        assertTrue(session.requiresLoad(7, true))
        session.markLoaded(7)
        assertFalse(session.requiresLoad(7, true))
    }

    @Test fun multipleFailuresDoNotClearInvalidation() {
        val session = AstraInferenceSession()
        session.markLoaded(-3)
        session.invalidate()
        session.invalidate()
        assertTrue(session.requiresLoad(-3, true))
        assertTrue(session.requiresLoad(4, true))
    }

    @Test fun warmSessionSurvivesAnOrdinaryCompletedExchange() {
        val session = AstraInferenceSession()
        val prior = listOf("user" to "Explain bonds", "assistant" to "Bonds pay coupons.")
        session.markLoaded(8)
        session.recordCompletedTurn(prior.size, prior, "Explain duration", "Duration measures sensitivity.")
        val next = prior + listOf("user" to "Explain duration", "assistant" to "Duration measures sensitivity.")
        session.checkTranscript(next.size, next)
        assertFalse(session.requiresLoad(8, true))
    }

    @Test fun localToolTurnsForceRecoveryInsteadOfStaleNativeContext() {
        val session = AstraInferenceSession()
        val prior = listOf("user" to "Explain bonds", "assistant" to "Bonds pay coupons.")
        session.markLoaded(8)
        session.recordCompletedTurn(prior.size, prior, "Explain duration", "Duration measures sensitivity.")
        val next = prior + listOf(
            "user" to "Explain duration", "assistant" to "Duration measures sensitivity.",
            "user" to "Calculate 20% of 500", "assistant" to "100"
        )
        session.checkTranscript(next.size, next)
        assertTrue(session.requiresLoad(8, true))
    }

    @Test fun overwrittenVisibleReplyForcesReload() {
        val session = AstraInferenceSession()
        session.markLoaded(8)
        session.recordCompletedTurn(0, emptyList(), "Question", "Original answer")
        session.checkTranscript(2, listOf("user" to "Question", "assistant" to "Changed answer"))
        assertTrue(session.requiresLoad(8, true))
    }

    @Test fun repeatedTextDoesNotHideAnInterveningExchange() {
        val session = AstraInferenceSession()
        session.markLoaded(8)
        session.recordCompletedTurn(0, emptyList(), "Question", "OK")
        session.checkTranscript(4, listOf(
            "user" to "Question", "assistant" to "OK",
            "user" to "Save a note", "assistant" to "OK"
        ))
        assertTrue(session.requiresLoad(8, true))
    }

    @Test fun boundedTranscriptEvictionPreservesWarmContinuity() {
        val session = AstraInferenceSession()
        val tail = listOf("user" to "a", "assistant" to "b", "user" to "c", "assistant" to "d")
        session.markLoaded(8)
        session.recordCompletedTurn(2000, tail, "next", "e")
        session.checkTranscript(2000, tail + listOf("user" to "next", "assistant" to "e"))
        assertFalse(session.requiresLoad(8, true))
    }

    @Test fun identicalAnswerCannotHideChangedUserQuestion() {
        val session = AstraInferenceSession()
        session.markLoaded(8)
        session.recordCompletedTurn(0, emptyList(), "Original question", "OK")
        session.checkTranscript(2, listOf("user" to "Different question", "assistant" to "OK"))
        assertTrue(session.requiresLoad(8, true))
    }

    @Test fun enrichedNativePromptUsesVisibleUserTextForWarmReuse() {
        val session = AstraInferenceSession()
        val prior = listOf("user" to "Explain bonds", "assistant" to "Bond overview.")
        session.markLoaded(8)
        session.recordCompletedTurn(prior.size, prior, "Go deeper", "Detailed answer")
        session.checkTranscript(4, prior + listOf("user" to "Go deeper", "assistant" to "Detailed answer"))
        assertFalse(session.requiresLoad(8, true))
    }
}
