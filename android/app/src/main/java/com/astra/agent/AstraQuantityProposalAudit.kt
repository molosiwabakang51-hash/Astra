package com.astra.agent

/** Evaluates a model proposal without granting it authority over conversation facts. */
internal object AstraQuantityProposalAudit {
    sealed class Outcome {
        object Invalid : Outcome()
        data class Unconfirmed(val computed: String, val unit: String) : Outcome()
        data class Confirmed(val computed: String, val unit: String) : Outcome()
    }

    fun evaluate(proposal: String, ownerConfirmedTrace: String? = null): Outcome {
        val checked = AstraQuantityTraceVerifier.verify(proposal) ?: return Outcome.Invalid
        // Matching results alone cannot establish that the interpretation was faithful.
        if (ownerConfirmedTrace == null || ownerConfirmedTrace != proposal) {
            return Outcome.Unconfirmed(checked.result, checked.unit)
        }
        return Outcome.Confirmed(checked.result, checked.unit)
    }
}
