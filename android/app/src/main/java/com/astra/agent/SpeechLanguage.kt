package com.astra.agent

/**
 * User-selected language for speech recognition, independent of text-to-speech.
 * A language being listed here does not mean that its offline model is installed.
 */
internal enum class SpeechLanguage(val tag: String, val displayName: String) {
    ENGLISH_US("en-US", "English (United States)"),
    ENGLISH_UK("en-GB", "English (United Kingdom)"),
    RUSSIAN("ru-RU", "Russian (Russia)");

    companion object {
        fun fromTag(tag: String?): SpeechLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: ENGLISH_US
    }
}
