package com.astra.agent

import java.util.Locale

/**
 * Re-retrieve an owner-selected local book for a follow-up ONLY when the owner
 * explicitly asks to use their book again. Do not silently carry document
 * passages or model-generated answers into ordinary chat.
 */
internal object AstraBookFollowUp {
    sealed interface Result {
        data object NotRequested : Result
        data object MissingAnchor : Result
        data class Ready(val modelPrompt: String) : Result
    }

    private val prompts = setOf(
        "continue using my book",
        "continue from my book",
        "continue using my books",
        "go deeper using my book",
        "go deeper using my books",
        "explain that further using my book",
        "explain that further using my books",
        "show another example from my book",
        "show another example from my books",
        "show the evidence from my book",
        "show the evidence from my books",
        "what does my book say about that",
        "what do my books say about that"
    )

    private const val MAX_QUESTION = 560
    private const val MAX_FOLLOW_UP = 150

    private const val MAX_CONTIGUOUS_PAIRS = 4

    private fun isExplicitFollowUp(raw: String): Boolean {
        val request = raw.trim()
        if (request.length > MAX_FOLLOW_UP ||
            request.contains('\n') || request.contains('\r')
        ) return false
        return request.lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trimEnd('?', '.', '!', ' ') in prompts
    }

    private fun hasVerifiedBookSource(reply: String): Boolean =
        reply.isNotBlank() &&
            !AstraInterruptedReply.isIncomplete(reply) &&
            reply.contains("Retrieved local book passages (verify originals):\n") &&
            reply.contains("\n[B1] ")

    fun resolve(
        raw: String,
        currentChat: List<AstraChatArchive.Message>
    ): Result {
        val request = raw.trim()
        if (!isExplicitFollowUp(request)) return Result.NotRequested

        // Walk only the owner's MOST RECENT uninterrupted chain of completed,
        // citation-backed same-chat book requests. This permits several
        // explicit follow-ups but never crosses an unrelated user question,
        // another conversation or a stopped native generation.
        var cursor = currentChat.size - 2
        var originalQuestion: String? = null
        var examined = 0
        while (cursor >= 0 && examined < MAX_CONTIGUOUS_PAIRS) {
            val user = currentChat[cursor]
            val answer = currentChat.getOrNull(cursor + 1)
                ?: return Result.MissingAnchor
            if (user.role != "You" || answer.role != "Astra" ||
                !hasVerifiedBookSource(answer.text)
            ) return Result.MissingAnchor

            val initial = AstraBookQuery.extract(user.text)
            if (initial != null) {
                originalQuestion = initial
                break
            }
            // Past explicit book continuations are owner consent only for
            // that local exchange; each NEW follow-up is approved afresh.
            if (!isExplicitFollowUp(user.text)) return Result.MissingAnchor
            cursor -= 2
            examined++
        }
        val initialQuestion = originalQuestion ?: return Result.MissingAnchor
        if (initialQuestion.length > MAX_QUESTION) return Result.MissingAnchor

        val original = initialQuestion
            .replace(Regex("""[\r\n\t\u0000-\u001f]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.', '?', '!')

        // Keep the explicit book command so the normal book search, consent,
        // excerpt-fit and citation verification safeguards still apply.
        // The previous AI response is deliberately NOT copied to the prompt:
        // source material must be retrieved again and attributed correctly.
        val groundedQuestion = buildString {
            append(original)
            append(". Follow-up request: ")
            append(request)
            append(". Use newly retrieved book excerpts as evidence; ")
            append("if they are insufficient, say so. ")
            append("Do not rely on the earlier model response as a source.")
        }
        return if (AstraReferenceFit.permitsBook(groundedQuestion)) {
            Result.Ready("ask my books $groundedQuestion")
        } else Result.MissingAnchor
    }
}
