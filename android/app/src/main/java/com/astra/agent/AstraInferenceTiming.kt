package com.astra.agent

/**
 * Last successful local Qwen request timing. Captures monotonic durations
 * and native output chunk counts ONLY — never prompts, tokens, paths, model
 * hashes, chat IDs or other owner content.
 *
 * First native output may contain hidden Qwen thinking tokens, so it is NOT
 * necessarily the first user-visible character. Native chunks are not tokens.
 */
internal object AstraInferenceTiming {
    data class Sample(
        val firstNativeChunkMs: Long,
        val requestTotalMs: Long,
        val nativeChunks: Int
    )

    fun sample(
        startedNs: Long,
        firstNativeNs: Long?,
        finishedNs: Long,
        nativeChunks: Int
    ): Sample? {
        // System.nanoTime() has an arbitrary origin and can be negative.
        // Validate ordering, not the sign of the monotonic timestamps.
        if (firstNativeNs == null || nativeChunks <= 0 ||
            finishedNs < firstNativeNs || firstNativeNs < startedNs
        ) return null
        return Sample(
            firstNativeChunkMs = (firstNativeNs - startedNs) / 1_000_000L,
            requestTotalMs = (finishedNs - startedNs) / 1_000_000L,
            nativeChunks = nativeChunks
        )
    }

    fun describe(sample: Sample?): String =
        if (sample == null) {
            "No successful native generation timed in this app process."
        } else {
            "Last successful offline AI request: " +
                "first native output after ${sample.firstNativeChunkMs} ms; " +
                "full request ${sample.requestTotalMs} ms; " +
                "${sample.nativeChunks} native output chunks. " +
                "Includes model startup if needed. Native chunks are not tokens, " +
                "and first native output is not necessarily the first visible text. " +
                "No chat content was recorded."
        }
}
