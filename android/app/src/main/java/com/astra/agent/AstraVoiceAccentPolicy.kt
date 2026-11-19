package com.astra.agent

import java.util.Locale

/**
 * Android TTS locale selection must be region-exact: setLanguage(Locale)
 * is allowed by Android to substitute a different regional accent.
 * This policy is pure Kotlin, so it is testable without a device or TTS.
 */
internal object AstraVoiceAccentPolicy {
    data class Candidate(
        val name: String,
        val localeTag: String,
        val networkRequired: Boolean,
        val installed: Boolean,
        val quality: Int
    )

    fun sameAccent(requestedTag: String, availableTag: String): Boolean {
        val requested = Locale.forLanguageTag(requestedTag)
        val available = Locale.forLanguageTag(availableTag)
        if (requested.language.isBlank() || requested.country.isBlank() ||
            available.language.isBlank() || available.country.isBlank()) return false
        return requested.language.equals(available.language, ignoreCase = true) &&
            requested.country.equals(available.country, ignoreCase = true) &&
            (requested.script.isBlank() ||
                requested.script.equals(available.script, ignoreCase = true))
    }

    fun choose(
        requestedTag: String,
        candidates: Collection<Candidate>,
        offlineOnly: Boolean
    ): Candidate? = candidates
        .asSequence()
        .filter { sameAccent(requestedTag, it.localeTag) && it.installed }
        .filter { !offlineOnly || !it.networkRequired }
        .sortedWith(compareBy<Candidate> { it.networkRequired }
            .thenByDescending { it.quality }
            .thenBy { it.name })
        .firstOrNull()

    /**
     * The preferred AI language takes priority over a separately configured
     * recognition locale. An English or French utterance must not be read using
     * a voice with the wrong language merely because it was selected for STT.
     * In automatic mode, only clear script cues affect routing.
     */
    fun forReply(
        configured: SpeechLanguage,
        preferred: AstraReplyLanguage.Choice,
        text: String
    ): SpeechLanguage {
        val explicit = when (preferred) {
            AstraReplyLanguage.Choice.AUTO -> null
            AstraReplyLanguage.Choice.ENGLISH ->
                if (configured == SpeechLanguage.ENGLISH_UK) configured
                else SpeechLanguage.ENGLISH_US
            AstraReplyLanguage.Choice.RUSSIAN -> SpeechLanguage.RUSSIAN
            AstraReplyLanguage.Choice.SETSWANA -> SpeechLanguage.SETSWANA
            AstraReplyLanguage.Choice.FRENCH -> SpeechLanguage.FRENCH
            AstraReplyLanguage.Choice.SPANISH -> SpeechLanguage.SPANISH
            AstraReplyLanguage.Choice.PORTUGUESE -> SpeechLanguage.PORTUGUESE
            AstraReplyLanguage.Choice.GERMAN -> SpeechLanguage.GERMAN
            AstraReplyLanguage.Choice.SWAHILI -> SpeechLanguage.SWAHILI
            AstraReplyLanguage.Choice.JAPANESE -> SpeechLanguage.JAPANESE
            AstraReplyLanguage.Choice.MANDARIN -> SpeechLanguage.MANDARIN
            AstraReplyLanguage.Choice.ARABIC -> SpeechLanguage.ARABIC
            AstraReplyLanguage.Choice.HINDI -> SpeechLanguage.HINDI
            AstraReplyLanguage.Choice.KOREAN -> SpeechLanguage.KOREAN
            AstraReplyLanguage.Choice.ITALIAN -> SpeechLanguage.ITALIAN
            AstraReplyLanguage.Choice.TURKISH -> SpeechLanguage.TURKISH
            AstraReplyLanguage.Choice.URDU -> SpeechLanguage.URDU
        }
        if (explicit != null) return explicit

        // A bounded detector: avoid guessing among closely related languages
        // based on ambiguous Latin or Arabic-script fragments.
        val excerpt = text.take(600)
        return when {
            excerpt.any { it in '\u3040'..'\u30FF' } -> SpeechLanguage.JAPANESE
            excerpt.any { it in '\uAC00'..'\uD7AF' } -> SpeechLanguage.KOREAN
            excerpt.any { it in '\u0900'..'\u097F' } -> SpeechLanguage.HINDI
            excerpt.any { it in '\u0400'..'\u052F' } -> SpeechLanguage.RUSSIAN
            excerpt.any { it in '\u4E00'..'\u9FFF' } -> SpeechLanguage.MANDARIN
            else -> configured
        }
    }
}
