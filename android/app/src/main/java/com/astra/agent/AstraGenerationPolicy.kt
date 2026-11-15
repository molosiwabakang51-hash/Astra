package com.astra.agent

import java.util.Locale

/**
 * Bounded response-length policy for the selected *on-device* GGUF.
 * A larger output allowance can improve completeness but not model reasoning,
 * factual accuracy or access to unprovided information.
 */
internal object AstraGenerationPolicy {
    enum class Depth { BRIEF, STANDARD, DETAILED }

    private val short = Regex(
        """(?i)\b(?:briefly|short answer|in one sentence|in a few words|one line|one word|tl;dr|be concise|keep it short)\b"""
    )
    private val long = Regex(
        """(?i)\b(?:in detail|detailed|thorough|comprehensive|step.by.step|explain fully|show your work|compare and contrast|derive|proof|analy[sz]e|essay|report|tutorial|teach me|walk me through|deep dive|write code|implement|give examples|course|lesson plan|study guide)\b"""
    )

    fun depth(rawQuestion: String): Depth {
        val question = rawQuestion.trim().lowercase(Locale.ROOT)
        if (question.isEmpty()) return Depth.BRIEF
        if (short.containsMatchIn(question)) return Depth.BRIEF
        if (long.containsMatchIn(question) || question.length > 700) {
            return Depth.DETAILED
        }
        return Depth.STANDARD
    }

    /**
     * Evidence-backed answers need sufficient tokens to discuss the quotes.
     * An explicit request for brevity always wins, and native inference
     * enforces the final token cap. No paid server or external model.
     */
    fun maxAnswerTokens(
        question: String,
        advancedModel: Boolean,
        hasReferences: Boolean = false
    ): Int {
        val tier = depth(question)
        val effective = if (hasReferences && tier == Depth.STANDARD)
            Depth.DETAILED else tier
        return when (effective) {
            Depth.BRIEF -> if (advancedModel) 256 else 192
            Depth.STANDARD -> if (advancedModel) 768 else 448
            Depth.DETAILED -> if (advancedModel) 1152 else 672
        }
    }
}
