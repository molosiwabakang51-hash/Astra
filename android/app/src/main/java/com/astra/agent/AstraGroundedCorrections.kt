package com.astra.agent

import java.util.Locale

/**
 * Keep a later owner correction attached to a matching earlier owner message
 * during EXPLICIT private-memory retrieval.
 *
 * Never invent a link across chats or past an unrelated owner message. Astra
 * replies are not testimony and never contribute correction text. Returned
 * passages are untrusted quotations, not executable instructions.
 */
internal object AstraGroundedCorrections {
    private fun isCorrection(raw: String): Boolean {
        val value = raw.trim().lowercase(Locale.ROOT)
        if (value.length !in 4..160) return false
        // Match compact numeric corrections without treating ordinary "no" as a new fact.
        if (value.startsWith("no") && value.getOrNull(2)?.let { !it.isLetterOrDigit() } == true) {
            val rest = value.drop(2).trimStart(' ', ',', ';', ':')
            if (rest.firstOrNull()?.isDigit() == true) return true
        }
        return listOf(
            "actually", "correction:", "correction ", "i meant",
            "instead", "rather", "sorry,", "no, make", "no make",
            "make it", "make that", "change it", "change that",
            "use that", "use it"
        ).any { prefix ->
            value.startsWith(prefix) &&
                (value.length == prefix.length ||
                    !value[prefix.length].isLetterOrDigit())
        }
    }

    private fun text(raw: String, limit: Int): String {
        val normalized = raw.trim().replace(Regex("""[\r\n\t\u0000-\u001f]+"""), " ")
            .replace(Regex("""\s+"""), " ").replace('"', '\'')
        var result = normalized.take(limit)
        if (result.isNotEmpty() && Character.isHighSurrogate(result.last())) {
            result = result.dropLast(1)
        }
        return result
    }

    /**
     * Null means no supported nearby correction was found.
     * The caller must first establish that the original message matches
     * the owner's retrieval topic; this helper does not grant memory access.
     */
    fun annotated(
        messages: List<AstraChatArchive.Message>,
        originalIndex: Int,
        focusedOriginal: String
    ): String? {
        if (messages.getOrNull(originalIndex)?.role != "You") return null
        val corrections = mutableListOf<String>()
        for (index in (originalIndex + 1) until
            minOf(messages.size, originalIndex + 7)
        ) {
            val entry = messages[index]
            if (entry.role != "You") continue
            if (!isCorrection(entry.text)) break
            corrections.add(entry.text)
            if (corrections.size == 2) break
        }
        if (corrections.isEmpty()) return null
        return if (corrections.size == 1) {
            "Earlier owner: " + text(focusedOriginal, 88) +
                " | Later owner correction: " + text(corrections.single(), 76)
        } else {
            "Earlier owner: " + text(focusedOriginal, 56) +
                " | Owner correction 1: " + text(corrections[0], 46) +
                " | Owner correction 2: " + text(corrections[1], 46)
        }
    }
}
