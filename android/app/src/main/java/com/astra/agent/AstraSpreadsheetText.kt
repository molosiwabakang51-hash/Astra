package com.astra.agent

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Read-only, bounded .xlsx worksheet-value extraction for the permanent
 * offline book index. Formulas, macros and external links are never executed.
 * Numbers are stored as-is: Excel date serials are not guessed as dates.
 */
internal object AstraSpreadsheetText {
    private const val MAX_ENTRIES = 350
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 6 * 1024 * 1024
    private const val MAX_SHEETS = 3
    private const val MAX_SHARED_STRINGS = 8_000
    private const val MAX_ROWS = 1_500
    private const val MAX_CELLS = 60_000
    private const val MAX_OUTPUT_CHARS = 700_000
    private val sheetPath = Regex("""xl/worksheets/sheet([1-9][0-9]*)\.xml""")
    private val columnRef = Regex("""^[A-Z]{1,3}""")

    fun extract(input: InputStream): String {
        val worksheets = mutableListOf<Pair<Int, ByteArray>>()
        var sharedXml: ByteArray? = null
        var scanned = 0
        var entries = 0
        ZipInputStream(AstraProgressInputStream(input).buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                require(++entries <= MAX_ENTRIES) { "XLSX has too many archive entries." }
                val path = entry.name.replace('\\', '/')
                require(!path.startsWith('/') && path.split('/').none { it == ".." }) {
                    "Unsafe XLSX archive entry."
                }
                if (!entry.isDirectory) {
                    val number = sheetPath.matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()
                    val keep = path == "xl/sharedStrings.xml" ||
                        (number != null && worksheets.size < MAX_SHEETS)
                    // Even skipped images and metadata count toward the decompression
                    // limit, so untrusted ZIP entries cannot bypass this budget.
                    val (length, bytes) = readBounded(
                        archive, minOf(MAX_ENTRY_BYTES, MAX_BYTES - scanned), keep
                    )
                    scanned += length
                    if (path == "xl/sharedStrings.xml") sharedXml = bytes
                    else if (number != null && keep && bytes != null)
                        worksheets.add(number to bytes)
                }
                archive.closeEntry()
            }
        }
        require(worksheets.isNotEmpty()) { "XLSX has no readable worksheets." }
        val shared = sharedXml?.let(::sharedStrings).orEmpty()
        val output = StringBuilder(
            "SPREADSHEET VALUES (raw values; formulas not calculated; " +
                "dates may be Excel serial numbers)\n"
        )
        var readable = false
        for ((number, bytes) in worksheets.sortedBy { it.first }) {
            if (output.length >= MAX_OUTPUT_CHARS) break
            val sheet = worksheet(bytes, shared, MAX_OUTPUT_CHARS - output.length - 20)
            if (sheet.isNotBlank()) {
                readable = true
                output.append("SHEET ").append(number).append(":\n").append(sheet)
            }
        }
        require(readable) { "XLSX has no readable cell values." }
        return output.take(MAX_OUTPUT_CHARS).toString()
    }

    private fun readBounded(
        stream: InputStream, max: Int, retain: Boolean
    ): Pair<Int, ByteArray?> {
        require(max > 0) { "XLSX exceeds safe decompression limit." }
        val output = if (retain) ByteArrayOutputStream() else null
        val buffer = ByteArray(16 * 1024)
        var count = 0
        val progress = AstraProgressInputStream(stream)
        while (true) {
            val n = progress.read(buffer)
            if (n < 0) break
            require(count + n <= max) { "XLSX entry exceeds safe extraction limit." }
            count += n
            output?.write(buffer, 0, n)
        }
        return count to output?.toByteArray()
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newSAXParser().xmlReader.apply {
            entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
            contentHandler = handler
            parse(InputSource(ByteArrayInputStream(xml)))
        }
    }

    private fun sharedStrings(xml: ByteArray): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inItem = false
        var inText = false
        parse(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes?) {
                when (localName) {
                    "si" -> { inItem = true; current.setLength(0) }
                    "t" -> if (inItem) inText = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText && current.length < 1_000)
                    current.append(ch, start, minOf(length, 1_000 - current.length))
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (localName) {
                    "t" -> inText = false
                    "si" -> {
                        require(values.size < MAX_SHARED_STRINGS) { "XLSX shared text exceeds limit." }
                        values.add(current.toString())
                        inItem = false
                    }
                }
            }
        })
        return values
    }

    private fun worksheet(xml: ByteArray, shared: List<String>, maxChars: Int): String {
        val output = StringBuilder()
        val fields = mutableListOf<String>()
        val content = StringBuilder()
        var rows = 0
        var cells = 0
        var rowNumber = ""
        var reference = ""
        var type = ""
        var insideCell = false
        var reading = false
        var formula = false
        var truncated = false
        parse(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes?) {
                when (localName) {
                    "row" -> {
                        rows++
                        rowNumber = atts?.getValue("r")?.take(10) ?: rows.toString()
                        fields.clear()
                        if (rows > MAX_ROWS) truncated = true
                    }
                    "c" -> {
                        insideCell = true
                        cells++
                        if (cells > MAX_CELLS) truncated = true
                        reference = atts?.getValue("r").orEmpty().take(12)
                        type = atts?.getValue("t").orEmpty()
                        content.setLength(0)
                        formula = false
                    }
                    "f" -> if (insideCell) formula = true
                    "v" -> if (insideCell) reading = true
                    "t" -> if (insideCell && type == "inlineStr") reading = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (reading && !truncated && content.length < 1_000)
                    content.append(ch, start, minOf(length, 1_000 - content.length))
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (localName) {
                    "v", "t" -> reading = false
                    "c" -> {
                        if (!truncated && fields.size < 64) {
                            val raw = content.toString().trim()
                            val value = when {
                                formula -> "[formula omitted]"
                                type == "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1)
                                    ?: "[missing shared string]"
                                type == "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            if (value.isNotEmpty()) {
                                val column = columnRef.find(reference)?.value ?: "Cell"
                                fields.add(column + ": " + value.take(1_000))
                            }
                        }
                        insideCell = false
                    }
                    "row" -> {
                        if (!truncated && fields.isNotEmpty() && output.length < maxChars)
                            output.append("Row ").append(rowNumber).append(": ")
                                .append(fields.joinToString("; ")).append('\n')
                        if (output.length >= maxChars) truncated = true
                    }
                }
            }
        })
        if (truncated) output.append("[Worksheet truncated for memory safety]\n")
        return output.take(maxChars.coerceAtLeast(0)).toString()
    }
}
