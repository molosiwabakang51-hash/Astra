package com.astra.agent

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraVerifiedEconomicsTest {
    @Test fun identifiesCorrectYaleAuthorAndAcknowledgesMisattribution() {
        assertTrue(AstraVerifiedEconomics.respond("Robert Stiller?").orEmpty()
            .contains("Robert J. Shiller"))
        assertTrue(AstraVerifiedEconomics.respond(
            "Niall Shiller is not the author of Irrational Exuberance, yes?"
        ).orEmpty().contains("mistaken attribution"))
        assertTrue(AstraVerifiedEconomics.respond("who is Robert J. Shiller?").orEmpty()
            .contains("Yale"))
        assertTrue(AstraVerifiedEconomics.respond("what does Shiller say about irrational exuberance")
            .orEmpty().contains("speculative bubbles"))
        assertTrue(AstraVerifiedEconomics.respond("who is Robert J. Shiller?").orEmpty()
            .contains("2013"))
    }

    @Test fun neverHijacksOtherFinancialConceptsOrStudy() {
        assertNull(AstraVerifiedEconomics.respond("Tell me about Shiller's other books"))
        assertNull(AstraVerifiedEconomics.respond("how do I buy a US Treasury bond?"))
        assertNull(AstraVerifiedEconomics.respond("teach me French"))
        assertNull(AstraVerifiedEconomics.respond("What is leverage?"))
        assertNull(AstraVerifiedEconomics.respond("a".repeat(1000)))
    }
}
