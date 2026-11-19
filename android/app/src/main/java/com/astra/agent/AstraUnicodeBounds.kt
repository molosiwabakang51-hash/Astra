package com.astra.agent

/**
 * Truncate display-only strings without splitting UTF-16 surrogate pairs.
 * Kotlin String length is measured in 16-bit code units. A raw take()/slice()
 * through an emoji or supplementary writing-system character creates an
 * isolated surrogate, which later renders as a replacement glyph and can
 * damage exported chat titles and search previews.
 */
internal object AstraUnicodeBounds {
    fun prefix(value: String, maxUnits: Int): String {
        require(maxUnits >= 0)
        if (value.length <= maxUnits) return value
        val safeEnd = if (maxUnits > 0 &&
            value[maxUnits - 1].isHighSurrogate() &&
            value[maxUnits].isLowSurrogate()
        ) maxUnits - 1 else maxUnits
        return value.substring(0, safeEnd)
    }

    fun slice(value: String, start: Int, end: Int): String {
        require(start >= 0 && start <= end && end <= value.length)
        val safeStart = if (start > 0 && start < value.length &&
            value[start].isLowSurrogate() &&
            value[start - 1].isHighSurrogate()
        ) start - 1 else start
        val safeEnd = if (end > 0 && end < value.length &&
            value[end - 1].isHighSurrogate() &&
            value[end].isLowSurrogate()
        ) end - 1 else end
        return value.substring(safeStart, safeEnd.coerceAtLeast(safeStart))
    }
}
