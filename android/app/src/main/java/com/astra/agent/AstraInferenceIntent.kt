package com.astra.agent

/**
 * Keep the current owner request separate from internally attached context.
 * This is a provenance boundary, not a semantic classifier. The complete
 * contextual prompt is still passed to Qwen unchanged.
 */
internal object AstraInferenceIntent {
    fun policyQuestion(
        ownerRequest: String?,
        preparedQuestion: String,
        diagnosticIsolated: Boolean
    ): String {
        if (diagnosticIsolated || ownerRequest.isNullOrBlank()) return preparedQuestion
        return AstraConversationContext.currentUserMessage(ownerRequest)
    }

    /**
     * An automatically enriched same-chat follow-up contains prior model
     * prose, not a new request to search private files or course passages.
     * Keep it in the model context without using that prose as retrieval
     * terms. Explicit book requests retain their existing authorization.
     */
    fun allowsAutomaticReferences(original: String, focused: String): Boolean =
        original == focused && !AstraWorkspaceHandoff.isModelDraft(original)
}
