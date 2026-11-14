package com.astra.agent

/**
 * Route only unsupported, non-sensitive local answers to optional cloud AI.
 * Local tools always run first, and the user's Cloud AI opt-in is mandatory.
 */
internal object AstraAiRouting {
    private const val UNSUPPORTED =
        "I cannot answer that reliably with Astra's current offline tools."

    fun shouldUseCloud(
        enabled: Boolean,
        endpointValid: Boolean,
        assistantText: String,
        sensitive: Boolean,
        hasAction: Boolean
    ): Boolean = enabled && endpointValid && !sensitive && !hasAction &&
        assistantText.startsWith(UNSUPPORTED)
}
