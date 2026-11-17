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
