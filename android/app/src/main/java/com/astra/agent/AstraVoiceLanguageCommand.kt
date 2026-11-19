package com.astra.agent

import java.util.Locale

/**
 * Explicit voice-language selection. Speech recognition, speech synthesis and
 * local AI reply style use separate engines but a single selected locale.
 *
 * Listing a language is not a guarantee that an Android device has its
 * offline recognizer or non-network TTS voice installed.
 */
internal object AstraVoiceLanguageCommand {
    data class Selection(
        val speech: SpeechLanguage,
        val reply: AstraReplyLanguage.Choice
    )

    private val englishCommand = Regex(
        """^(?:(?:please\s+)?(?:speak|talk)(?:\s+to\s+me)?(?:\s+in)?|""" +
            """(?:please\s+)?listen(?:\s+to\s+me)?\s+in|""" +
            """set\s+(?:my\s+)?(?:voice|speech)\s+language\s+to|""" +
            """switch\s+(?:the\s+)?voice\s+to)\s+([\p{L} -]{2,45})$""",
        RegexOption.IGNORE_CASE
    )
    private val alternateCommand = Regex(
        """^(?:parle\s+français|parlez\s+français|háblame\s+en\s+español|""" +
            """hablame\s+en\s+espanol|sprich\s+deutsch|говори\s+по-русски)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): Selection? {
        val normalized = raw.trim().replace(Regex("""\s+"""), " ")
            .trimEnd('.', '!', '?')
        if (normalized.length !in 5..90) return null
        val lower = normalized.lowercase(Locale.ROOT)
        val requested = when {
            alternateCommand.matches(lower) -> when {
                lower.contains("français") -> "french"
                lower.contains("español") || lower.contains("espanol") -> "spanish"
                lower.contains("deutsch") -> "german"
                else -> "russian"
            }
            (lower.startsWith("use ") || lower.startsWith("please use ")) &&
                lower.endsWith(" for voice") ->
                lower.removePrefix("please ").removePrefix("use ")
                    .removeSuffix(" for voice").trim()
            else -> englishCommand.matchEntire(normalized)?.groupValues?.get(1)
                ?.lowercase(Locale.ROOT)?.trim() ?: return null
        }

        val name = requested.trim()
        val language = when (name) {
            "english", "american english", "english us", "english usa" ->
                SpeechLanguage.ENGLISH_US
            "british english", "english uk" -> SpeechLanguage.ENGLISH_UK
            "russian", "русский" -> SpeechLanguage.RUSSIAN
            "setswana", "tswana" -> SpeechLanguage.SETSWANA
            "french", "français", "francais" -> SpeechLanguage.FRENCH
            "spanish", "español", "espanol" -> SpeechLanguage.SPANISH
            "portuguese", "português", "portugues" -> SpeechLanguage.PORTUGUESE
            "german", "deutsch" -> SpeechLanguage.GERMAN
            "swahili", "kiswahili" -> SpeechLanguage.SWAHILI
            "japanese", "日本語" -> SpeechLanguage.JAPANESE
            "mandarin", "chinese", "mandarin chinese", "中文" ->
                SpeechLanguage.MANDARIN
            else -> return null
        }
        val reply = when (language) {
            SpeechLanguage.ENGLISH_US, SpeechLanguage.ENGLISH_UK ->
                AstraReplyLanguage.Choice.ENGLISH
            SpeechLanguage.RUSSIAN -> AstraReplyLanguage.Choice.RUSSIAN
            SpeechLanguage.SETSWANA -> AstraReplyLanguage.Choice.SETSWANA
            SpeechLanguage.FRENCH -> AstraReplyLanguage.Choice.FRENCH
            SpeechLanguage.SPANISH -> AstraReplyLanguage.Choice.SPANISH
            SpeechLanguage.PORTUGUESE -> AstraReplyLanguage.Choice.PORTUGUESE
            SpeechLanguage.GERMAN -> AstraReplyLanguage.Choice.GERMAN
            SpeechLanguage.SWAHILI -> AstraReplyLanguage.Choice.SWAHILI
            SpeechLanguage.JAPANESE -> AstraReplyLanguage.Choice.JAPANESE
            SpeechLanguage.MANDARIN -> AstraReplyLanguage.Choice.MANDARIN
        }
        return Selection(language, reply)
    }

    fun confirmation(selection: Selection, offlineVoiceAvailable: Boolean): String =
        "Astra language selected: " + selection.reply.display +
            ". Local AI replies will prefer this language. Dictation and " +
            "speech playback will use " + selection.speech.displayName + ". " +
            if (offlineVoiceAvailable) {
                "A compatible non-network speech voice was detected. " +
                    "Offline recognition depends on installed Android language data."
            } else {
                "An offline voice for this language is not confirmed on this phone. " +
                    "Install its Android voice and recognition language packs " +
                    "before expecting offline speech. Text learning still works."
            }

    /** Short real-language examples, not a promise of fluent voice output. */
    fun testPhrase(language: SpeechLanguage): String = when (language) {
        SpeechLanguage.ENGLISH_US, SpeechLanguage.ENGLISH_UK ->
            "Hello. I am Astra. This is a voice test."
        SpeechLanguage.RUSSIAN -> "Здравствуйте. Я Астра."
        SpeechLanguage.SETSWANA -> "Dumela. Ke nna Astra."
        SpeechLanguage.FRENCH -> "Bonjour. Je suis Astra."
        SpeechLanguage.SPANISH -> "Hola. Soy Astra."
        SpeechLanguage.PORTUGUESE -> "Olá. Sou a Astra."
        SpeechLanguage.GERMAN -> "Hallo. Ich bin Astra."
        SpeechLanguage.SWAHILI -> "Habari. Mimi ni Astra."
        SpeechLanguage.JAPANESE -> "こんにちは。アストラです。"
        SpeechLanguage.MANDARIN -> "你好。我是阿斯特拉。"
    }
}
