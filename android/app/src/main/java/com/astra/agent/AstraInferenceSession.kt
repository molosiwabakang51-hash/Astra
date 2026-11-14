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

    fun requiresLoad(conversationGeneration: Int, nativeModelReady: Boolean): Boolean =
        invalidated || !nativeModelReady || loadedGeneration != conversationGeneration

    fun markLoaded(conversationGeneration: Int) {
        loadedGeneration = conversationGeneration
        invalidated = false
    }

    fun invalidate() {
        loadedGeneration = null
        invalidated = true
    }
}
