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

    // A long pasted source can contain its own instructions about brevity.
    // When the owner explicitly marks the final request, apply response-length
    // cues to that request rather than to quoted or pasted source material.
    private val finalRequestMarker = Regex(
        """(?im)^[ \t]{0,12}(?:final question|now answer|my question|latest request)[ \t]*:[ \t]*"""
    )

    fun depth(rawQuestion: String): Depth {
        val full = rawQuestion.trim()
        val latest = finalRequestMarker.findAll(full).lastOrNull()?.let {
            full.substring(it.range.last + 1).trim()
        }?.takeIf { it.isNotEmpty() } ?: full
        val question = latest.lowercase(Locale.ROOT)
        if (question.isEmpty()) return Depth.BRIEF
        if (short.containsMatchIn(question)) return Depth.BRIEF
        if (long.containsMatchIn(question) || question.length > 700) {
            return Depth.DETAILED
        }
        return Depth.STANDARD
    }

    /** Short on-device diagnostics need a small, predictable output allowance. */
    const val DIAGNOSTIC_TOKEN_LIMIT = 96

    /**
     * A diagnostic cap can reduce generation work but must never expand or
     * disable the normal response budget. It is only applied for explicit
     * owner-initiated spot checks, not ordinary chat.
     */
    fun effectiveBudget(normal: Int, diagnosticMaximum: Int?): Int {
        require(normal in 32..1152) { "Invalid native model token budget." }
        return diagnosticMaximum?.coerceIn(32, normal) ?: normal
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
