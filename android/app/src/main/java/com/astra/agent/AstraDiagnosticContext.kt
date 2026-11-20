package com.astra.agent

/**
 * Isolates fixed public benchmark prompts from private owner book data and
 * optional synced local-library passages. Benchmark requests must be
 * reproducible irrespective of private documents and language preferences.
 *
 * This policy only applies when the internal caller explicitly marks a
 * diagnostic request. Regular conversation behavior remains unchanged.
 */
internal object AstraDiagnosticContext {
    fun allowPrivateBooks(
        explicitBookQuery: Boolean,
        booksEnabledInChat: Boolean,
        diagnosticIsolated: Boolean
    ): Boolean =
        !diagnosticIsolated && (explicitBookQuery || booksEnabledInChat)

    fun replyLanguage(configuredLanguage: String, diagnosticIsolated: Boolean): String =
        if (diagnosticIsolated) "auto" else configuredLanguage
}
