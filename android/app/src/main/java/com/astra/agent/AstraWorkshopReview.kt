package com.astra.agent

/**
 * Conservative local review schedule for authored course quizzes.
 *
 * Spaced reviews encourage retention; they are not a verified psychometric
 * measure of mastery. The same short quiz question may be memorized, so users
 * should also complete the independent worked exercises.
 *
 * A UTC-day integer allows durable local storage and deterministic unit tests.
 * Wrong answers are due immediately; repeated early successes cannot game
 * the interval by moving an already scheduled review further into the future.
 */
internal object AstraWorkshopReview {
    data class State(val streak: Int, val intervalDays: Int, val dueUtcDay: Int)

    private val intervals = intArrayOf(1, 3, 7, 14, 30, 60, 120)

    fun afterAnswer(
        todayUtcDay: Int,
        correct: Boolean,
        previous: State?
    ): State {
        require(todayUtcDay in 0..1_000_000)
        if (!correct) return State(0, 0, todayUtcDay)
        val before = previous ?: State(0, 0, todayUtcDay)
        // Do not advance a review on repeated same-day correct guesses.
        if (before.streak > 0 && before.dueUtcDay > todayUtcDay) return before
        val streak = (before.streak + 1).coerceAtMost(1_000)
        val interval = intervals[(streak - 1).coerceIn(0, intervals.lastIndex)]
        return State(streak, interval, todayUtcDay + interval)
    }

    fun due(todayUtcDay: Int, state: State?): Boolean =
        state != null && state.dueUtcDay <= todayUtcDay
}
