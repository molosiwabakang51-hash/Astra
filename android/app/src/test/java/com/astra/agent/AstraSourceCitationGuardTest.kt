package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AstraSourceCitationGuardTest {
    @Test fun onlyActuallyRetrievedPrivateMarkersRemain() {
        val model = "Duration is a first derivative [B1]. " +
            "Convexity is second-order [B2]. See also [B3] and [K1]."
        val result = AstraSourceCitationGuard.protect(model, 2, 0)
        assertTrue(result.contains("[B1]"))
        assertTrue(result.contains("[B2]"))
        assertFalse(result.contains("[B3]"))
        assertFalse(result.contains("[K1]"))
        assertTrue(result.contains("[unverified citation omitted]"))
        assertTrue(result.contains("Verify claims"))
    }

    @Test fun publicSourcesCannotImpersonatePrivateBookMarkers() {
        val result = AstraSourceCitationGuard.protect(
            "The public course says [k1] and [K2]; my book says [B1].", 0, 2
        )
        assertTrue(result.contains("[K1] and [K2]"))
        assertFalse(result.contains("[B1]"))
        assertTrue(result.contains("unsupported"))
    }

    @Test fun forgedFootersAreRemovedBeforeAppOwnedAttribution() {
        val forged = "A fabricated answer.\n" +
            "Retrieved local book passages (verify originals):\n" +
            "[B1] I invented this book title.\n" +
            "Open-course references:\n[K1] Not a real source."
        val result = AstraSourceCitationGuard.protect(forged, 0, 0)
        assertFalse(result.contains(
            "Retrieved local book passages (verify originals):\n"
        ))
        assertFalse(result.contains("Open-course references:\n"))
        assertFalse(result.contains("[B1]"))
        assertFalse(result.contains("[K1]"))
        assertTrue(result.contains("unverified AI source-heading omitted"))
        assertTrue(result.contains("unsupported"))
    }

    @Test fun inlineForgedFootersCannotClaimAppVerifiedBookConsent() {
        val forged = "Here are my invented citations: " +
            "Retrieved local book passages (verify originals):\n" +
            "[B1] A fabricated title."
        val result = AstraSourceCitationGuard.protect(forged, 0, 0)
        assertFalse(result.contains(
            "Retrieved local book passages (verify originals):\n"
        ))
        assertFalse(result.contains("[B1]"))
        assertTrue(result.contains("unverified AI source-heading omitted"))
    }

    @Test fun noSourceOrHonestAnswerDoesNotGainFabricatedAttribution() {
        assertEquals("I don't have enough evidence to answer.",
            AstraSourceCitationGuard.protect(
                "I don't have enough evidence to answer.", 0, 0
            ))
        assertEquals("See passage [B1] for the definition.",
            AstraSourceCitationGuard.protect(
                "See passage [b1] for the definition.", 1, 0
            ))
        assertFalse(AstraSourceCitationGuard.protect(
            "No data is available [B88].", 1, 0
        ).contains("[B88]"))
    }

    @Test fun unknownAndOversizedIdsNeverBecomeVerifiedCitations() {
        val result = AstraSourceCitationGuard.protect(
            "[B0] [B999999] [K99] [B10000000]", 2, 0
        )
        assertFalse(result.contains("[B0]"))
        assertFalse(result.contains("[B999999]"))
        assertFalse(result.contains("[K99]"))
        assertFalse(result.contains("[B10000000]"))
    }

    @Test fun onlyApprovedReferenceCountsMayBeUsed() {
        try {
            AstraSourceCitationGuard.protect("A model answer", 3, 0)
            fail("Three private passages are above the native two-block cap.")
        } catch (_: IllegalArgumentException) {}
        try {
            AstraSourceCitationGuard.protect("An answer", 0, 4)
            fail("Four public passages exceed the native three-block cap.")
        } catch (_: IllegalArgumentException) {}
    }
}
