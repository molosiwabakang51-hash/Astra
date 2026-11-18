package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraAppLockPolicyTest {
    @Test fun firstInstallIsNotLockedWithoutOwnerOptIn() {
        assertTrue(AstraAppLockPolicy.unlockedOnColdLaunch(false))
        assertFalse(AstraAppLockPolicy.requiresUnlock(false, false))
    }

    @Test fun enabledLockRequiresAuthenticationOnColdLaunch() {
        assertFalse(AstraAppLockPolicy.unlockedOnColdLaunch(true))
        assertTrue(AstraAppLockPolicy.requiresUnlock(true, false))
        assertFalse(AstraAppLockPolicy.requiresUnlock(true, true))
    }

    @Test fun exitingToBackgroundLocksSecuredWorkspace() {
        assertFalse(AstraAppLockPolicy.unlockedAfterBackground(true))
        assertTrue(AstraAppLockPolicy.unlockedAfterBackground(false))
        assertTrue(AstraAppLockPolicy.requiresUnlock(
            true, AstraAppLockPolicy.unlockedAfterBackground(true)
        ))
    }

    @Test fun selectableBackgroundGracePeriodHonorsExactDeadline() {
        assertTrue(AstraAppLockPolicy.unlockedAfterBackground(true, 30_000L, 0L))
        assertTrue(AstraAppLockPolicy.unlockedAfterBackground(true, 30_000L, 29_999L))
        assertFalse(AstraAppLockPolicy.unlockedAfterBackground(true, 30_000L, 30_000L))
        assertFalse(AstraAppLockPolicy.unlockedAfterBackground(true, 120_000L, 120_001L))
        assertTrue(AstraAppLockPolicy.unlockedAfterBackground(true, 300_000L, 299_999L))
    }

    @Test fun invalidOrImmediateDelayFailsClosed() {
        assertFalse(AstraAppLockPolicy.unlockedAfterBackground(true, 0L, 0L))
        assertFalse(AstraAppLockPolicy.unlockedAfterBackground(true, -1L, 0L))
        assertFalse(AstraAppLockPolicy.unlockedAfterBackground(true, 60_000L, 0L))
        assertFalse(AstraAppLockPolicy.unlockedAfterBackground(true, 30_000L, -1L))
        assertTrue(AstraAppLockPolicy.unlockedAfterBackground(false, 0L, 500_000L))
    }

    @Test fun onlyFourDocumentedDelaysAreAvailable() {
        org.junit.Assert.assertEquals(
            listOf(0L, 30_000L, 120_000L, 300_000L),
            AstraAppLockPolicy.backgroundDelaysMillis
        )
        org.junit.Assert.assertEquals(
            "Immediately", AstraAppLockPolicy.labelForDelay(0L)
        )
        org.junit.Assert.assertEquals(
            "After 2 minutes", AstraAppLockPolicy.labelForDelay(120_000L)
        )
    }

    @Test fun disablingOptInRestoresUnrestrictedDefault() {
        assertFalse(AstraAppLockPolicy.requiresUnlock(false, true))
        assertFalse(AstraAppLockPolicy.requiresUnlock(false, false))
    }
}
