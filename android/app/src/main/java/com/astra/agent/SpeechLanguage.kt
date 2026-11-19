package com.astra.agent

/**
 * User-selected language for speech recognition, independent of text-to-speech.
 * A language being listed here does not mean that its offline model is installed.
 */
internal enum class SpeechLanguage(val tag: String, val displayName: String) {
    ENGLISH_US("en-US", "English (United States)"),
    ENGLISH_UK("en-GB", "English (United Kingdom)"),
    RUSSIAN("ru-RU", "Russian (Russia)"),
    SETSWANA("tn-BW", "Setswana (Botswana)"),
    FRENCH("fr-FR", "French (France)"),
    FRENCH_CANADA("fr-CA", "French (Canada)"),
    SPANISH("es-ES", "Spanish (Spain)"),
    SPANISH_MEXICO("es-MX", "Spanish (Mexico)"),
    PORTUGUESE("pt-PT", "Portuguese (Portugal)"),
    PORTUGUESE_BRAZIL("pt-BR", "Portuguese (Brazil)"),
    GERMAN("de-DE", "German (Germany)"),
    SWAHILI("sw-KE", "Swahili (Kenya)"),
    JAPANESE("ja-JP", "Japanese (Japan)"),
    MANDARIN("zh-CN", "Mandarin Chinese (China)"),
    ARABIC("ar-SA", "Arabic (Saudi Arabia)"),
    HINDI("hi-IN", "Hindi (India)"),
    KOREAN("ko-KR", "Korean (South Korea)"),
    ITALIAN("it-IT", "Italian (Italy)"),
    TURKISH("tr-TR", "Turkish (Türkiye)"),
    URDU("ur-PK", "Urdu (Pakistan)");

    companion object {
        fun fromTag(tag: String?): SpeechLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: ENGLISH_US
    }
}
