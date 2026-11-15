package com.astra.agent

/** Explicit private book questions always take precedence over deterministic skills. */
internal object AstraBookQuery {
    private val pattern = Regex("""(?i)^(?:ask book|ask my books)\s+(.+)$""")
    fun extract(raw: String): String? =
        pattern.matchEntire(raw.trim())?.groupValues?.getOrNull(1)
            ?.trim()?.takeIf(String::isNotEmpty)
}
