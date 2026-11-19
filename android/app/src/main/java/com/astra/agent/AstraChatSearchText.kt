package com.astra.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Unicode-safe, accent-tolerant matching for saved conversation search.
 *
 * Keep snippets in the ORIGINAL text, never in the folded search index.
 * This makes "resume"/"résumé" and composed/decomposed accents searchable
 * without corrupting scripts, voice accents or private stored messages.
 * No transliteration, external dictionary or network dependency.
 */
internal object AstraChatSearchText {
    data class Match(val start: Int, val endExclusive: Int)

    fun find(text: String, query: String): Match? {
        if (query.isEmpty()) return null
        val direct = text.indexOf(query, ignoreCase = true)
        if (direct >= 0) return Match(direct, direct + query.length)
        // Avoid normalizing thousands of ordinary ASCII-only messages on
        // every keystroke when their ordinary substring search already failed.
        if (text.all { it.code < 128 } && query.all { it.code < 128 }) return null

        val wanted = fold(query)
        if (wanted.isEmpty()) return null
        val at = fold(text).indexOf(wanted)
        if (at < 0) return null
        val targetEnd = at + wanted.length

        // Find original UTF-16 offsets. A folded index cannot be passed
        // directly into the source string because accents and case folds can
        // change length. Walk code points, never splitting surrogate pairs.
        var sourceAt = 0
        var foldedAt = 0
        var start = -1
        while (sourceAt < text.length) {
            val cp = Character.codePointAt(text, sourceAt)
            val sourceNext = sourceAt + Character.charCount(cp)
            val pieceEnd = foldedAt + foldPiece(cp).length
            if (start < 0 && pieceEnd > at) start = sourceAt
            if (start >= 0 && pieceEnd >= targetEnd) {
                return Match(start, sourceNext)
            }
            foldedAt = pieceEnd
            sourceAt = sourceNext
        }
        return null
    }

    fun contains(text: String, query: String): Boolean = find(text, query) != null

    private fun fold(value: String): String {
        val result = StringBuilder(value.length)
        var offset = 0
        while (offset < value.length) {
            val cp = Character.codePointAt(value, offset)
            result.append(foldPiece(cp))
            offset += Character.charCount(cp)
        }
        return result.toString()
    }

    private fun foldPiece(cp: Int): String {
        if (cp in 65..90) return (cp + 32).toChar().toString()
        if (cp < 128) return cp.toChar().toString()
        val normalized = Normalizer.normalize(
            String(Character.toChars(cp)), Normalizer.Form.NFD
        ).lowercase(Locale.ROOT)
        // Fold the combining-diacritic block used by most Latin accents
        // and by Russian Ё, but preserve Indic vowel signs, Arabic harakat,
        // Hebrew niqqud and other script-specific combining marks.
        // Removing every Unicode mark would merge *different words* in those
        // languages, yielding misleading search results.
        return normalized.filterNot { it.code in 0x0300..0x036F }
    }
}
