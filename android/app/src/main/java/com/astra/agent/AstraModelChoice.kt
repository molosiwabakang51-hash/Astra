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

    fun choose(advancedAvailable: Boolean, compactAvailable: Boolean): Variant =
        when {
            advancedAvailable -> Variant.ADVANCED
            compactAvailable -> Variant.COMPACT
            else -> Variant.NONE
        }
}
