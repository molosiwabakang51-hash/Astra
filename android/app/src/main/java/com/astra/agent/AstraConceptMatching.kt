package com.astra.agent

/**
 * Bounded, conservative concept matching for explicit memory retrieval.
 * Lexical aliases are not embeddings or full semantic comprehension.
 * Exact evidence outranks aliases; two distinct concepts are required
 * before a synonym-only passage can qualify. Never expand identifiers.
 */
internal object AstraConceptMatching {
    private val groups = listOf(
        setOf("buy", "purchase", "purchased", "purchasing"),
        setOf("choose", "select", "selected", "selecting"),
        setOf("car", "automobile"),
        setOf("doctor", "physician"),
        setOf("lawyer", "attorney"),
        setOf("begin", "began", "start", "started", "commence", "commenced"),
        setOf("finish", "finished", "complete", "completed"),
        setOf("cancel", "cancelled", "canceled", "cancellation"),
        setOf("postpone", "postponed", "defer", "deferred"),
        setOf("annual", "yearly"),
        setOf("salary", "wage"),
        setOf("expense", "expenditure"),
        setOf("compute", "calculate", "calculated", "computed"),
        setOf("repair", "repaired", "fix", "fixed"),
        setOf("vacation", "holiday"),
        setOf("approximately", "roughly"),
        setOf("decrease", "decreased", "decline", "declined")
    )
    private val ids = groups.flatMapIndexed { index, group ->
        group.map { it to index }
    }.toMap()

    fun key(term: String): String = ids[term]?.let { "concept:$it" } ?: "word:$term"

    fun matches(left: String, right: String): Boolean =
        left == right || (ids[left] != null && ids[left] == ids[right])

    data class Match(val exact: Int, val alias: Int) {
        val count: Int get() = exact + alias
        val eligible: Boolean get() = exact > 0 || alias >= 2
        val weight: Int get() = exact * 14 + alias * 6
    }

    fun compare(query: List<String>, visible: Set<String>): Match {
        val groups = query.groupBy(::key)
        var exact = 0
        var alias = 0
        val visibleKeys = visible.map(::key).toSet()
        for ((key, terms) in groups) {
            if (terms.any(visible::contains)) exact++
            else if (key.startsWith("concept:") && key in visibleKeys) alias++
        }
        return Match(exact, alias)
    }
}
