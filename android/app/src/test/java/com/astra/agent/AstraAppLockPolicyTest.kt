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

    @Test fun disablingOptInRestoresUnrestrictedDefault() {
        assertFalse(AstraAppLockPolicy.requiresUnlock(false, true))
        assertFalse(AstraAppLockPolicy.requiresUnlock(false, false))
    }
}
