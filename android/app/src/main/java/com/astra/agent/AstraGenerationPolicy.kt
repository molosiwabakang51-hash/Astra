package com.astra.agent

import java.util.Locale

/**
 * Device-only response depth selection. The native llama.cpp runtime still
 * enforces the generation cap; this policy adds no network or paid provider.
 */
internal object AstraGenerationPolicy {
    enum class Depth { BRIEF, STANDARD, DETAILED }

    private val brief = Regex(
        """(?i)\b(?:briefly|short answer|in one sentence|one line|in a few words|be concise|tl;dr)\b"""
    )
    private val detailed = Regex(
        """(?i)\b(?:in detail|detailed|thorough|comprehensive|step.by.step|explain fully|show your work|compare and contrast|derive|proof|analy[sz]e|essay|report|tutorial|teach me|walk me through|deep dive|write code|implement)\b"""
    )

    fun depth(question: String): Depth {
        val normalized = question.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return Depth.BRIEF
        if (brief.containsMatchIn(normalized)) return Depth.BRIEF
        if (detailed.containsMatchIn(normalized) || normalized.length > 700) {
            return Depth.DETAILED
        }
        return Depth.STANDARD
    }

    fun maxAnswerTokens(question: String, advancedModel: Boolean): Int =
        when (depth(question)) {
            Depth.BRIEF -> if (advancedModel) 256 else 192
            Depth.STANDARD -> if (advancedModel) 768 else 448
            Depth.DETAILED -> if (advancedModel) 1152 else 672
        }
}
