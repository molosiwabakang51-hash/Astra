package com.astra.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraNativeStartupWaitTest {
    @Test fun readyEngineIsReturnedWithoutWaitingForDeadline() = runBlocking {
        val state = AstraNativeStartupWait.awaitReady(timeoutMs = 1000L) {
            "initialized"
        }
        assertEquals("initialized", state)
    }

    @Test fun stalledNativeEngineReturnsUsefulErrorNotUserStoppedMessage() = runBlocking {
        val error = runCatching {
            AstraNativeStartupWait.awaitReady(timeoutMs = 30L) {
                CompletableDeferred<String>().await()
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(AstraNativeStartupWait.TIMEOUT_ADVICE, error?.message)
        assertTrue(error?.message.orEmpty().contains("Qwen3 1.7B"))
        assertFalse(error?.message.orEmpty().contains("user cancelled"))
    }

    @Test fun explicitOwnerCancellationStillPropagatesCancellationException() {
        // Catch at the test boundary as well: runBlocking can propagate
        // cancellation when its child job is deliberately stopped.
        val error = runCatching {
            runBlocking {
                val started = CompletableDeferred<Unit>()
                val blocked = CompletableDeferred<String>()
                val job = async {
                    AstraNativeStartupWait.awaitReady(timeoutMs = 5000L) {
                        started.complete(Unit)
                        blocked.await()
                    }
                }
                started.await()
                job.cancel(CancellationException("owner switched conversations"))
                job.await()
            }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertFalse(error?.message == AstraNativeStartupWait.TIMEOUT_ADVICE)
    }

    @Test fun outerCallerDeadlineIsNotMisreportedAsNativeStartupFailure() {
        val error = runCatching {
            runBlocking {
                withTimeout(30L) {
                    AstraNativeStartupWait.awaitReady(timeoutMs = 5000L) {
                        CompletableDeferred<Unit>().await()
                    }
                }
            }
        }.exceptionOrNull()
        assertTrue(error is TimeoutCancellationException)
        assertFalse(error?.message == AstraNativeStartupWait.TIMEOUT_ADVICE)
    }

    @Test fun nullIsALegitimateReadyValueNotADiagnosticTimeout() = runBlocking {
        val ready = AstraNativeStartupWait.awaitReady<String?>(
            timeoutMs = 1000L
        ) { null }
        assertEquals(null, ready)
    }

    @Test fun nativeEngineErrorStateOrOtherExceptionPropagatesUntouched() {
        val original = IllegalArgumentException("engine rejected native state")
        val error = runCatching {
            runBlocking {
                AstraNativeStartupWait.awaitReady<String>(timeoutMs = 1000L) {
                    throw original
                }
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(original.message, error?.message)
    }

    @Test fun timeoutParametersMustBePositive() = runBlocking {
        val bad = runCatching {
            AstraNativeStartupWait.awaitReady(timeoutMs = 0L) { "ready" }
        }.exceptionOrNull()
        assertTrue(bad is IllegalArgumentException)
        val negative = runCatching {
            AstraNativeStartupWait.awaitReady(timeoutMs = -1L) { "ready" }
        }.exceptionOrNull()
        assertTrue(negative is IllegalArgumentException)
    }

    @Test fun publishedTimeoutMessageIsContentFree() {
        val visible = AstraNativeStartupWait.TIMEOUT_ADVICE
        for (secret in listOf("content://", "/data/user/", "private.gguf",
            "Bearer", "prompt:", "secret-token", "http://")) {
            assertFalse(visible.contains(secret))
        }
        assertTrue(visible.contains("notes and chats are retained"))
    }
}
