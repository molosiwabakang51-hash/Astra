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
    // A reasoning request is not necessarily a multi-step imperative.
    // Recognize common deductive, counterfactual and consistency tasks
    // without requiring specific subject matter or benchmark answers.
    private val deductive = Regex(
        """^(?:please )?(?:(?:can|could|would) you )?""" +
            """(?:determine|test|check|examine|evaluate|assess|identify|find|""" +
            """is|does|do|would|what)\b.{0,220}\b""" +
            """(?:premises?|conclusions?|contradictions?|inconsistenc(?:y|ies)|""" +
            """counterexamples?|logical(?:ly)?|necessary|sufficient)\b.{0,180}\b""" +
            """(?:follows?|follow|valid|invalid|consistent|inconsistent|""" +
            """implies?|imply|prove|disprove|given|assuming|if|because|""" +
            """assumptions?|evidence)\b""",
        RegexOption.IGNORE_CASE
    )
    private val tradeoffQuestion = Regex(
        """^(?:given|assuming|under|which|what|how|would|should)\b.{0,260}\b""" +
            """(?:constraints?|tradeoffs?|alternatives?|opportunity cost|risks?)\b.{0,180}\b""" +
            """(?:choose|prefer|decide|better|best|optimal|recommend|prioriti[sz]e|""" +
            """balance|evaluate|compare|justify)\b""",
        RegexOption.IGNORE_CASE
    )
    // Do not interpret a request to translate or define a quoted reasoning
    // phrase as a request to spend the larger reasoning budget.
    private val languageOrQuotation = Regex(
        """^(?:please )?(?:(?:can|could|would) you )?""" +
            """(?:translate|define|paraphrase|pronounce|rewrite|""" +
            """what does the (?:word|phrase|expression|sentence)|""" +
            """what is the meaning of|explain the (?:word|phrase|expression))\b""",
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
            explicitFast.containsMatchIn(current) ||
            languageOrQuotation.containsMatchIn(current)
        ) return false
        // Only recognizable multi-constraint tasks trigger expensive thought.
        // Ordinary chat, lessons and translations retain the fast path.
        return planning.containsMatchIn(current) ||
            multistep.containsMatchIn(current) ||
            conditional.containsMatchIn(current) ||
            deductive.containsMatchIn(current) ||
            tradeoffQuestion.containsMatchIn(current)
    }
}
