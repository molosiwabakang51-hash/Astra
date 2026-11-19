package com.astra.agent

/**
 * The only supported conversation engine is locally verified Qwen3 1.7B.
 * Do not silently select the historical 0.6B weights when the larger model
 * is absent, corrupted or cannot be loaded. No remote inference is used.
 */
internal object AstraModelChoice {
    enum class Variant { ADVANCED, NONE }

    /** Only report a successfully loaded 1.7B model; never expose its path. */
    fun loadedName(modelPath: String?): String? =
        when (modelPath?.substringAfterLast('/')) {
            AstraModelImport.ADVANCED_NAME -> "Qwen3 1.7B · Q4_K_M"
            else -> null
        }

    /** Only the advanced 1.7B model can serve general conversation. */
    fun candidates(advancedAvailable: Boolean): List<Variant> =
        if (advancedAvailable) listOf(Variant.ADVANCED) else emptyList()

    fun choose(advancedAvailable: Boolean): Variant =
        candidates(advancedAvailable).firstOrNull() ?: Variant.NONE
}
