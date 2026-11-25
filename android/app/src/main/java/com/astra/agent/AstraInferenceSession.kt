package com.astra.agent

/**
 * Pure state tracking for the single JNI model session.
 *
 * An interrupted generation is not a clean chat turn: native KV-cache
 * contents may contain an unfinished assistant response. The next request
 * must unload and reload the local model even when the user stays in the
 * same conversation. This does not delete GGUF weights or chat history.
 */
internal class AstraInferenceSession {
    private var loadedGeneration: Int? = null
    private var invalidated: Boolean = false
    private var completed: CompletedTurn? = null

    private data class CompletedTurn(
        val priorCount: Int,
        val priorTail: List<Pair<String, String>>,
        val userMessage: String,
        val reply: String
    )

    /**
     * The visible chat can advance through deterministic tools, courses or
     * failed requests without advancing native KV-cache. Only reuse a warm
     * session when the last completed native turn still ends the transcript.
     * Keep a bounded in-memory tail, never persist or log chat content.
     */
    fun checkTranscript(priorCount: Int, priorTail: List<Pair<String, String>>) {
        val last = completed ?: return
        val expectedCount = last.priorCount + 2
        val countMatches = priorCount == expectedCount ||
            (expectedCount > AstraConversationHistory.MAX_RETAINED_TURNS &&
                priorCount in (expectedCount - 2)..expectedCount)
        val same = countMatches && priorTail.size >= 2 &&
            priorTail.last() == ("assistant" to last.reply) &&
            priorTail[priorTail.lastIndex - 1] == ("user" to last.userMessage) &&
            priorTail.dropLast(2).takeLast(last.priorTail.size) == last.priorTail
        if (!same) invalidate()
    }

    fun recordCompletedTurn(
        priorCount: Int,
        priorTail: List<Pair<String, String>>,
        userMessage: String,
        reply: String
    ) {
        require(reply.isNotBlank())
        completed = CompletedTurn(priorCount, priorTail.takeLast(4).toList(), userMessage, reply)
    }

    fun requiresLoad(conversationGeneration: Int, nativeModelReady: Boolean): Boolean {
        if (!nativeModelReady) {
            // Native readiness was lost. Even if the engine later reports ready,
            // its KV-cache/session continuity cannot be assumed.
            invalidate()
            return true
        }
        return invalidated || loadedGeneration != conversationGeneration
    }

    fun markLoaded(conversationGeneration: Int) {
        loadedGeneration = conversationGeneration
        invalidated = false
        completed = null
    }

    fun invalidate() {
        loadedGeneration = null
        invalidated = true
        completed = null
    }
}
