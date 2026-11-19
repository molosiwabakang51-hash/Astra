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
     * In automatic mode, strong script or long-language evidence may
     * override the selected dictation voice. Ambiguity keeps the configured
     * locale and never authorizes a wrong-region voice fallback.
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
            AstraReplyLanguage.Choice.FRENCH ->
                if (configured == SpeechLanguage.FRENCH_CANADA) configured
                else SpeechLanguage.FRENCH
            AstraReplyLanguage.Choice.SPANISH ->
                if (configured == SpeechLanguage.SPANISH_MEXICO) configured
                else SpeechLanguage.SPANISH
            AstraReplyLanguage.Choice.PORTUGUESE ->
                if (configured == SpeechLanguage.PORTUGUESE_BRAZIL) configured
                else SpeechLanguage.PORTUGUESE
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

        // In AUTO mode only strong script evidence can override the configured
        // voice. A foreign word inside an English reply is not sufficient.
        val excerpt = text.take(600)
        val letterCount = excerpt.count { it.isLetter() }
        if (letterCount < 12) return configured
        fun dominant(first: Char, last: Char): Boolean {
            val count = excerpt.count { it in first..last }
            return count >= 8 && count * 2 > letterCount
        }
        val kana = excerpt.count { it in '\u3040'..'\u30FF' }
        val han = excerpt.count { it in '\u4E00'..'\u9FFF' }
        return when {
            // Japanese combines kana and Han characters; a Chinese-only
            // detector incorrectly classifies ordinary mixed Japanese text.
            dominant('\u3040', '\u30FF') ||
                (kana >= 3 && (kana + han) * 2 > letterCount) ->
                SpeechLanguage.JAPANESE
            dominant('\uAC00', '\uD7AF') -> SpeechLanguage.KOREAN
            dominant('\u0900', '\u097F') -> SpeechLanguage.HINDI
            dominant('\u0400', '\u052F') -> SpeechLanguage.RUSSIAN
            dominant('\u0600', '\u06FF') ->
                if (excerpt.count { it in "پچگڑےں" } >= 2)
                    SpeechLanguage.URDU else SpeechLanguage.ARABIC
            dominant('\u4E00', '\u9FFF') -> SpeechLanguage.MANDARIN
            else -> AstraSpokenLanguageHeuristics.detect(excerpt, configured) ?: configured
        }
    }
}
