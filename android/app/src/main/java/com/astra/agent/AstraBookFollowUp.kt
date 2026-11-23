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

    fun resolve(
        raw: String,
        currentChat: List<AstraChatArchive.Message>
    ): Result {
        val request = raw.trim()
        if (request.length > MAX_FOLLOW_UP || request.contains('\n')) {
            return Result.NotRequested
        }
        val lowered = request.lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trimEnd('?', '.', '!', ' ')
        if (lowered !in prompts) return Result.NotRequested

        // Only the most recent completed exchange can authorize continuity.
        // No search of archived chats, notes, tools or earlier unrelated turns.
        val recent = currentChat.takeLast(2)
        if (recent.size != 2 ||
            recent[0].role != "You" || recent[1].role != "Astra" ||
            AstraInterruptedReply.isIncomplete(recent[1].text) ||
            recent[1].text.isBlank()
        ) return Result.MissingAnchor

        val originalQuestion = AstraBookQuery.extract(recent[0].text)
            ?: return Result.MissingAnchor
        if (originalQuestion.length > MAX_QUESTION) return Result.MissingAnchor

        // Do not promote a failed/blocked previous request to an evidence
        // source. The next reply must retrieve fresh, matching local passages.
        val previousAnswer = recent[1].text.trim()
        if (previousAnswer.startsWith("Offline model unavailable:", true) ||
            previousAnswer.startsWith("Your book excerpts could not fit", true) ||
            previousAnswer.startsWith("Your book question is too long", true) ||
            previousAnswer.startsWith("The offline model did not produce", true)
        ) return Result.MissingAnchor

        val original = originalQuestion
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
