package com.astra.agent

/**
 * In-memory CDITS session state. Does not infer IQ or persist personal answers.
 * The caller owns consent, lifecycle, and any future encrypted persistence.
 */
internal class AstraCognitiveSession(
    val domain: AstraCognitiveAssessment.Domain,
    private val items: List<AstraCognitiveAssessment.Item>
) {
    init {
        require(items.isNotEmpty())
        require(items.all { it.domain == domain })
        require(items.map { it.id }.distinct().size == items.size)
    }

    private val results = mutableListOf<AstraCognitiveAssessment.Result>()
    val completed: List<AstraCognitiveAssessment.Result> get() = results.toList()
    val remaining: Int get() = items.size - results.size
    val finished: Boolean get() = remaining == 0
    val currentItem: AstraCognitiveAssessment.Item?
        get() = items.getOrNull(results.size)

    fun answer(attempt: AstraCognitiveAssessment.Attempt): AstraCognitiveAssessment.Result {
        val item = currentItem ?: throw IllegalStateException("Assessment session completed")
        require(attempt.itemId == item.id) { "Answer does not match current question" }
        return AstraCognitiveAssessment.evaluate(item, attempt).also { results.add(it) }
    }

    fun summary(): AstraCognitiveAssessment.Summary = AstraCognitiveAssessment.summary(results)

    fun recommendedDifficulty(current: Int): Int =
        AstraCognitiveAssessment.nextDifficulty(current, domain, results)
}
