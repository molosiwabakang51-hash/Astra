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

    private const val MAX_INPUT_CHARS = 3_000
    private const val TAIL_CHARS = 1_800
    private const val OMITTED_MARKER =
        "\n[Middle of long input omitted to fit the offline model context.]\n"

    /**
     * Preserve both the user's context at the start and the latest request at
     * the end. Plain take(3000) can silently lose the actual question when
     * an owner pastes long lecture notes, a report or a coding example.
     * Never silently concatenate disconnected excerpts as complete source.
     */
    fun boundedInput(raw: String): String {
        val input = raw.trim()
        if (input.length <= MAX_INPUT_CHARS) return input
        val headLength = MAX_INPUT_CHARS - OMITTED_MARKER.length - TAIL_CHARS
        // UTF-16 offsets must not split an emoji or other surrogate pair.
        val headEnd = if (input[headLength - 1].isHighSurrogate() &&
            input[headLength].isLowSurrogate()
        ) headLength - 1 else headLength
        val tailStart = input.length - TAIL_CHARS
        val safeTailStart = if (input[tailStart - 1].isHighSurrogate() &&
            input[tailStart].isLowSurrogate()
        ) tailStart + 1 else tailStart
        val head = input.substring(0, headEnd).trimEnd()
        val tail = input.substring(safeTailStart).trimStart()
        return head + OMITTED_MARKER + tail
    }

    // Ordinary requests answer directly. Explicit, current-owner requests
    // may opt into bounded private reasoning; quoted history cannot enable it.
    fun modelPrompt(userText: String, thinkingRequested: Boolean = false): String =
        boundedInput(userText) + if (thinkingRequested) "\n/think" else "\n/no_think"

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
        val cleaned = value.dropLast(trim).trim()
        if (cleaned.length <= 16_000) return cleaned
        // Kotlin take() counts UTF-16 code units, not Unicode code points.
        // A long streamed answer can end exactly between an emoji's
        // surrogate halves. Never persist or speak a malformed character.
        val safeEnd = if (cleaned[15_999].isHighSurrogate() &&
            cleaned[16_000].isLowSurrogate()
        ) 15_999 else 16_000
        return cleaned.substring(0, safeEnd)
    }
}
