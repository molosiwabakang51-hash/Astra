package com.astra.agent

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Portable, user-initiated offline backup for all nine private vocabulary decks.
 *
 * This is deliberately separate from the legacy 8 MB preferences JSON backup:
 * a user may import 45,000 extra flashcards, and existing JSON backups must
 * continue to work unchanged. The bundle is a plain UTF-8, three-column TSV.
 *
 * Restore is additive: it never deletes existing cards or changes quiz progress.
 * All rows and per-language storage limits are checked before any write.
 */
internal object AstraVocabularyBundle {
    private const val HEADER = "language\tphrase\ttranslation"
    private const val FOOTER = "#astra-private-vocabulary-v1-end"
    private const val MAX_BUNDLE_BYTES = 32 * 1024 * 1024
    private const val MAX_LANGUAGE_BYTES = 3 * 1024 * 1024
    private const val MAX_CARD_CHARS = 160
    private val utf8 = StandardCharsets.UTF_8

    data class RestoreResult(
        val added: Int,
        val skipped: Int,
        val languages: Int
    )

    /** Do not close the caller-owned output stream. */
    fun export(store: AstraVocabularyStore, output: OutputStream): Int {
        val writer = OutputStreamWriter(output, utf8).buffered()
        val digest = MessageDigest.getInstance("SHA-256")
        val headerBytes = (HEADER + "\n").toByteArray(utf8)
        digest.update(headerBytes)
        writer.write(HEADER + "\n")
        var totalBytes = headerBytes.size
        var cards = 0
        for (language in AstraVocabularyStore.supportedLanguages.sorted()) {
            for (word in store.list(language)) {
                val line = "$language\t${word.phrase}\t${word.translation}\n"
                val bytes = line.toByteArray(utf8)
                totalBytes += bytes.size
                require(totalBytes <= MAX_BUNDLE_BYTES) {
                    "Vocabulary archive exceeds the 32 MB safety limit."
                }
                digest.update(bytes)
                writer.write(line)
                cards++
            }
        }
        // A complete footer prevents truncated, still-valid TSV prefixes from
        // masquerading as a successful full backup.
        val marker = FOOTER + "\t" + cards + "\t" + digest.digest().toHex() + "\n"
        require(totalBytes + marker.toByteArray(utf8).size <= MAX_BUNDLE_BYTES) {
            "Vocabulary archive exceeds the 32 MB safety limit."
        }
        writer.write(marker)
        writer.flush()
        return cards
    }

    /**
     * Preflight the complete archive before calling the existing atomic
     * per-language TSV importer. If storage fails after preflight, earlier
     * additions remain safe and the owner may retry the same bundle.
     */
    fun restore(store: AstraVocabularyStore, input: InputStream): RestoreResult {
        val buffer = ByteArray(16 * 1024)
        val accumulated = ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(accumulated.size().toLong() + count <= MAX_BUNDLE_BYTES.toLong()) {
                "Vocabulary archive exceeds the 32 MB safety limit."
            }
            accumulated.write(buffer, 0, count)
        }
        val text = utf8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(accumulated.toByteArray()))
            .toString()
            .removePrefix("\uFEFF")

        val byLanguage = linkedMapOf<String, MutableList<AstraVocabularyStore.Word>>()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update((HEADER + "\n").toByteArray(utf8))
        var footerSeen = false
        var rows = 0
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.removeSuffix("\r")
            if (index == 0) {
                require(line == HEADER) {
                    "Not an Astra vocabulary bundle. Expected language, phrase and translation columns."
                }
                return@forEachIndexed
            }
            if (line.isEmpty()) return@forEachIndexed
            require(!footerSeen) { "Unexpected text after the vocabulary backup checksum." }
            if (line.startsWith(FOOTER + "\t")) {
                val marker = line.split('\t')
                require(marker.size == 3 && marker[1].toIntOrNull() == rows &&
                    marker[2] == digest.digest().toHex()
                ) { "Vocabulary backup is incomplete or its checksum does not match." }
                footerSeen = true
                return@forEachIndexed
            }
            val parts = line.split('\t')
            require(parts.size == 3) {
                "Invalid vocabulary bundle at line ${index + 1}: expected three tab-separated fields."
            }
            val language = parts[0]
            require(language in AstraVocabularyStore.supportedLanguages) {
                "Unsupported vocabulary language at line ${index + 1}."
            }
            val phrase = parts[1].trim()
            val translation = parts[2].trim()
            require(phrase.length in 1..MAX_CARD_CHARS &&
                translation.length in 1..MAX_CARD_CHARS &&
                !(phrase + translation).any { it.isISOControl() }
            ) { "Invalid vocabulary entry at line ${index + 1}." }
            rows++
            require(rows <= 9 * AstraVocabularyStore.MAX_CUSTOM_WORDS_PER_LANGUAGE) {
                "Vocabulary archive contains too many cards."
            }
            val deck = byLanguage.getOrPut(language) { mutableListOf() }
            deck.add(AstraVocabularyStore.Word(phrase, translation))
            require(deck.size <= AstraVocabularyStore.MAX_CUSTOM_WORDS_PER_LANGUAGE) {
                "Vocabulary archive exceeds 5,000 cards for $language."
            }
            digest.update((line + "\n").toByteArray(utf8))
        }
        require(footerSeen) { "Incomplete Astra vocabulary backup: checksum footer missing." }

        // Validate every merge and its eventual disk representation before
        // modifying any private deck. Exact case matters for German nouns.
        val imports = linkedMapOf<String, ByteArray>()
        for ((language, incoming) in byLanguage) {
            val existing = store.list(language)
            val known = existing.map { it.phrase }.toMutableSet()
            val toAdd = incoming.filter { known.add(it.phrase) }
            require(known.size <= AstraVocabularyStore.MAX_CUSTOM_WORDS_PER_LANGUAGE) {
                "Restoring $language would exceed 5,000 private cards; no changes made."
            }
            val projected = existing + toAdd
            val projectedBytes = projected.sumOf {
                (it.phrase + "\t" + it.translation + "\n").toByteArray(utf8).size.toLong()
            }
            require(projectedBytes <= MAX_LANGUAGE_BYTES) {
                "Restoring $language would exceed private storage limits; no changes made."
            }
            val payload = incoming.joinToString(separator = "\n", postfix = "\n") {
                it.phrase + "\t" + it.translation
            }.toByteArray(utf8)
            require(payload.size <= MAX_LANGUAGE_BYTES) {
                "$language has too much vocabulary data; no changes made."
            }
            imports[language] = payload
        }

        var added = 0
        var skipped = 0
        for ((language, payload) in imports) {
            val result = store.importTsv(language, ByteArrayInputStream(payload))
            added += result.added
            skipped += result.alreadyPresent
        }
        return RestoreResult(added, skipped, imports.size)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
