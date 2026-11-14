package com.astra.agent

/**
 * Local-first model routing. Use the largest verified model available, with
 * no subscription, remote inference endpoint or hardware-based restriction.
 */
internal object AstraModelChoice {
    enum class Variant { ADVANCED, COMPACT, NONE }

    /** Return a public label, never exposing the private filesystem path. */
    fun loadedName(modelPath: String?): String? =
        when (modelPath?.substringAfterLast('/')) {
            AstraModelImport.ADVANCED_NAME -> "Qwen3 1.7B · Q4_K_M"
            AstraOnDeviceBrain.ASSET_NAME -> "Qwen3 0.6B · Q4_K_M"
            else -> null
        }

    /**
     * Preference order, not a guarantee that a file is valid. A candidate
     * can fail the checksum or JNI load check and a later candidate can work.
     */
    fun candidates(advancedAvailable: Boolean, compactAvailable: Boolean): List<Variant> =
        buildList {
            if (advancedAvailable) add(Variant.ADVANCED)
            if (compactAvailable) add(Variant.COMPACT)
        }

    fun choose(advancedAvailable: Boolean, compactAvailable: Boolean): Variant =
        candidates(advancedAvailable, compactAvailable).firstOrNull() ?: Variant.NONE
}
