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
        // The model is streamed token by token. A reasoning block can begin
        // after some visible answer text, not just at the beginning. Strip
        // complete blocks anywhere and discard unfinished blocks, including
        // nested/repeated reasoning markers. Never save private deliberation.
        val raw = modelOutput.trim()
        val opening = "<think>"
        val closing = "</think>"
        val visible = StringBuilder()
        var index = 0
        while (index < raw.length) {
            val start = raw.indexOf(opening, index, ignoreCase = true)
            val end = raw.indexOf(closing, index, ignoreCase = true)
            if (start >= 0 && (end < 0 || start < end)) {
                visible.append(raw, index, start)
                var depth = 1
                var cursor = start + opening.length
                while (depth > 0) {
                    val nested = raw.indexOf(opening, cursor, ignoreCase = true)
                    val stop = raw.indexOf(closing, cursor, ignoreCase = true)
                    if (stop < 0) {
                        // Streaming stopped inside a reasoning block.
                        return safeVisiblePrefix(visible.toString())
                    }
                    if (nested >= 0 && nested < stop) {
                        depth++
                        cursor = nested + opening.length
                    } else {
                        depth--
                        cursor = stop + closing.length
                    }
                }
                index = cursor
                // Keep one separator when both sides of a hidden block had
                // spaces, instead of displaying a doubled gap mid-sentence.
                if (visible.isNotEmpty() && visible[visible.length - 1] == ' ' &&
                    raw.getOrNull(index) == ' '
                ) index++
            } else if (end >= 0) {
                // When an opening marker was lost in a partial stream, text
                // before an unmatched closing marker cannot be trusted.
                visible.setLength(0)
                index = end + closing.length
            } else {
                visible.append(raw, index, raw.length)
                break
            }
        }
        return safeVisiblePrefix(visible.toString())
    }

    private fun safeVisiblePrefix(value: String): String {
        // In an incremental UI update, '<thi' may be the start of a new
        // '<think>' marker. Hold back such suffixes until the token resolves.
        var trim = 0
        for (marker in listOf("<think>", "</think>")) {
            for (length in 1 until marker.length) {
                if (value.endsWith(marker.substring(0, length), ignoreCase = true)) {
                    trim = maxOf(trim, length)
                }
            }
        }
        return value.dropLast(trim).trim().take(16_000)
    }
}
