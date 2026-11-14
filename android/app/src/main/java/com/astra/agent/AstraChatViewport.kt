package com.astra.agent

/**
 * Presentation-only follow policy for Astra chat.
 *
 * Native inference may append text several times per second. Following the
 * newest message is useful until the user deliberately drags to read older
 * messages. No conversation content is passed into this policy.
 */
internal object AstraChatViewport {
    fun nextFollowState(
        wasFollowing: Boolean,
        userDragging: Boolean,
        canScrollForward: Boolean
    ): Boolean = when {
        userDragging -> false
        !canScrollForward -> true
        else -> wasFollowing
    }

    fun showReturnToLatest(
        following: Boolean,
        canScrollForward: Boolean
    ): Boolean = !following && canScrollForward
}
