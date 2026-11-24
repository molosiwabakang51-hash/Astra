package com.astra.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JNI InferenceEngine is an Android process singleton. Per-Activity
 * AstraOnDeviceBrain instances must not trust a previous owner's KV-cache.
 */
class AstraNativeSessionOwnerTest {
    @Test fun sameBrainKeepsContinuityButSwitchingBrainsForcesReload() = runBlocking {
        val firstBrain = AstraModelOperationGate.newOwnerId()
        val secondBrain = AstraModelOperationGate.newOwnerId()
        assertTrue(firstBrain > 0L && secondBrain > firstBrain)
        assertTrue(AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(firstBrain)
        })
        assertFalse(AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(firstBrain)
        })
        assertTrue(AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(secondBrain)
        })
        assertTrue(AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(firstBrain)
        })
        assertFalse(AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(firstBrain)
        })
    }

    @Test fun verifiedGgufPromotionInvalidatesEvenTheSameBrainOwner() = runBlocking {
        val brain = AstraModelOperationGate.newOwnerId()
        AstraModelOperationGate.withNativeSession {
            assertTrue(AstraModelOperationGate.claimNativeSession(brain))
        }
        AstraModelOperationGate.withPromotion { Unit }
        AstraModelOperationGate.withNativeSession {
            assertTrue(AstraModelOperationGate.claimNativeSession(brain))
        }
    }

    @Test fun failedPromotionStillRequiresAConservativeFreshNativeLoad() = runBlocking {
        val brain = AstraModelOperationGate.newOwnerId()
        AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(brain)
        }
        val error = runCatching {
            AstraModelOperationGate.withPromotion<Unit> {
                throw IllegalStateException("staged model rename failed")
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(brain)
        })
    }

    @Test fun claimsOutsideGlobalNativeGateAreRejected() {
        val owner = AstraModelOperationGate.newOwnerId()
        val error = runCatching {
            AstraModelOperationGate.claimNativeSession(owner)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test fun rejectedOwnerIdCannotBecomeAValidCacheOwner() = runBlocking {
        AstraModelOperationGate.withNativeSession {
            assertTrue(runCatching {
                AstraModelOperationGate.claimNativeSession(0L)
            }.isFailure)
        }
        val valid = AstraModelOperationGate.newOwnerId()
        assertTrue(AstraModelOperationGate.withNativeSession {
            AstraModelOperationGate.claimNativeSession(valid)
        })
    }
}
