package com.astra.agent

/**
 * The only supported conversation engine is locally verified Qwen3 1.7B.
 * Do not silently select the historical 0.6B weights when the larger model
 * is absent, corrupted or cannot be loaded. No remote inference is used.
 */
internal object AstraModelChoice {
    enum class Variant { ADVANCED, NONE }

    /** Only the advanced 1.7B model can serve general conversation. */
    fun candidates(advancedAvailable: Boolean): List<Variant> =
        if (advancedAvailable) listOf(Variant.ADVANCED) else emptyList()

    fun choose(advancedAvailable: Boolean): Variant =
        candidates(advancedAvailable).firstOrNull() ?: Variant.NONE
}
