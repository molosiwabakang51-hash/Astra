package com.astra.agent

/** Pure, testable policy for resuming a GGUF download over HTTP. */
internal object AstraDownloadRange {
    data class Plan(val append: Boolean, val totalBytes: Long)

    fun plan(
        statusCode: Int,
        requestedOffset: Long,
        contentRange: String?,
        contentLength: Long
    ): Plan {
        require(requestedOffset >= 0L) { "Invalid local download offset." }
        return when (statusCode) {
            200 -> {
                require(contentLength > 0L) { "Model download size unavailable." }
                // Server ignored Range; truncate and restart, never duplicate data.
                Plan(append = false, totalBytes = contentLength)
            }
            206 -> {
                val values = Regex("""bytes (\d+)-(\d+)/(\d+)""")
                    .matchEntire(contentRange.orEmpty().trim())
                    ?: error("Model server returned an invalid byte range.")
                val start = values.groupValues[1].toLongOrNull() ?: error("Invalid range start.")
                val end = values.groupValues[2].toLongOrNull() ?: error("Invalid range end.")
                val total = values.groupValues[3].toLongOrNull() ?: error("Invalid range size.")
                require(start == requestedOffset && end >= start && total > end) {
                    "Model server returned an inconsistent byte range."
                }
                if (contentLength >= 0) {
                    require(contentLength == end - start + 1L) {
                        "Model server returned an inconsistent transfer size."
                    }
                }
                Plan(append = start > 0L, totalBytes = total)
            }
            else -> error("Model download failed (HTTP $statusCode).")
        }
    }
}
