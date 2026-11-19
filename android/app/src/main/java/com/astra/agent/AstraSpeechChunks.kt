package com.astra.agent

/**
 * Divides long chat and course replies into bounded Android TTS utterances.
 *
 * The caller must choose/confirm the correct installed language/accent first.
 * This utility makes no network calls, changes no transcript, and holds
 * chunks only in memory until Android's audio queue finishes or is stopped.
 */
internal object AstraSpeechChunks {
    data class Plan(val utterances: List<String>, val truncated: Boolean) {
        val totalChars: Int get() = utterances.sumOf { it.length }
    }

    /**
     * Keep utterances comfortably beneath Android TextToSpeech's maximum
     * input length. capChunks bounds both memory and speech queue duration.
     * On truncation the full response is still visible in Chat.
     */
    fun partition(
        source: String,
        maxChars: Int = 3_000,
        capChunks: Int = 32
    ): Plan {
        require(maxChars in 16..3_500 && capChunks in 1..60)
        if (source.isBlank()) return Plan(emptyList(), false)
        val result = ArrayList<String>(minOf(8, capChunks))
        var cursor = 0
        while (cursor < source.length && result.size < capChunks) {
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            if (cursor >= source.length) break
            var end = minOf(source.length, cursor + maxChars)

            // Never split a UTF-16 surrogate pair. This is particularly
            // important for multilingual emoji and historic script glyphs.
            if (end < source.length && Character.isHighSurrogate(source[end - 1]) &&
                Character.isLowSurrogate(source[end])
            ) end--

            if (end < source.length) {
                val earliest = cursor + maxChars / 2
                var candidate = end - 1
                while (candidate >= earliest) {
                    val c = source[candidate]
                    if (c == '\n' || c == ' ' || c == '.' || c == '?' ||
                        c == '!' || c == ';' || c == '。' || c == '？' ||
                        c == '！' || c == '؛'
                    ) {
                        end = candidate + 1
                        break
                    }
                    candidate--
                }
            }
            if (end <= cursor) break
            val chunk = source.substring(cursor, end).trim()
            if (chunk.isNotEmpty()) result.add(chunk)
            cursor = end
        }
        return Plan(result, source.substring(cursor).any { !it.isWhitespace() })
    }
}
