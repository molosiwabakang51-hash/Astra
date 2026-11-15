package com.astra.agent

/**
 * Selects eligible same-chat turns for recovery after a native-model reload.
 *
 * The app already retains at most 2,000 turns. Restricting recovery to 160
 * prevents the relevance ranker from ever finding older owner corrections.
 * This bounded window stays in Kotlin only; AstraConversationContext picks
 * up to eight short excerpts for Qwen's prompt, never the whole transcript.
 */
internal object AstraConversationHistory {
    const val MAX_RETAINED_TURNS = 2_000

    fun <T> forRecovery(history: List<T>): List<T> =
        if (history.size <= MAX_RETAINED_TURNS) history
        else history.takeLast(MAX_RETAINED_TURNS)
}
