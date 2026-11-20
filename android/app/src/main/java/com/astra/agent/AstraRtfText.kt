package com.astra.agent

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Bounded, text-only RTF 1.x extraction for owner-selected documents.
 * Never interprets or executes embedded binary objects, fields or macros.
 * This is not a full RTF layout renderer; scans, equations and images
 * require separate owner-approved offline extraction components.
 */
internal object AstraRtfText {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_CHARS = 900_000
    private const val MAX_DEPTH = 32
    private val windows1252 = Charset.forName("windows-1252")
    private val hiddenDestinations = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object",
        "header", "footer", "footerf", "headerf", "annotation", "xmlnstbl",
        "listtable", "listoverridetable", "datastore", "themedata",
        "shp", "shpinst", "filetbl"
    )
    private data class Frame(val hidden: Boolean, val unicodeFallbackCount: Int)

    fun extract(input: InputStream): String {
        val protectedInput = AstraProgressInputStream(input)
        val data = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = protectedInput.read(buffer)
            if (n < 0) break
            require(data.size() + n <= MAX_BYTES) {
                "RTF exceeds the safe offline extraction limit."
            }
            data.write(buffer, 0, n)
        }
        // Latin-1 maps each original byte onto one code unit. This allows
        // exact byte-count skipping for RTF's \binN data sections.
        val source = String(data.toByteArray(), StandardCharsets.ISO_8859_1)
        val header = source.trimStart()
        require(header.startsWith("{\\rtf") && header.getOrNull(5)?.isDigit() == true) {
            "This file is not a supported RTF text document."
        }
        val output = StringBuilder()
        val stack = ArrayDeque<Frame>()
        var hidden = false
        var uc = 1
        var fallback = 0
        var depth = 0
        var position = 0

        fun emit(text: String) {
            if (hidden || text.isEmpty()) return
            require(text.length <= MAX_CHARS - output.length) {
                "RTF readable text exceeds the safe indexing limit; no partial import was saved."
            }
            output.append(text)
        }
        fun emitChar(char: Char) {
            if (fallback > 0) {
                fallback--
            } else {
                emit(char.toString())
            }
        }

        while (position < source.length) {
            when (val char = source[position]) {
                '{' -> {
                    require(++depth <= MAX_DEPTH) { "RTF group nesting exceeds the safe limit." }
                    stack.addLast(Frame(hidden, uc))
                    position++
                }
                '}' -> {
                    require(stack.isNotEmpty()) { "RTF has unmatched closing brace." }
                    val parent = stack.removeLast()
                    hidden = parent.hidden
                    uc = parent.unicodeFallbackCount
                    depth--
                    fallback = 0
                    position++
                }
                '\\' -> {
                    position++
                    require(position < source.length) { "RTF ends inside an escape." }
                    val command = source[position]
                    when {
                        command in "\\{}" -> {
                            emitChar(command)
                            position++
                        }
                        command == '\'' -> {
                            require(position + 2 < source.length) {
                                "RTF contains a truncated hex escape."
                            }
                            val hex = source.substring(position + 1, position + 3)
                            val value = hex.toIntOrNull(16)
                                ?: throw IllegalArgumentException("RTF contains a malformed hex escape.")
                            val decoded = String(byteArrayOf(value.toByte()), windows1252)
                            if (fallback > 0) fallback-- else emit(decoded)
                            position += 3
                        }
                        command.isLetter() && command.code < 128 -> {
                            val begin = position
                            while (position < source.length &&
                                (source[position] in 'A'..'Z' ||
                                    source[position] in 'a'..'z')
                            ) position++
                            val word = source.substring(begin, position).lowercase()
                            val signedStart = position
                            if (position < source.length && source[position] == '-') position++
                            while (position < source.length && source[position].isDigit()) position++
                            val arg = source.substring(signedStart, position)
                                .takeIf { it.isNotEmpty() && it != "-" }?.toIntOrNull()
                            if (position < source.length && source[position] == ' ') position++
                            when (word) {
                                in hiddenDestinations -> hidden = true
                                "uc" -> if (arg != null) {
                                    require(arg in 0..16) { "RTF Unicode fallback count is too large." }
                                    uc = arg
                                }
                                "u" -> {
                                    require(arg != null && arg in -32768..65535) {
                                        "RTF contains an invalid Unicode escape."
                                    }
                                    emit(arg.toChar().toString())
                                    fallback = uc
                                }
                                "bin" -> {
                                    require(arg != null && arg >= 0 &&
                                        arg <= source.length - position &&
                                        arg <= MAX_BYTES) {
                                        "RTF contains invalid embedded binary length."
                                    }
                                    position += arg
                                }
                                "par", "line" -> emit("\n")
                                "tab" -> emit("\t")
                                "emdash" -> emit("—")
                                "endash" -> emit("–")
                                "bullet" -> emit("•")
                                "lquote", "rquote" -> emit("'")
                                "ldblquote", "rdblquote" -> emit("\"")
                                else -> Unit // Ignore RTF formatting control words.
                            }
                        }
                        else -> {
                            when (command) {
                                '*' -> hidden = true
                                '~' -> emitChar(' ')
                                '_' -> emitChar('-')
                                '-' -> Unit
                                else -> Unit
                            }
                            position++
                        }
                    }
                }
                '\r', '\n' -> position++ // RTF source whitespace is not document text.
                else -> {
                    if (char.code >= 128) {
                        val translated = String(byteArrayOf(char.code.toByte()), windows1252)
                        if (fallback > 0) fallback-- else emit(translated)
                    } else emitChar(char)
                    position++
                }
            }
        }
        require(depth == 0) { "RTF contains unclosed groups." }
        return output.toString().trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("RTF contains no readable text.")
    }
}
