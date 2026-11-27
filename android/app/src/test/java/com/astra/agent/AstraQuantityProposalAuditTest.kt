package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraQuantityProposalAuditTest {
    private fun trace(body: String) = "Trace quantity: $body. What is the current amount?"

    @Test fun validProposalIsNotAutomaticallyConfirmed() {
        val proposal = trace("initial amount is 10 coins; add 5 coins to it")
        assertEquals(AstraQuantityProposalAudit.Outcome.Unconfirmed("15", "coin"),
            AstraQuantityProposalAudit.evaluate(proposal))
        assertEquals(AstraQuantityProposalAudit.Outcome.Confirmed("15", "coin"),
            AstraQuantityProposalAudit.evaluate(proposal, proposal))
    }

    @Test fun equalResultsDoNotProveSemanticFidelity() {
        val proposal = trace("initial amount is 10 coins; add 5 coins to it")
        val other = trace("initial amount is 20 coins; subtract 5 coins from it")
        assertEquals(AstraQuantityProposalAudit.Outcome.Unconfirmed("15", "coin"),
            AstraQuantityProposalAudit.evaluate(proposal, other))
    }

    @Test fun malformedProposalFailsClosedEvenIfConfirmed() {
        val invalid = trace("initial amount is 10 coins; add 5 dollars to it")
        assertEquals(AstraQuantityProposalAudit.Outcome.Invalid,
            AstraQuantityProposalAudit.evaluate(invalid, invalid))
    }
}
