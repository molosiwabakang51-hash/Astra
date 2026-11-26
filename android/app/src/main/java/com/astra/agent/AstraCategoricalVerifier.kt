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
        """^Categorical premises: ([A-Z a-z;]+)\. Conclusion: ([A-Z a-z]+)\. Does the conclusion follow\?(?: Reply YES or NO only\.)?$"""
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

    /**
     * Controlled English only: each predicate is a single, explicitly named
     * class. No stemming, singular/plural guessing, synonyms, or world facts.
     * The complete input must match a sequence of categorical premises and
     * an explicit entailment question. Unsupported language falls through.
     */
    data class NamedCheck(val check: Check, val mapping: Map<Char, String>)

    private val namedRequest = Regex(
        """^((?:(?:All|No|Some) [a-zA-Z]{2,24} are (?:not )?[a-zA-Z]{2,24}\. ){1,6})Does it follow that ((?:all|no|some) [a-zA-Z]{2,24} are (?:not )?[a-zA-Z]{2,24})\?(?: Reply YES or NO only\.)?$"""
    )
    private val namedStatement = Regex(
        """^(All|No|Some|all|no|some) ([a-zA-Z]{2,24}) are (not )?([a-zA-Z]{2,24})$"""
    )
    private val reserved = setOf("all", "no", "some", "not", "are", "and", "or",
        "if", "then", "only", "most", "many", "few", "the", "they", "them",
        "these", "those", "there", "here", "what", "who", "which", "both",
        "either", "neither", "any", "none", "always", "never", "true", "false")

    fun verifyControlledLanguage(question: String): NamedCheck? {
        if (question.length !in 35..650 || question.any { it == '\n' || it == '\r' }) return null
        val match = namedRequest.matchEntire(question.trim()) ?: return null
        val premises = match.groupValues[1].removeSuffix(". ").split(". ")
        if (premises.size !in 1..6) return null
        val mapping = linkedMapOf<String, Char>()
        fun translate(text: String): String? {
            val m = namedStatement.matchEntire(text) ?: return null
            val quantifier = m.groupValues[1].lowercase(java.util.Locale.ROOT)
            val negative = m.groupValues[3].isNotEmpty()
            if (negative && quantifier != "some") return null
            // Preserve lexical identity exactly; never conflate different
            // capitalizations or infer membership from related word forms.
            fun symbol(name: String): Char? {
                if (name.lowercase(java.util.Locale.ROOT) in reserved) return null
                if (name in mapping) return mapping[name]
                if (mapping.size >= 8) return null
                val next = 'A' + mapping.size
                mapping[name] = next
                return next
            }
            val left = symbol(m.groupValues[2]) ?: return null
            val right = symbol(m.groupValues[4]) ?: return null
            val prefix = quantifier.replaceFirstChar { it.uppercaseChar() }
            return "$prefix $left are " + (if (negative) "not " else "") + right
        }
        val translated = premises.map { translate(it) ?: return null }
        val conclusion = translate(match.groupValues[2]) ?: return null
        val result = verify("Categorical premises: " + translated.joinToString("; ") +
            ". Conclusion: $conclusion. Does the conclusion follow?") ?: return null
        return NamedCheck(result, mapping.entries.associate { it.value to it.key })
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
