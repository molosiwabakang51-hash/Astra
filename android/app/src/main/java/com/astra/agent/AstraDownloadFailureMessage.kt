package com.astra.agent

import java.util.Locale

/**
 * Maps public-model download failures to owner-actionable, privacy-safe
 * diagnostics. Never copy an exception's raw message into chat, preferences
 * or exported support reports: provider errors can include local file paths,
 * signed redirect URLs, or device-specific identifiers.
 */
internal object AstraDownloadFailureMessage {
    fun forOwner(failure: Exception): String {
        val source = failure.message.orEmpty().lowercase(Locale.ROOT)
        return when {
            "http 416" in source || "saved model offset was rejected" in source ->
                "The saved range was rejected by the model server. The unusable " +
                    "temporary partial was cleared; the next download starts fresh."
            "sha-256" in source || "checksum" in source ->
                "Saved or received model weights failed the official SHA-256 check. " +
                    "Unverified weights were not installed."
            "insufficient storage" in source || "not enough" in source ||
                "no space" in source ->
                "Not enough internal phone storage for the verified 1.7B model. " +
                    "Free space without uninstalling Astra or deleting your chats."
            "stopped sending" in source || "timed out" in source ||
                "timeout" in source || "connection reset" in source ->
                "The model connection stalled or timed out. Any usable saved " +
                    "partial is retained for a resumed transfer."
            "unexpected gguf size" in source || "inconsistent" in source ||
                "invalid byte range" in source ->
                "The model server returned an invalid size or byte range. " +
                    "No unverified model was installed."
            "http 429" in source || "too many requests" in source ->
                "The model host is rate limiting requests. Retry later; saved " +
                    "download data is retained where safe."
            "http " in source ->
                "The model server did not accept the request. Check your " +
                    "connection and retry when it is available."
            else ->
                "The offline model transfer could not finish. Check storage " +
                    "and connectivity; existing conversations and weights are intact."
        }
    }
}
