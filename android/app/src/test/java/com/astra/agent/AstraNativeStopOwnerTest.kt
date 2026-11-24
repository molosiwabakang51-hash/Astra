package com.astra.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One process-wide JNI engine, but an old Activity must not interrupt the
 * current Activity's native response after an ownership handoff.
 */
class AstraNativeStopOwnerTest {
    @Test fun onlyCurrentOwnerSignalsWhileItsSessionIsActive() = runBlocking {
        val stale = AstraModelOperationGate.newOwnerId()
        val active = AstraModelOperationGate.newOwnerId()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val running = launch(start = CoroutineStart.UNDISPATCHED) {
            AstraModelOperationGate.withNativeSession {
                AstraModelOperationGate.claimNativeSession(active)
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        var nativeSignals = 0
        assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(stale) {
            nativeSignals++
        })
        assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(0L) {
            nativeSignals++
        })
        assertTrue(AstraModelOperationGate.signalStopIfCurrentOwner(active) {
            nativeSignals++
        })
        assertEquals(1, nativeSignals)
        release.complete(Unit)
        running.join()
        assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(active) {
            nativeSignals++
        })
        assertEquals(1, nativeSignals)
    }

    @Test fun completedSessionCannotAbortNextActivitySession() = runBlocking {
        val first = AstraModelOperationGate.newOwnerId()
        val second = AstraModelOperationGate.newOwnerId()
        AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(first)
            assertTrue(AstraModelOperationGate.signalStopIfCurrentOwner(first) { })
        }
        AstraModelOperationGate.withNativeSession {
            assertTrue(AstraModelOperationGate.claimNativeSession(second))
            assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(first) {
                error("A stale Activity must never signal JNI")
            })
            assertTrue(AstraModelOperationGate.signalStopIfCurrentOwner(second) { })
        }
        assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(second) { })
    }

    @Test fun failedGenerationRevokesPermissionInFinally() = runBlocking {
        val owner = AstraModelOperationGate.newOwnerId()
        val outcome = runCatching {
            AstraModelOperationGate.withNativeSession {
                AstraModelOperationGate.claimNativeSession(owner)
                assertTrue(AstraModelOperationGate.signalStopIfCurrentOwner(owner) { })
                error("synthetic native generation failure")
            }
        }
        assertTrue(outcome.isFailure)
        assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(owner) {
            error("The failed generation has already relinquished ownership")
        })
    }

    @Test fun modelPromotionCannotBeStoppedViaThePreviousActivity() = runBlocking {
        val owner = AstraModelOperationGate.newOwnerId()
        AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(owner)
        }
        AstraModelOperationGate.withPromotion {
            assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(owner) {
                error("Model promotion is not generation")
            })
        }
        assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(owner) { })
        AstraModelOperationGate.withNativeSession {
            assertTrue(AstraModelOperationGate.claimNativeSession(owner))
            assertTrue(AstraModelOperationGate.signalStopIfCurrentOwner(owner) { })
        }
    }

    @Test fun exceptionsInsideAtomicSignalDoNotLeaveStaleAuthorizationLock() = runBlocking {
        val owner = AstraModelOperationGate.newOwnerId()
        AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(owner)
            val failure = runCatching {
                AstraModelOperationGate.signalStopIfCurrentOwner(owner) {
                    throw UnsatisfiedLinkError("simulated missing native stop symbol")
                }
            }.exceptionOrNull()
            assertTrue(failure is UnsatisfiedLinkError)
            assertTrue(AstraModelOperationGate.signalStopIfCurrentOwner(owner) { })
        }
        assertFalse(AstraModelOperationGate.signalStopIfCurrentOwner(owner) { })
    }
}
