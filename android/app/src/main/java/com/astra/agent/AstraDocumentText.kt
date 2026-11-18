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
        "rtf" to "rtf", "xlsx" to "xlsx",
        "pptx" to "pptx", "odt" to "odt"
    )

    fun format(filename: String): String? {
        val name = filename.substringAfterLast('/').substringAfterLast('\\')
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return supported[ext]?.takeIf { name.length > ext.length + 1 }
    }

    const val FORMATS = "TXT, MD, EPUB, HTML, CSV, TSV, JSON, XML, DOCX, RTF, XLSX, PPTX or ODT"

    fun extract(format: String, input: InputStream): String = when (format) {
        "html" -> html(decode(readBounded(input, MAX_TEXT_BYTES)))
        "csv" -> table(decode(readBounded(input, MAX_TEXT_BYTES)), ',')
        "tsv" -> table(decode(readBounded(input, MAX_TEXT_BYTES)), '\t')
        "json" -> json(decode(readBounded(input, MAX_TEXT_BYTES)))
        "xml" -> xml(readBounded(input, MAX_TEXT_BYTES), office = false)
        "docx" -> docx(input)
        "rtf" -> AstraRtfText.extract(input)
        "xlsx" -> AstraSpreadsheetText.extract(input)
        "pptx" -> officeArchive(input, "pptx")
        "odt" -> officeArchive(input, "odt")
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
                        if (name == "br") text.append('\n')
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

    /**
     * PPTX slides and ODT body text are plain owner-provided XML in ZIP
     * containers. Scan/decompress within a shared budget, including ignored
     * image/macro entries, to prevent a tiny archive from expanding without
     * limit. Never execute objects, macros, scripts, or embedded documents.
     */
    private fun officeArchive(input: InputStream, kind: String): String {
        require(kind == "pptx" || kind == "odt") { "Unsupported office archive." }
        val slides = sortedMapOf<Int, String>()
        var body: String? = null
        var mime: String? = null
        var types: String? = null
        var foundPresentation = false
        var scanned = 0
        var entries = 0
        val slidePath = Regex("""ppt/slides/slide([1-9][0-9]{0,3})\.xml""")
        ZipInputStream(input.buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                require(++entries <= MAX_ARCHIVE_ENTRIES) {
                    "Office archive has too many entries to inspect safely."
                }
                val path = entry.name.replace('\\', '/')
                require(!path.startsWith('/') && path.split('/').none { it == ".." }) {
                    "Unsafe office archive entry."
                }
                if (entry.isDirectory) continue
                val remaining = MAX_ARCHIVE_SCAN - scanned
                require(remaining > 0) {
                    "Office archive exceeds the safe extraction budget."
                }
                // Reading every entry, even skipped pictures, bounds the total
                // uncompressed workload and limits hostile ZIP expansion.
                val data = readBounded(archive, minOf(MAX_OFFICE_ENTRY, remaining))
                scanned += data.size
                when {
                    kind == "odt" && path == "mimetype" ->
                        mime = decode(data).trim()
                    kind == "odt" && path == "content.xml" ->
                        body = odtBody(data)
                    kind == "pptx" && path == "[Content_Types].xml" ->
                        types = decode(data)
                    kind == "pptx" && path == "ppt/presentation.xml" ->
                        foundPresentation = true
                    kind == "pptx" && slidePath.matches(path) -> {
                        val number = slidePath.matchEntire(path)!!.groupValues[1].toInt()
                        require(slides.size < 150 && number !in slides) {
                            "Presentation exceeds the safe slide limit or duplicates a slide."
                        }
                        val content = xml(data, office = true)
                        if (content.isNotBlank()) slides[number] = content.take(MAX_CHARS)
                    }
                }
            }
        }
        if (kind == "odt") {
            require(mime == "application/vnd.oasis.opendocument.text") {
                "This is not a standard OpenDocument text file."
            }
            return body?.takeIf { it.isNotBlank() }?.take(MAX_CHARS)
                ?: throw IllegalArgumentException(
                    "ODT has no readable body text. Scanned or encrypted content is unsupported."
                )
        }
        require(foundPresentation &&
            types?.contains("presentationml.presentation.main+xml") == true) {
            "This is not a standard PowerPoint presentation."
        }
        require(slides.isNotEmpty()) {
            "PPTX has no readable slide text. Scanned slides and images need OCR."
        }
        return buildString {
            for ((number, value) in slides) {
                val header = "Slide $number:\n"
                if (length + header.length >= MAX_CHARS) break
                append(header)
                append(value.take(MAX_CHARS - length))
                if (length < MAX_CHARS) append('\n')
            }
        }.trim().take(MAX_CHARS)
    }

    /** ODT paragraphs/headings only; never surface scripts, metadata or XML tags. */
    private fun odtBody(bytes: ByteArray): String {
        val officeNamespace =
            "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        val textNamespace =
            "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
        val output = StringBuilder()
        var insideBody = false
        var paragraphs = 0
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
                    if (uri == officeNamespace && localName == "text") {
                        insideBody = true
                    } else if (insideBody && uri == textNamespace) {
                        when (localName) {
                            "p", "h" -> {
                                if (output.isNotEmpty() && output.length < MAX_CHARS)
                                    output.append('\n')
                                paragraphs++
                            }
                            "tab" -> if (paragraphs > 0 && output.length < MAX_CHARS)
                                output.append('\t')
                            "line-break" -> if (paragraphs > 0 && output.length < MAX_CHARS)
                                output.append('\n')
                            "s" -> if (paragraphs > 0 && output.length < MAX_CHARS) {
                                val count = attributes?.getValue(textNamespace, "c")
                                    ?.toIntOrNull()?.coerceIn(1, 16) ?: 1
                                output.append(" ".repeat(minOf(count, MAX_CHARS - output.length)))
                            }
                        }
                    }
                }
                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    if (insideBody && uri == textNamespace &&
                        (localName == "p" || localName == "h")) {
                        paragraphs = (paragraphs - 1).coerceAtLeast(0)
                    } else if (uri == officeNamespace && localName == "text") {
                        insideBody = false
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (insideBody && paragraphs > 0 && output.length < MAX_CHARS) {
                        output.append(ch, start, minOf(length, MAX_CHARS - output.length))
                    }
                }
            }
            parse(InputSource(ByteArrayInputStream(bytes)))
        }
        return output.toString().trim()
    }
}
