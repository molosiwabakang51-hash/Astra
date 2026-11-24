package com.astra.agent

/**
 * CDITS assessment kernel. Pure Kotlin, offline and independent of the LLM.
 * This is infrastructure only: it does not imply an assessment UI or IQ test.
 */
internal object AstraCognitiveAssessment {
    enum class Domain { FLUID, QUANTITATIVE, VERBAL, WORKING_MEMORY, METACOGNITION }
    data class Item(
        val id: String,
        val domain: Domain,
        val difficulty: Int,
        val prompt: String,
        val acceptedAnswers: Set<String>
    ) {
        init {
            require(id.isNotBlank() && prompt.isNotBlank())
            require(difficulty in 1..5)
            require(acceptedAnswers.isNotEmpty())
        }
    }
    data class Attempt(
        val itemId: String,
        val answer: String,
        val hints: Int = 0,
        val confidence: Int? = null,
        val elapsedMillis: Long? = null
    ) {
        init {
            require(hints >= 0)
            require(confidence == null || confidence in 0..100)
            require(elapsedMillis == null || elapsedMillis >= 0)
        }
    }
    data class Result(
        val itemId: String,
        val domain: Domain,
        val difficulty: Int,
        val correct: Boolean,
        val hints: Int,
        val confidence: Int?,
        val elapsedMillis: Long?
    )
    data class Summary(val answered: Int, val correct: Int, val accuracy: Double?)

    private fun normalize(raw: String): String =
        raw.trim().lowercase(java.util.Locale.ROOT)
            .replace(Regex("\\s+"), " ")

    /** Only exact, pre-keyed objective answers are graded. */
    fun evaluate(item: Item, attempt: Attempt): Result {
        require(item.id == attempt.itemId) { "Mismatched assessment item" }
        return Result(
            item.id, item.domain, item.difficulty,
            normalize(attempt.answer) in item.acceptedAnswers.map(::normalize),
            attempt.hints, attempt.confidence, attempt.elapsedMillis
        )
    }

    fun summary(results: List<Result>): Summary =
        Summary(results.size, results.count { it.correct },
            if (results.isEmpty()) null else results.count { it.correct }.toDouble() / results.size)

    /** A bounded, transparent recommendation; not a psychological ability estimate. */
    fun nextDifficulty(current: Int, recent: List<Result>): Int {
        require(current in 1..5)
        val evidence = recent.takeLast(5)
        if (evidence.size < 3) return current
        val independentlyCorrect = evidence.count { it.correct && it.hints == 0 }
        return when {
            independentlyCorrect >= 4 && evidence.size >= 4 -> (current + 1).coerceAtMost(5)
            evidence.count { !it.correct } >= 3 -> (current - 1).coerceAtLeast(1)
            else -> current
        }
    }
}
