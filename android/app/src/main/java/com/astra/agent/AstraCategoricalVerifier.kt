package com.astra.agent

/**
 * Independent monadic categorical logic over a nonempty domain.
 * Explicit single-letter predicates only; no existential import for universal
 * statements and no inferred natural-language mapping. Every possible object
 * membership type is enumerated, then existential witnesses are constructed.
 */
internal object AstraCategoricalVerifier {
    enum class Verdict { ENTAILED, COUNTEREXAMPLE, INCONSISTENT_PREMISES }
    data class Check(val verdict: Verdict, val types: Int, val witness: String? = null)

    private enum class Kind { ALL, NONE, SOME, SOME_NOT }
    private data class Statement(val kind: Kind, val a: Char, val b: Char) {
        fun negated(): Statement = copy(kind = when (kind) {
            Kind.ALL -> Kind.SOME_NOT
            Kind.NONE -> Kind.SOME
            Kind.SOME -> Kind.NONE
            Kind.SOME_NOT -> Kind.ALL
        })
        val existential get() = kind == Kind.SOME || kind == Kind.SOME_NOT
        fun matches(type: Int): Boolean {
            val left = type and (1 shl (a - 'A')) != 0
            val right = type and (1 shl (b - 'A')) != 0
            return when (kind) {
                Kind.ALL -> !left || right
                Kind.NONE -> !left || !right
                Kind.SOME -> left && right
                Kind.SOME_NOT -> left && !right
            }
        }
    }

    private val request = Regex(
        """^Categorical premises: ([A-Z a-z;]+)\. Conclusion: ([A-Z a-z]+)\. Does the conclusion follow\?$"""
    )
    private val all = Regex("""All ([A-Z]) are ([A-Z])""")
    private val none = Regex("""No ([A-Z]) are ([A-Z])""")
    private val some = Regex("""Some ([A-Z]) are ([A-Z])""")
    private val someNot = Regex("""Some ([A-Z]) are not ([A-Z])""")

    private fun parse(raw: String): Statement? {
        for ((kind, regex) in listOf(Kind.ALL to all, Kind.NONE to none,
            Kind.SOME to some, Kind.SOME_NOT to someNot)) {
            val match = regex.matchEntire(raw.trim()) ?: continue
            return Statement(kind, match.groupValues[1].single(), match.groupValues[2].single())
        }
        return null
    }

    /**
     * A model exists iff at least one type satisfies all universal statements
     * and every existential statement has an allowed witness type. With only
     * unary predicates, witnesses can coexist as distinct objects.
     */
    private fun model(statements: List<Statement>, types: List<Int>): List<Int>? {
        val universal = statements.filterNot { it.existential }
        val allowed = types.filter { type -> universal.all { it.matches(type) } }
        if (allowed.isEmpty()) return null
        val witnesses = mutableListOf<Int>()
        for (statement in statements.filter { it.existential }) {
            witnesses.add(allowed.firstOrNull { statement.matches(it) } ?: return null)
        }
        // Classical first-order domains are nonempty, even when all named
        // predicates are empty. One allowed object is sufficient in that case.
        if (witnesses.isEmpty()) witnesses.add(allowed.first())
        return witnesses.distinct()
    }

    fun verify(question: String): Check? {
        if (question.length !in 40..280) return null
        val match = request.matchEntire(question.trim()) ?: return null
        val pieces = match.groupValues[1].split(';')
        if (pieces.size !in 1..6) return null
        val premises = pieces.map { parse(it) ?: return null }
        val conclusion = parse(match.groupValues[2]) ?: return null
        val symbols = (premises + conclusion).flatMap { listOf(it.a, it.b) }.distinct().sorted()
        if (symbols.size !in 1..8) return null
        val types = (0 until (1 shl symbols.size)).map { bits ->
            symbols.withIndex().fold(0) { mask, (i, symbol) ->
                if (bits and (1 shl i) != 0) mask or (1 shl (symbol - 'A')) else mask
            }
        }
        if (model(premises, types) == null)
            return Check(Verdict.INCONSISTENT_PREMISES, types.size)
        val countermodel = model(premises + conclusion.negated(), types)
            ?: return Check(Verdict.ENTAILED, types.size)
        val witness = countermodel.mapIndexed { i, type ->
            "object ${i + 1}: " + symbols.joinToString(", ") { symbol ->
                "$symbol=" + (type and (1 shl (symbol - 'A')) != 0)
            }
        }.joinToString("; ")
        return Check(Verdict.COUNTEREXAMPLE, types.size, witness)
    }
}
