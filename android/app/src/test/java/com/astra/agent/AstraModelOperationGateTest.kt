package com.astra.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraModelOperationGateTest {
    @Test fun verifiedPromotionCannotReplaceWeightsDuringNativeInference() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val inference = launch(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withNativeSession {
                events.add("native start")
                release.await()
                events.add("native end")
            }
        }
        val promotion = async(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withPromotion {
                events.add("promote verified GGUF")
                true
            }
        }
        assertEquals(listOf("native start"), events)
        assertFalse(promotion.isCompleted)
        release.complete(Unit)
        inference.join()
        assertTrue(promotion.await())
        assertEquals(
            listOf("native start", "native end", "promote verified GGUF"), events
        )
    }

    @Test fun nativeInferenceWaitsForAnActiveModelPromotion() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val promotion = launch(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withPromotion {
                events.add("promotion start")
                release.await()
                events.add("promotion end")
            }
        }
        val inference = async(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withNativeSession {
                events.add("native inference")
            }
        }
        assertEquals(listOf("promotion start"), events)
        assertFalse(inference.isCompleted)
        release.complete(Unit)
        promotion.join()
        inference.await()
        assertEquals(
            listOf("promotion start", "promotion end", "native inference"), events
        )
    }

    @Test fun cancellationOfQueuedPromotionDoesNotUnlockActiveNativeSession() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val inference = launch(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withNativeSession {
                events.add("native")
                release.await()
            }
        }
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withPromotion { events.add("cancelled") }
        }
        cancelled.cancelAndJoin()
        assertEquals(listOf("native"), events)
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withPromotion { events.add("valid promotion") }
        }
        assertFalse(next.isCompleted)
        release.complete(Unit)
        inference.join()
        next.await()
        assertEquals(listOf("native", "valid promotion"), events)
    }

    @Test fun duplicateSafImportsFailFastAndLockRecoversAfterFailure() = runBlocking {
        val failure = AstraModelOperationGate.withImport {
            runCatching {
                AstraModelOperationGate.withImport { "should not run" }
            }.exceptionOrNull()
        }
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("import is already in progress"))
        assertEquals("next import", AstraModelOperationGate.withImport { "next import" })
    }

    @Test fun duplicateDownloadsFailFastWithoutBlockingIndependentStaging() = runBlocking {
        AstraModelOperationGate.withImport {
            val failure = AstraModelOperationGate.withDownload {
                runCatching {
                    AstraModelOperationGate.withDownload { Unit }
                }.exceptionOrNull()
            }
            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message.orEmpty().contains("download is already in progress"))
        }
        assertEquals(7, AstraModelOperationGate.withDownload { 7 })
    }

    @Test fun failedPromotionReleasesNativeGate() = runBlocking {
        val failure = runCatching {
            AstraModelOperationGate.withPromotion<Unit> {
                throw IllegalStateException("synthetic failure")
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(42, AstraModelOperationGate.withNativeSession { 42 })
    }
}
