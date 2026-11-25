package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraDiagnosticContextTest {
    @Test fun diagnosticPromptsNeverIngestOwnerBookDataEvenWhenOptedIn() {
        for (explicit in listOf(false, true)) {
            for (inChat in listOf(false, true)) {
                assertFalse(
                    AstraDiagnosticContext.allowPrivateBooks(
                        explicit, inChat, diagnosticIsolated = true
                    )
                )
            }
        }
    }

    @Test fun normalConversationRetainsExistingExplicitAndOptInBookAccess() {
        assertTrue(AstraDiagnosticContext.allowPrivateBooks(true, false, false))
        assertTrue(AstraDiagnosticContext.allowPrivateBooks(false, true, false))
        assertFalse(AstraDiagnosticContext.allowPrivateBooks(false, false, false))
    }

    @Test fun benchmarkDoesNotChangeSavedLanguageChoice() {
        for (choice in listOf("russian", "setswana", "english", "auto")) {
            assertEquals("auto", AstraDiagnosticContext.replyLanguage(choice, true))
            assertEquals(choice, AstraDiagnosticContext.replyLanguage(choice, false))
        }
    }
    @Test fun explicitMemoryGroundingNeverMixesUnrequestedKnowledgeSources() {
        for (explicit in listOf(false, true)) {
            for (enabled in listOf(false, true)) {
                assertFalse(AstraDiagnosticContext.allowPrivateBooks(
                    explicit, enabled, diagnosticIsolated = false, memoryGrounded = true
                ))
            }
        }
        assertFalse(AstraDiagnosticContext.allowOpenCourse(false, true))
        assertFalse(AstraDiagnosticContext.allowOpenCourse(true, false))
        assertFalse(AstraDiagnosticContext.allowOpenCourse(true, true))
        assertTrue(AstraDiagnosticContext.allowOpenCourse(false, false))
    }
}
