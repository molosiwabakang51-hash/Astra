package com.astra.agent

/**
 * Conservative on-device routing between Qwen3's /think and /no_think.
 *
 * Automatic mode is OWNER-OPT-IN and disabled by default until phone A/B
 * evidence shows a net benefit. Small models can exhaust their answer budget
 * in /think; this policy is not a correctness guarantee.
 */
internal object AstraAdaptiveReasoning {
    private val planning = Regex(
        """\b(?:compare|evaluate|assess|weigh)\b.*\b""" +
            """(?:tradeoffs?|risks?|constraints?|assumptions?|alternatives?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val multistep = Regex(
        """\b(?:first|step one|step 1)\b.{0,180}\b(?:then|next|step two|step 2)\b""",
        RegexOption.IGNORE_CASE
    )
    private val conditional = Regex(
        """\bif\b.{10,180}\b(?:and|but|unless)\b.{8,160}\b(?:then|what|would|should|can)\b""",
        RegexOption.IGNORE_CASE
    )
    private val explicitFast = Regex(
        """\b(?:reply only|answer only|just the answer|no explanation|do not explain|""" +
            """without explanation|do not think|don't think|fast mode)\b""",
        RegexOption.IGNORE_CASE
    )
    private val untrustedSource = Regex(
        """(?m)(?:^\s*(?:previous (?:user|astra):|SYSTEM:|USER:)|""" +
            """^\s*>|^\s*retrieved local book passages)""",
        RegexOption.IGNORE_CASE
    )

    fun select(
        raw: String,
        automaticEnabled: Boolean,
        diagnosticIsolated: Boolean = false
    ): Boolean {
        if (diagnosticIsolated) return false
        // Deliberate current-owner mode requests always take precedence.
        if (AstraReasoningMode.enabled(raw)) return true
        if (!automaticEnabled) return false
        val current = raw.trim()
        // Long imported content or quoted transcripts are not authoritative
        // instructions for model settings. Never infer mode from them.
        if (current.length !in 45..800 || '\n' in current ||
            untrustedSource.containsMatchIn(current) || current.contains("```")
        ) return false
        if (AstraGenerationPolicy.depth(current) == AstraGenerationPolicy.Depth.BRIEF ||
            explicitFast.containsMatchIn(current)
        ) return false
        // Only recognizable multi-constraint tasks trigger expensive thought.
        // Ordinary chat, lessons and translations retain the fast path.
        return planning.containsMatchIn(current) ||
            multistep.containsMatchIn(current) ||
            conditional.containsMatchIn(current)
    }
}
