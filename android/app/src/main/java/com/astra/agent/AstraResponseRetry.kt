package com.astra.agent

/**
 * Offer retry only for a failed on-device reply at the very end of a chat.
 * Never replay arbitrary successful replies or deterministic tool actions.
 * This pure policy is shared by the button visibility and its click guard.
 */
internal object AstraResponseRetry {
    data class Message(val role: String, val text: String)

    fun eligibleQuestion(tail: List<Message>, busy: Boolean): String? {
        if (busy || tail.size != 2) return null
        val (question, answer) = tail
        if (question.role != "You" || answer.role != "Astra") return null
        val prompt = question.text.trim()
        if (prompt.isEmpty()) return null
        val response = answer.text.trim()
        val failed = response == "Offline response stopped." ||
            response == "The response was interrupted when Astra closed." ||
            response.endsWith("[Response stopped]") ||
            response.contains("\n\n[Response stopped]\n\n") ||
            response.startsWith("Offline model unavailable:") ||
            response.startsWith("The offline model did not produce a usable answer.")
        return prompt.takeIf { failed }
    }
}
