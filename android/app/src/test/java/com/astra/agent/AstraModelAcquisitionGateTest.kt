package com.astra.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraModelAcquisitionGateTest {
    @Test fun secondAcquisitionFailsFastInsteadOfDuplicatingHugeStaging() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            AstraModelAcquisitionGate.singleFlight { release.await() }
        }
        val duplicate = runCatching {
            AstraModelAcquisitionGate.singleFlight {
                error("A second 1.3GB transfer must never start")
            }
        }.exceptionOrNull()
        assertTrue(duplicate is IllegalStateException)
        assertTrue(duplicate?.message.orEmpty().contains("import or download is already running"))
        release.complete(Unit)
        first.join()
        assertEquals("next model", AstraModelAcquisitionGate.singleFlight { "next model" })
    }

    @Test fun cancelledTransferReleasesGlobalAcquisitionOwnership() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val transfer = launch(start = CoroutineStart.UNDISPATCHED) {
            AstraModelAcquisitionGate.singleFlight {
                entered.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        entered.await()
        transfer.cancelAndJoin()
        assertEquals(5, AstraModelAcquisitionGate.singleFlight { 5 })
    }

    @Test fun failedTransferNeverLeavesDownloadAndImportBlocked() = runBlocking {
        val failure = runCatching {
            AstraModelAcquisitionGate.singleFlight<Unit> {
                throw IllegalStateException("Simulated provider failure")
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("safe retry", AstraModelAcquisitionGate.singleFlight { "safe retry" })
    }

    @Test fun stagingDoesNotHoldTheNativeInferenceMutex() = runBlocking {
        val nativeRan = AstraModelAcquisitionGate.singleFlight {
            AstraModelOperationGate.withNativeSession { true }
        }
        assertTrue(nativeRan)
        val stalled = CompletableDeferred<Unit>()
        val inference = launch(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withNativeSession {
                stalled.await()
            }
        }
        // A native response can run while a 1.7B transfer stages bytes.
        var stagingEntered = false
        AstraModelAcquisitionGate.singleFlight {
            stagingEntered = true
        }
        assertTrue(stagingEntered)
        assertFalse(inference.isCompleted)
        stalled.complete(Unit)
        inference.join()
    }
}
