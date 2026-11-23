package com.astra.agent

/**
 * Prevent expensive chat Markdown/layout recomposition on every native token.
 * It does not drop tokens: final text is always delivered when generation
 * finishes, including when the last partial was throttled.
 */
internal object AstraStreamingUiPolicy {
    const val MIN_PUBLISH_INTERVAL_NS = 550_000_000L

    fun intervalElapsed(nowNs: Long, lastNs: Long): Boolean =
        lastNs == 0L || (nowNs >= lastNs &&
            nowNs - lastNs >= MIN_PUBLISH_INTERVAL_NS)

    fun hasNewVisibleText(partial: String, previous: String): Boolean =
        partial.isNotBlank() && partial != previous
}
