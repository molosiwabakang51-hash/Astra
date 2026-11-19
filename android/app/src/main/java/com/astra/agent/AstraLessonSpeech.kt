package com.astra.agent

import java.util.Locale

/**
 * Voice output for bilingual, authored vocabulary lessons.
 *
 * The visual cards contain both target-language vocabulary and explanatory
 * English. Reading that whole card with a single accent is misleading.
 * Extract only authored target-language phrase lines, then require an installed
 * TTS voice with the exact target language and regional accent.
 *
 * This never translates text, guesses the language of general conversation,
 * reads imported documents, or falls back to an unrelated system voice.
 */
internal object AstraLessonSpeech {
    data class Sample(
        val language: SpeechLanguage,
        val spokenText: String,
        val phraseCount: Int
    )

    private data class Course(val name: String, val accent: SpeechLanguage)

    private val supported = listOf(
        Course("Setswana (Botswana)", SpeechLanguage.SETSWANA),
        Course("Mandarin Chinese", SpeechLanguage.MANDARIN),
        Course("Russian", SpeechLanguage.RUSSIAN),
        Course("French", SpeechLanguage.FRENCH),
        Course("Spanish", SpeechLanguage.SPANISH),
        Course("Portuguese", SpeechLanguage.PORTUGUESE),
        Course("German", SpeechLanguage.GERMAN),
        Course("Swahili", SpeechLanguage.SWAHILI),
        Course("Japanese", SpeechLanguage.JAPANESE)
    )

    private val firstLessonLine = Regex(
        """^\s*lesson\s+[1-9][0-9]{0,2}(?:/[1-9][0-9]{0,2})?(?:\s+—\s+[^\n]{1,90})?$""",
        RegexOption.IGNORE_CASE
    )
    private val romanization = Regex("""\s+\([^)\n]{1,60}\)$""")
    private val control = Regex("""[\p{C}]""")
    private val numbersOnly = Regex("""^[0-9.:,\s]+$""")

    fun fromLesson(response: String, configured: SpeechLanguage): Sample? {
        if (response.length !in 15..4_000) return null
        val lines = response.lines()
        val first = lines.firstOrNull()?.trim().orEmpty()
        val course = supported.firstOrNull { item ->
            first.startsWith(item.name + " lesson ", ignoreCase = true) &&
                firstLessonLine.matches(
                    first.substring(item.name.length)
                )
        } ?: return null

        // Preserve requested Canadian French, Mexican Spanish or Brazilian
        // Portuguese rather than silently switching their dialect to Europe.
        val target = when (course.accent) {
            SpeechLanguage.FRENCH ->
                if (configured == SpeechLanguage.FRENCH_CANADA) configured
                else SpeechLanguage.FRENCH
            SpeechLanguage.SPANISH ->
                if (configured == SpeechLanguage.SPANISH_MEXICO) configured
                else SpeechLanguage.SPANISH
            SpeechLanguage.PORTUGUESE ->
                if (configured == SpeechLanguage.PORTUGUESE_BRAZIL) configured
                else SpeechLanguage.PORTUGUESE
            else -> course.accent
        }

        val phrases = lines.drop(1).mapNotNull { line ->
            val divider = line.indexOf(" — ")
            if (divider < 1 || divider > 110) return@mapNotNull null
            val left = line.substring(0, divider)
                .replace(romanization, "")
                .trim()
            // The right-hand side is the English gloss. Do not feed it to
            // the foreign-language TTS engine.
            val gloss = line.substring(divider + 3).trim()
            if (left.length !in 1..100 || gloss.isEmpty() ||
                numbersOnly.matches(left) || control.containsMatchIn(left)) return@mapNotNull null
            if (!left.any { it.isLetter() }) return@mapNotNull null
            left
        }.take(24).distinct()
        if (phrases.isEmpty()) return null

        // Never cut a word, grapheme cluster, or voice-card example midstream.
        // The former .take(700) could cut a phrase (or a UTF-16 surrogate
        // pair), while phraseCount incorrectly included phrases not spoken.
        val spokenPhrases = ArrayList<String>(phrases.size)
        var totalLength = 0
        for (phrase in phrases) {
            val complete = if (phrase.last() in ".!?。！？؟") phrase else "$phrase."
            val extra = complete.length + if (spokenPhrases.isEmpty()) 0 else 1
            if (totalLength + extra > 700) break
            spokenPhrases.add(complete)
            totalLength += extra
        }
        if (spokenPhrases.isEmpty()) return null
        return Sample(target, spokenPhrases.joinToString(" "), spokenPhrases.size)
    }
}
