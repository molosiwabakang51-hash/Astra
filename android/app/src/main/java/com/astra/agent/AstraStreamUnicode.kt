package com.astra.agent

/**
 * Caps streamed model text in UTF-16 code units without severing a surrogate
 * pair at the cap. Once a chunk is truncated, callers must stop appending
 * subsequent chunks; this keeps the response's prefix stable.
 *
 * Native generation and token budgets remain unchanged. This helper does not
 * try to repair malformed Unicode received from upstream.
 */
internal object AstraStreamUnicode {
    /**
     * Safe incremental UTF-16 decoder for native streaming responses.
     *
     * JNI may deliver one half of a supplementary character in each chunk.
     * Never expose a dangling high surrogate to UI, backups or saved chats.
     * An unmatched half at the end of generation is replaced with U+FFFD,
     * while a valid pair consumes both UTF-16 units atomically.
     *
     * The output remains bounded, and truncation is permanent so later
     * stream chunks cannot change an earlier visible prefix.
     */
    internal class StreamingBuffer(private val maxUnits: Int) {
        init { require(maxUnits > 0) { "Output cap must be positive." } }

        private val text = StringBuilder()
        private var pendingHigh: Char? = null
        private var truncated = false

        fun append(chunk: String): Boolean {
            if (truncated || chunk.isEmpty()) return truncated
            var index = 0
            while (index < chunk.length && !truncated) {
                val next = chunk[index]
                val high = pendingHigh
                if (high != null) {
                    pendingHigh = null
                    if (next.isLowSurrogate()) {
                        appendPair(high, next)
                        index++
                        continue
                    }
                    appendCodeUnit('\uFFFD')
                    if (truncated) break
                }
                when {
                    next.isHighSurrogate() -> {
                        if (index + 1 < chunk.length) {
                            val following = chunk[index + 1]
                            if (following.isLowSurrogate()) {
                                appendPair(next, following)
                                index += 2
                                continue
                            }
                            appendCodeUnit('\uFFFD')
                        } else {
                            // Wait for the next native chunk; a half character
                            // must never be rendered or saved on its own.
                            if (text.length >= maxUnits) truncated = true
                            else pendingHigh = next
                        }
                    }
                    next.isLowSurrogate() -> appendCodeUnit('\uFFFD')
                    else -> appendCodeUnit(next)
                }
                index++
            }
            return truncated
        }

        fun snapshot(): String = text.toString()

        /** Flush an unmatched final half as U+FFFD, not malformed UTF-16. */
        fun finish(): String {
            if (pendingHigh != null && !truncated) {
                pendingHigh = null
                appendCodeUnit('\uFFFD')
            }
            return snapshot()
        }

        private fun appendPair(high: Char, low: Char) {
            if (maxUnits - text.length < 2) {
                truncated = true
                return
            }
            text.append(high).append(low)
        }

        private fun appendCodeUnit(codeUnit: Char) {
            if (text.length >= maxUnits) {
                truncated = true
                return
            }
            text.append(codeUnit)
        }
    }

    /** True if this chunk was truncated to the remaining capacity. */
    fun appendCapped(output: StringBuilder, chunk: String, maxUnits: Int): Boolean {
        require(maxUnits > 0) { "Output cap must be positive." }
        if (chunk.isEmpty()) return false
        val space = (maxUnits - output.length).coerceAtLeast(0)
        if (space == 0) return true

        var count = minOf(space, chunk.length)
        if (count < chunk.length &&
            count > 0 &&
            Character.isHighSurrogate(chunk[count - 1]) &&
            Character.isLowSurrogate(chunk[count])
        ) {
            count--
        }
        if (count > 0) output.append(chunk, 0, count)
        return count < chunk.length
    }
}
