package com.astra.agent

/**
 * Independent, bounded truth-table verification for explicitly formalized
 * propositional arguments. No language interpretation, model answer repair,
 * network or state-changing actions. Unknown syntax fails closed.
 */
internal object AstraPropositionalVerifier {
    enum class Verdict { ENTAILED, COUNTEREXAMPLE, INCONSISTENT_PREMISES }
    data class Check(val verdict: Verdict, val assignments: Int, val witness: String? = null)

    // Require an entire explicitly structured owner question. Never extract
    // a formula from quoted prose, a document, or a generated model answer.
    private val request = Regex(
        """^Premises:\s*([A-Z!&|^()<>\-\s;]+)\.\s*Conclusion:\s*([A-Z!&|^()<>\-\s]+)\.\s*Does the conclusion follow\?\s*(?:Reply YES or NO only\.)?$"""
    )

    private sealed interface Expr {
        fun eval(mask: Int): Boolean
    }
    private data class Variable(val bit: Int) : Expr {
        override fun eval(mask: Int): Boolean = mask and (1 shl bit) != 0
    }
    private data class Not(val child: Expr) : Expr {
        override fun eval(mask: Int) = !child.eval(mask)
    }
    private data class Binary(val op: Char, val left: Expr, val right: Expr) : Expr {
        override fun eval(mask: Int): Boolean = when (op) {
            '&' -> left.eval(mask) && right.eval(mask)
            '|' -> left.eval(mask) || right.eval(mask)
            '>' -> !left.eval(mask) || right.eval(mask)
            '^' -> left.eval(mask) != right.eval(mask)
            '=' -> left.eval(mask) == right.eval(mask)
            else -> error("Invalid internal operator")
        }
    }

    private class Invalid : RuntimeException()

    private class Parser(private val input: String, private val variables: MutableSet<Char>) {
        private var pos = 0

        private fun consume(token: String): Boolean {
            while (pos < input.length && input[pos].isWhitespace()) pos++
            if (!input.startsWith(token, pos)) return false
            pos += token.length
            return true
        }

        fun parse(): Expr {
            val expr = equivalence()
            while (pos < input.length && input[pos].isWhitespace()) pos++
            if (pos != input.length) throw Invalid()
            return expr
        }

        // !, &, ^, |, right-associative ->, then <->.
        // A bare chained biconditional is intentionally rejected: require
        // parentheses rather than guess an intended reading of equivalence.
        private fun equivalence(): Expr {
            val left = implication()
            return if (consume("<->")) Binary('=', left, implication()) else left
        }
        private fun implication(): Expr {
            val left = disjunction()
            return if (consume("->")) Binary('>', left, implication()) else left
        }
        private fun disjunction(): Expr {
            var expr = exclusiveOr()
            while (consume("|")) expr = Binary('|', expr, exclusiveOr())
            return expr
        }
        private fun exclusiveOr(): Expr {
            var expr = conjunction()
            while (consume("^")) expr = Binary('^', expr, conjunction())
            return expr
        }
        private fun conjunction(): Expr {
            var expr = atom()
            while (consume("&")) expr = Binary('&', expr, atom())
            return expr
        }
        private fun atom(): Expr {
            if (consume("!")) return Not(atom())
            if (consume("(")) {
                val expr = equivalence()
                if (!consume(")")) throw Invalid()
                return expr
            }
            val c = input.getOrNull(pos) ?: throw Invalid()
            if (c !in 'A'..'Z') throw Invalid()
            pos++
            variables.add(c)
            if (variables.size > 8) throw Invalid()
            return Variable(c - 'A')
        }
    }

    fun verify(question: String): Check? {
        // Bound parsing recursion and work before parsing; at most 256 truth
        // assignments, six premises, and 240 UTF-16 input units.
        if (question.length !in 20..240) return null
        val match = request.matchEntire(question.trim()) ?: return null
        val premises = match.groupValues[1].split(';')
        if (premises.size !in 1..6 || premises.any { it.isBlank() }) return null
        val vars = sortedSetOf<Char>()
        val parsed: List<Expr>
        val conclusion: Expr
        try {
            parsed = premises.map { Parser(it, vars).parse() }
            conclusion = Parser(match.groupValues[2], vars).parse()
        } catch (_: Invalid) {
            return null
        }
        val ordered = vars.toList()
        val total = 1 shl ordered.size
        var satisfying = 0
        var counterexample: String? = null
        for (assignment in 0 until total) {
            var mask = 0
            for ((i, c) in ordered.withIndex()) {
                if (assignment and (1 shl i) != 0) mask = mask or (1 shl (c - 'A'))
            }
            if (!parsed.all { it.eval(mask) }) continue
            satisfying++
            if (!conclusion.eval(mask) && counterexample == null) {
                counterexample = ordered.joinToString(", ") { c ->
                    "$c=" + if (mask and (1 shl (c - 'A')) != 0) "true" else "false"
                }
            }
        }
        return when {
            satisfying == 0 -> Check(Verdict.INCONSISTENT_PREMISES, total)
            counterexample != null -> Check(Verdict.COUNTEREXAMPLE, total, counterexample)
            else -> Check(Verdict.ENTAILED, total)
        }
    }
}
