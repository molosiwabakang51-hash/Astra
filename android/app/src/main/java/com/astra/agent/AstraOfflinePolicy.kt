package com.astra.agent

/**
 * Keeps local command actions separate from model-generated prose.
 * No network provider is involved: the model is packaged in the APK.
 */
internal object AstraOfflinePolicy {
    private const val UNSUPPORTED =
        "I cannot answer that reliably with Astra's current offline tools."

    fun shouldUseModel(assistantText: String, sensitive: Boolean, hasAction: Boolean): Boolean =
        !sensitive && !hasAction && assistantText.startsWith(UNSUPPORTED)

    // Qwen3's documented soft switch makes the small phone model answer
    // directly rather than spend its output budget generating <think> tokens.
    fun modelPrompt(userText: String): String =
        userText.trim().take(3000) + "\n/no_think"

    // A killed Android process can leave its last saved reply as 'thinking'.
    // Recover these placeholders without changing valid saved conversations.
    fun recoverInterruptedMessage(role: String, text: String): String =
        if (role == "Astra" && text in setOf(
                "ASTRA // THINKING OFFLINE...", "ASTRA // THINKING..."
            )
        ) "The response was interrupted when Astra closed." else text

    fun publicReply(modelOutput: String): String {
        var reply = modelOutput.trim()
        // Reasoning is not a user-facing answer. Do not display a partial
        // thinking block if generation ends before the closing tag.
        if (reply.startsWith("<think>")) {
            val end = reply.indexOf("</think>")
            if (end < 0) return ""
            reply = reply.substring(end + "</think>".length).trim()
        }
        return reply.take(16_000)
    }
}
