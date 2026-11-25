package com.astra.agent

/**
 * Source-of-truth for identifying an incomplete model answer.
 *
 * A cancelled or process-killed generation is not a reliable account of what
 * Astra answered. Retain the saved text visibly for the owner, but exclude
 * incomplete assistant messages from context replay and same-chat follow-up
 * anchoring. Never suppress or change user-authored messages.
 */
internal object AstraInterruptedReply {
    const val EMPTY_MODEL_REPLY = "The offline model did not produce a usable answer. Please try again."
    fun isIncomplete(assistantText: String): Boolean {
        val value = assistantText.trim()
        return value.startsWith("ASTRA // THINKING") ||
            value == "The response was interrupted when Astra closed." ||
            value == EMPTY_MODEL_REPLY ||
            value.startsWith("Offline model unavailable.") ||
            value.startsWith("Offline response interrupted.") ||
            value.startsWith("Offline response stopped.") ||
            value.contains("\n\n[Response interrupted]") ||
            value.contains("\n\n[Response stopped]")
    }
}
