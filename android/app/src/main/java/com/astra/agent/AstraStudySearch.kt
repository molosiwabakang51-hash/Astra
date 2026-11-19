package com.astra.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Offline, language-aware matching for the Study catalog. Users can type
 * "francais" to find "français" without changing an authored lesson title.
 *
 * Remove diacritical marks ONLY from Latin-script words. Stripping marks
 * indiscriminately destroys vowels in Devanagari, Arabic and other scripts.
 * Non-Latin words remain searchable using their actual writing system.
 */
internal object AstraStudySearch {
    fun key(raw: String): String {
        val decomposed = Normalizer.normalize(
            raw.lowercase(Locale.ROOT), Normalizer.Form.NFD
        )
        val result = StringBuilder(decomposed.length)
        var precedingLatin = false
        for (char in decomposed) {
            val type = Character.getType(char)
            val isMark = type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            if (isMark) {
                if (!precedingLatin) result.append(char)
                continue
            }
            precedingLatin = Character.UnicodeScript.of(char.code) ==
                Character.UnicodeScript.LATIN
            result.append(char)
        }
        return result.toString()
            .replace("ß", "ss")
            .replace("æ", "ae")
            .replace("œ", "oe")
            .replace("ø", "o")
            .replace("ł", "l")
    }

    fun query(raw: String): String = key(raw.trim().take(80))

    fun matches(text: String, normalizedQuery: String): Boolean =
        key(text).contains(normalizedQuery)
}
