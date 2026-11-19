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

    @Test fun inactivityLockEnforcesExactLimit() {
        assertFalse(AstraAppLockPolicy.shouldLockForInactivity(true, 120_000L, 0L))
        assertFalse(AstraAppLockPolicy.shouldLockForInactivity(true, 120_000L, 119_999L))
        assertTrue(AstraAppLockPolicy.shouldLockForInactivity(true, 120_000L, 120_000L))
        assertTrue(AstraAppLockPolicy.shouldLockForInactivity(true, 300_000L, 300_001L))
        assertTrue(AstraAppLockPolicy.shouldLockForInactivity(true, 900_000L, -1L))
    }

    @Test fun idleProtectionIsOptInAndStrictlyValidated() {
        assertFalse(AstraAppLockPolicy.shouldLockForInactivity(false, 120_000L, 900_000L))
        assertFalse(AstraAppLockPolicy.shouldLockForInactivity(true, 0L, 900_000L))
        assertFalse(AstraAppLockPolicy.shouldLockForInactivity(true, -1L, 900_000L))
        assertFalse(AstraAppLockPolicy.shouldLockForInactivity(true, 30_000L, 900_000L))
        org.junit.Assert.assertEquals(
            listOf(0L, 120_000L, 300_000L, 900_000L),
            AstraAppLockPolicy.idleTimeoutsMillis
        )
        org.junit.Assert.assertEquals(
            "After 15 minutes", AstraAppLockPolicy.idleTimeoutLabel(900_000L)
        )
        org.junit.Assert.assertEquals(
            "Off", AstraAppLockPolicy.idleTimeoutLabel(-1L)
        )
    }

    @Test fun disablingOptInRestoresUnrestrictedDefault() {
        assertFalse(AstraAppLockPolicy.requiresUnlock(false, true))
        assertFalse(AstraAppLockPolicy.requiresUnlock(false, false))
    }

    @Test fun corruptSavedLockPreferenceCannotSilentlyDisableProtection() {
        assertFalse(AstraAppLockPolicy.enabledFromStoredPreference(false, null))
        assertFalse(AstraAppLockPolicy.enabledFromStoredPreference(false, "corrupt"))
        assertFalse(AstraAppLockPolicy.enabledFromStoredPreference(true, false))
        assertTrue(AstraAppLockPolicy.enabledFromStoredPreference(true, true))
        for (bad in listOf<Any?>(
            null, "false", "true", "", 0, 1L, 0.0, byteArrayOf(0)
        )) {
            assertTrue("Invalid lock preference unexpectedly bypassed authentication",
                AstraAppLockPolicy.enabledFromStoredPreference(true, bad))
            assertFalse(AstraAppLockPolicy.unlockedOnColdLaunch(
                AstraAppLockPolicy.enabledFromStoredPreference(true, bad)))
        }
    }

}
