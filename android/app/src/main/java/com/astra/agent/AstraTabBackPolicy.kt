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

    /**
     * Android Back at the genuine Chat root must not finish the Activity:
     * show the existing Astra navigation surface instead. That surface has
     * a Back dismissal, so the owner can explore all six destinations and
     * return without losing the current conversation or model session.
     */
    fun rootOpensNavigation(currentTab: Int, history: List<Int>): Boolean =
        currentTab == CHAT && previous(currentTab, history) == null

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
