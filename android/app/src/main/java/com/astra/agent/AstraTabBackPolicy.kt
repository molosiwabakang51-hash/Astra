package com.astra.agent

/**
 * One-step Android Back decisions for the six top-level Astra screens.
 *
 * Chat is a destination, not always the root. If the owner navigates from
 * Settings to Chat, the next Back should return to Settings instead of
 * unexpectedly finishing the Android Activity.
 *
 * The history is UI-only; no private messages or task content is retained.
 */
internal object AstraTabBackPolicy {
    private const val CHAT = 0
    private const val LAST_DESTINATION = 5

    data class Target(val tab: Int, val remainingHistory: Int)

    fun previous(currentTab: Int, history: List<Int>): Target? {
        // Skip duplicate/stale entries instead of getting stuck on a Back
        // action that selects the same screen again.
        for (index in history.indices.reversed()) {
            val candidate = history[index]
            if (candidate in CHAT..LAST_DESTINATION && candidate != currentTab) {
                return Target(candidate, index)
            }
        }
        // If the app opened directly on a saved non-Chat tab after process
        // recreation, there may be no in-session history to restore.
        return if (currentTab != CHAT) Target(CHAT, 0) else null
    }
}
