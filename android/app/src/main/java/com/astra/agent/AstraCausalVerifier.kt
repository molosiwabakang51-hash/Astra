package com.astra.agent

/** Exact reference for explicit deterministic binary structural equations.
 * Does not interpret unrestricted prose or establish real-world causation. */
internal object AstraCausalVerifier {
    enum class Verdict { YES, NO, UNKNOWN, INCONSISTENT }
    data class Checked(val verdict: Verdict, val compatible: Int, val examined: Int,
                       val onWitness: String?, val offWitness: String?)
    private data class Rule(val node: Char, val op: String, val args: List<Char>)
    private val syntax = Regex(
        """Causal model: inputs ([A-Z](?:,[A-Z]){0,5}); rules (.+?); observe (.+?); force (.+?)\. Is ([A-Z]) ON\?(?: Reply with YES, NO or UNKNOWN\.)?"""
    )
    private val rulePattern = Regex("""([A-Z])=(AND|OR|XOR|NOT|COPY)\(([A-Z](?:,[A-Z])?)\)""")
    private val assignment = Regex("""([A-Z])=([01])""")

    private fun assignments(raw: String, nodes: Set<Char>): Map<Char, Boolean>? {
        if (raw == "none") return emptyMap()
        val parts = raw.split(",")
        if (parts.size > nodes.size) return null
        val result = linkedMapOf<Char, Boolean>()
        for (part in parts) {
            val match = assignment.matchEntire(part) ?: return null
            val node = match.groupValues[1].single()
            if (node !in nodes || node in result) return null
            result[node] = match.groupValues[2] == "1"
        }
        return result
    }

    fun verify(raw: String): Checked? {
        if (raw.length !in 35..500 || raw != raw.trim() || raw.any { it.isISOControl() }) return null
        val match = syntax.matchEntire(raw) ?: return null
        val inputs = match.groupValues[1].split(",").map { it.single() }
        if (inputs.distinct().size != inputs.size) return null
        val rules = mutableListOf<Rule>()
        val declared = inputs.toMutableSet()
        val rulesText = match.groupValues[2]
        if (rulesText != "none") {
            for (text in rulesText.split(";")) {
                val part = rulePattern.matchEntire(text.trim()) ?: return null
                val node = part.groupValues[1].single()
                val op = part.groupValues[2]
                val args = part.groupValues[3].split(",").map { it.single() }
                val arity = if (op == "NOT" || op == "COPY") 1 else 2
                if (node in declared || args.size != arity || args.any { it !in declared }) return null
                declared.add(node)
                if (declared.size > 10) return null
                rules.add(Rule(node, op, args))
            }
        }
        val observed = assignments(match.groupValues[3], declared) ?: return null
        val forced = assignments(match.groupValues[4], declared) ?: return null
        val target = match.groupValues[5].single()
        if (target !in declared) return null
        fun world(mask: Int, intervention: Map<Char, Boolean>): Map<Char, Boolean> {
            val values = linkedMapOf<Char, Boolean>()
            for ((index, node) in inputs.withIndex()) {
                values[node] = intervention[node] ?: ((mask and (1 shl index)) != 0)
            }
            for (rule in rules) {
                val a = values.getValue(rule.args[0])
                val b = rule.args.getOrNull(1)?.let { values.getValue(it) } ?: false
                values[rule.node] = intervention[rule.node] ?: when (rule.op) {
                    "AND" -> a && b
                    "OR" -> a || b
                    "XOR" -> a xor b
                    "NOT" -> !a
                    "COPY" -> a
                    else -> error("Validated operator required")
                }
            }
            return values
        }
        var count = 0
        var on: String? = null
        var off: String? = null
        val examined = 1 shl inputs.size
        for (mask in 0 until examined) {
            val original = world(mask, emptyMap())
            if (!observed.all { (node, value) -> original[node] == value }) continue
            count++
            // Evidence constrains the original world, not the forced world.
            val changed = world(mask, forced)
            val witness = inputs.joinToString(",") { it.toString() + "=" + (if (original.getValue(it)) 1 else 0) }
            if (changed.getValue(target)) {
                if (on == null) on = witness
            } else if (off == null) off = witness
        }
        val verdict = when {
            count == 0 -> Verdict.INCONSISTENT
            on != null && off != null -> Verdict.UNKNOWN
            on != null -> Verdict.YES
            else -> Verdict.NO
        }
        return Checked(verdict, count, examined, on, off)
    }

    fun report(checked: Checked, previous: String? = null): String? {
        val scalar = previous?.trimEnd('.', '!')?.uppercase(java.util.Locale.ROOT)
        if (previous != null && scalar !in setOf("YES", "NO", "UNKNOWN")) return null
        val result = if (checked.verdict == Verdict.INCONSISTENT) {
            "Original observations are inconsistent; no compatible original world exists."
        } else if (previous == null) {
            "reference result: " + checked.verdict + "."
        } else if (scalar == checked.verdict.name) {
            "correct."
        } else {
            "incorrect; corrected result: " + checked.verdict + "."
        }
        val witnesses = if (checked.verdict == Verdict.UNKNOWN)
            "\nOriginal-input witnesses: ON under " + checked.onWitness + "; OFF under " + checked.offWitness + "." else ""
        return "INDEPENDENT CAUSAL CHECK — " + result + "\n" +
            "Examined " + checked.examined + " original input assignments; " + checked.compatible + " compatible with the original observations." +
            witnesses + "\nOnly the explicitly supplied deterministic binary causal model was verified. " +
            "This does not establish real-world causation or validate a natural-language translation."
    }
}
