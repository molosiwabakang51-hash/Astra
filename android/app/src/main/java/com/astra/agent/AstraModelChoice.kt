package com.astra.agent

/**
 * Local-first model routing. Use the largest verified model available, with
 * no subscription, remote inference endpoint or hardware-based restriction.
 */
internal object AstraModelChoice {
    enum class Variant { ADVANCED, COMPACT, NONE }

    fun choose(advancedAvailable: Boolean, compactAvailable: Boolean): Variant =
        when {
            advancedAvailable -> Variant.ADVANCED
            compactAvailable -> Variant.COMPACT
            else -> Variant.NONE
        }
}
