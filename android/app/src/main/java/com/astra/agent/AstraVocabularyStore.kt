package com.astra.agent

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * On-device vocabulary that the owner imports explicitly from local UTF-8 TSV.
 *
 * Files are stored under Android's private no-backup directory, never sent to
 * a server or mixed into neural training, and do not change the APK/model size.
 * Existing built-in cards and quiz indices always come first.
 */
internal class AstraVocabularyStore(private val directory: File) {
    data class Word(val phrase: String, val translation: String)
    data class ImportResult(
        val language: String, val added: Int,
        val alreadyPresent: Int, val totalCustomWords: Int
    )

    companion object {
        const val MAX_CUSTOM_WORDS_PER_LANGUAGE = 5_000
        private const val MAX_INPUT_BYTES = 3 * 1024 * 1024
        private const val MAX_STORED_BYTES = 3 * 1024 * 1024
        private const val MAX_LINES = 6_000
        private const val MAX_TEXT_CHARS = 160
        val supportedLanguages = setOf(
            "russian", "setswana", "french", "spanish", "portuguese",
            "german", "swahili", "japanese", "mandarin"
        )
    }

    private val cache = mutableMapOf<String, List<Word>>()

    @Synchronized
    fun list(languageId: String): List<Word> {
        val id = checkedLanguage(languageId)
        cache[id]?.let { return it }
        val file = File(directory, "$id.tsv")
        if (!file.exists()) return emptyList<Word>().also { cache[id] = it }
        require(file.isFile && file.length() in 1..MAX_STORED_BYTES.toLong()) {
            "Private vocabulary file is invalid or exceeds 3 MB."
        }
        val words = file.inputStream().use { parse(it, allowHeader = false) }
        require(words.size <= MAX_CUSTOM_WORDS_PER_LANGUAGE) {
            "Private vocabulary exceeds the 5,000-card local limit."
        }
        require(words.map { it.phrase }.toSet().size == words.size) {
            "Private vocabulary contains duplicated terms."
        }
        return words.also { cache[id] = it }
    }

    /**
     * Imports atomically: malformed rows never change an existing collection.
     * Duplicate source-language entries are skipped, retaining the original.
     * This intentionally does not translate, infer, or verify user-supplied text.
     */
    @Synchronized
    fun importTsv(languageId: String, source: InputStream): ImportResult {
        val id = checkedLanguage(languageId)
        val current = list(id)
        val parsed = parse(source, allowHeader = true)
        require(parsed.isNotEmpty()) {
            "Select a UTF-8 TSV file containing 'phrase<TAB>English translation' rows."
        }

        val known = current.map { it.phrase }.toMutableSet()
        var duplicates = 0
        val added = mutableListOf<Word>()
        parsed.forEach { word ->
            if (known.add(word.phrase)) added.add(word) else duplicates++
        }
        val combined = current + added
        require(combined.size <= MAX_CUSTOM_WORDS_PER_LANGUAGE) {
            "Maximum 5,000 imported cards per language. " +
                "This file would exceed that limit; no changes made."
        }
        if (added.isNotEmpty()) {
            require(directory.isDirectory || directory.mkdirs()) {
                "Cannot create private vocabulary storage."
            }
            val target = File(directory, "$id.tsv")
            val temp = File(directory, "$id.partial")
            try {
                FileOutputStream(temp).use { raw ->
                    raw.bufferedWriter(Charsets.UTF_8).use { out ->
                        combined.forEach {
                            out.append(it.phrase).append('\t')
                                .append(it.translation).append('\n')
                        }
                        out.flush()
                    }
                }
                require(temp.length() in 1..MAX_STORED_BYTES.toLong()) {
                    "Vocabulary exceeds local storage safety limit."
                }
                try {
                    Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING)
                }
                cache[id] = combined
            } finally {
                temp.delete()
            }
        }
        return ImportResult(id, added.size, duplicates, combined.size)
    }

    @Synchronized
    fun clear(languageId: String): Boolean {
        val id = checkedLanguage(languageId)
        val target = File(directory, "$id.tsv")
        if (target.isFile && !target.delete()) {
            error("Could not delete privately imported vocabulary.")
        }
        cache.remove(id)
        return !target.exists()
    }

    private fun checkedLanguage(raw: String): String {
        require(raw in supportedLanguages) { "Unsupported vocabulary language." }
        return raw
    }

    private fun parse(source: InputStream, allowHeader: Boolean): List<Word> {
        val data = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            require(data.size() + count <= MAX_INPUT_BYTES) {
                "Vocabulary file exceeds the 3 MB import limit."
            }
            data.write(buffer, 0, count)
        }
        val decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data.toByteArray()))
            .toString().removePrefix("\uFEFF")

        val words = mutableListOf<Word>()
        var rowNumber = 0
        decoded.lineSequence().forEach { line ->
            rowNumber++
            require(rowNumber <= MAX_LINES) {
                "Vocabulary contains too many lines (maximum 6,000)."
            }
            if (line.isBlank()) return@forEach
            val parts = line.removeSuffix("\r").split('\t')
            require(parts.size == 2) {
                "Line $rowNumber needs two fields separated by a tab."
            }
            val phrase = parts[0].trim()
            val translation = parts[1].trim()
            if (allowHeader && rowNumber == 1 &&
                phrase.equals("phrase", true) &&
                translation.equals("translation", true)
            ) return@forEach

            require(phrase.length in 1..MAX_TEXT_CHARS &&
                translation.length in 1..MAX_TEXT_CHARS &&
                !(phrase + translation).any { it.isISOControl() }
            ) {
                "Line $rowNumber has an empty, overlong or invalid phrase."
            }
            words.add(Word(phrase, translation))
        }
        return words
    }
}
