package com.astra.agent

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Bounded offline text extraction from owner-selected documents.
 * This does not implement OCR, multimedia decoding or legacy DOC reading.
 * Contents remain untrusted retrieval data; no macros or formulae execute.
 */
internal object AstraDocumentText {
    private const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    private const val MAX_OFFICE_ENTRY = 8 * 1024 * 1024
    private const val MAX_ARCHIVE_SCAN = 20 * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 350
    private const val MAX_CHARS = 900_000
    private val supported = mapOf(
        "txt" to "txt", "md" to "markdown", "markdown" to "markdown",
        "epub" to "epub", "html" to "html", "htm" to "html",
        "csv" to "csv", "tsv" to "tsv",
        "json" to "json", "xml" to "xml", "docx" to "docx",
        "rtf" to "rtf"
    )

    fun format(filename: String): String? {
        val name = filename.substringAfterLast('/').substringAfterLast('\\')
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return supported[ext]?.takeIf { name.length > ext.length + 1 }
    }

    const val FORMATS = "TXT, MD, EPUB, HTML, CSV, TSV, JSON, XML, DOCX or RTF"

    fun extract(format: String, input: InputStream): String = when (format) {
        "html" -> html(decode(readBounded(input, MAX_TEXT_BYTES)))
        "csv" -> table(decode(readBounded(input, MAX_TEXT_BYTES)), ',')
        "tsv" -> table(decode(readBounded(input, MAX_TEXT_BYTES)), '\t')
        "json" -> json(decode(readBounded(input, MAX_TEXT_BYTES)))
        "xml" -> xml(readBounded(input, MAX_TEXT_BYTES), office = false)
        "docx" -> docx(input)
        "rtf" -> AstraRtfText.extract(input)
        else -> throw IllegalArgumentException("Unsupported document format: $format.")
    }

    private fun readBounded(stream: InputStream, maximum: Int): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(result.size() + count <= maximum) {
                "Document exceeds the safe offline extraction limit."
            }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    private fun decode(bytes: ByteArray) =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")

    private fun html(source: String): String {
        val stripped = source
            .replace(Regex("(?is)<(script|style|noscript|svg)\\b[^>]*>.*?</\\1\\s*>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?is)<(?:br|hr)\\b[^>]*>"), "\n")
            .replace(Regex("(?is)</(?:p|div|h[1-6]|li|section|article|tr)>"), "\n")
            .replace(Regex("(?s)<[^>]{0,1000}>"), " ")
        return entities(stripped).trim().take(MAX_CHARS)
    }

    private fun entities(value: String): String =
        value.replace(Regex("&(?:amp|lt|gt|quot|apos|nbsp|#\\d{1,7}|#x[0-9a-fA-F]{1,6});")) {
            when (it.value.lowercase(Locale.ROOT)) {
                "&amp;" -> "&"
                "&lt;" -> "<"
                "&gt;" -> ">"
                "&quot;" -> "\""
                "&apos;" -> "'"
                "&nbsp;" -> " "
                else -> {
                    val raw = it.value.drop(2).dropLast(1)
                    val code = runCatching {
                        if (raw.startsWith("x", true)) raw.drop(1).toInt(16)
                        else raw.toInt()
                    }.getOrNull()
                    if (code != null && code in 32..0x10ffff &&
                        code !in 0xd800..0xdfff) String(Character.toChars(code))
                    else " "
                }
            }
        }

    /** Parse escaped CSV/TSV cells as text; spreadsheet formulae never execute. */
    private fun table(source: String, separator: Char): String {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        var truncated = false
        fun endCell() {
            if (row.size < 64) row.add(cell.toString().trim().take(800))
            cell.setLength(0)
        }
        fun endRow() {
            endCell()
            if (rows.size < 1500) rows.add(row.toList()) else truncated = true
            row.clear()
        }
        while (i < source.length && !truncated) {
            val c = source[i]
            if (c == '"') {
                if (quoted && i + 1 < source.length && source[i + 1] == '"') {
                    if (cell.length < 800) cell.append('"')
                    i++
                } else quoted = !quoted
            } else if (!quoted && c == separator) {
                endCell()
            } else if (!quoted && (c == '\n' || c == '\r')) {
                endRow()
                if (c == '\r' && i + 1 < source.length && source[i + 1] == '\n') i++
            } else if (cell.length < 800) cell.append(c)
            i++
        }
        require(!quoted) { "CSV or TSV contains an unclosed quoted value." }
        if (!truncated && (cell.isNotEmpty() || row.isNotEmpty())) endRow()
        require(rows.isNotEmpty()) { "This table is empty." }
        val headings = rows.first()
        return buildString {
            appendLine("TABLE COLUMNS: " + headings.joinToString(" | "))
            for ((index, fields) in rows.drop(1).withIndex()) {
                if (length >= MAX_CHARS) { truncated = true; break }
                append("Row " + (index + 2) + ": ")
                appendLine(fields.mapIndexed { position, value ->
                    val heading = headings.getOrNull(position)?.take(80)
                        ?.ifBlank { "Column " + (position + 1) }
                        ?: "Column " + (position + 1)
                    heading + ": " + value
                }.joinToString("; "))
            }
            if (truncated) appendLine("[Table truncated for memory safety]")
        }.take(MAX_CHARS)
    }

    private fun json(source: String): String {
        // A depth guard prevents org.json stack overflow on hostile JSON.
        var depth = 0
        var quoted = false
        var escape = false
        for (c in source) {
            if (quoted) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') quoted = false
            } else if (c == '"') quoted = true
            else if (c == '{' || c == '[') {
                depth++
                require(depth <= 24) { "JSON nesting exceeds the safe limit." }
            } else if (c == '}' || c == ']') depth--
        }
        require(depth == 0 && !quoted) { "Malformed JSON document." }
        val root = JSONTokener(source).nextValue()
        require(root is JSONObject || root is JSONArray) {
            "JSON must contain an object or array."
        }
        val output = StringBuilder()
        var entries = 0
        fun walk(path: String, value: Any?, remaining: Int) {
            if (output.length >= MAX_CHARS || entries >= 6000) return
            entries++
            when (value) {
                is JSONObject -> {
                    if (remaining == 0) { output.appendLine(path + ": [nested data]"); return }
                    val keys = value.keys()
                    while (keys.hasNext() && entries < 6000 && output.length < MAX_CHARS) {
                        val key = keys.next().take(100)
                        walk(if (path.isEmpty()) key else path + "." + key,
                            value.opt(key), remaining - 1)
                    }
                }
                is JSONArray -> {
                    if (remaining == 0) { output.appendLine(path + ": [nested data]"); return }
                    for (i in 0 until minOf(value.length(), 1000)) {
                        if (entries >= 6000 || output.length >= MAX_CHARS) break
                        walk(path + "[" + i + "]", value.opt(i), remaining - 1)
                    }
                }
                else -> output.appendLine(path + ": " + (value?.toString() ?: "null").take(700))
            }
        }
        walk("", root, 12)
        require(output.isNotBlank()) { "JSON contains no indexable values." }
        if (entries >= 6000) output.appendLine("[JSON entries truncated]")
        return output.take(MAX_CHARS).toString()
    }

    private fun xml(bytes: ByteArray, office: Boolean): String {
        val text = StringBuilder()
        var inText = !office
        val parser = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }.newSAXParser()
        parser.xmlReader.apply {
            entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
            contentHandler = object : DefaultHandler() {
                override fun startElement(
                    uri: String?, localName: String?, qName: String?, attributes: Attributes?
                ) {
                    val name = (localName?.takeIf { it.isNotBlank() }
                        ?: qName.orEmpty().substringAfter(':')).lowercase(Locale.ROOT)
                    if (office) {
                        if (name == "p") text.append('\n')
                        if (name == "tab") text.append('\t')
                        if (name == "t") inText = true
                    } else text.append('\n').append(name).append(": ")
                }
                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    if (office) {
                        val name = localName?.takeIf { it.isNotBlank() }
                            ?: qName.orEmpty().substringAfter(':')
                        if (name == "t") inText = false
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inText && text.length < MAX_CHARS)
                        text.append(ch, start, minOf(length, MAX_CHARS - text.length))
                }
            }
            parse(InputSource(ByteArrayInputStream(bytes)))
        }
        return text.toString().trim()
    }

    private fun docx(input: InputStream): String {
        var entries = 0
        var scanned = 0
        var result: String? = null
        ZipInputStream(input.buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                require(++entries <= MAX_ARCHIVE_ENTRIES) {
                    "DOCX contains too many archive entries."
                }
                val path = entry.name.replace('\\', '/')
                require(!path.startsWith('/') && path.split('/').none { it == ".." }) {
                    "Unsafe DOCX archive entry."
                }
                if (entry.isDirectory) continue
                val remaining = MAX_ARCHIVE_SCAN - scanned
                require(remaining > 0) { "DOCX exceeds the extraction budget." }
                val data = readBounded(archive, minOf(remaining, MAX_OFFICE_ENTRY))
                scanned += data.size
                if (path == "word/document.xml") {
                    result = xml(data, office = true)
                    break
                }
            }
        }
        return result?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(
                "DOCX has no readable text. Encrypted, scanned and old DOC documents are unsupported."
            )
    }
}
