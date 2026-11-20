package com.astra.agent

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * Readable presentation for Qwen's local assistant messages.
 *
 * This is deliberately a bounded, non-executing Markdown subset: headings,
 * bold/italic emphasis, unordered and numbered lists, inline code, fenced
 * code and bounded readable Markdown tables. No HTML, image loading, links,
 * networking, JavaScript or file access.
 * Original messages remain unchanged for persistence, copy, share and voice.
 */
internal object AstraMessageMarkup {
    private val heading = Regex("""^\s{0,3}(#{1,4})\s+(.+)$""")
    private val bullet = Regex("""^\s{0,4}[-*+]\s+(.+)$""")
    private val numbered = Regex("""^\s{0,4}(\d{1,3})[.)]\s+(.+)$""")

    // A Markdown grid is not usable on a narrow Android chat bubble.
    // Turn only well-formed, completed tables into stacked labelled rows.
    // The underlying message, clipboard, exports and speech stay untouched.
    private data class ParsedTable(
        val headings: List<String>,
        val rows: List<List<String>>,
        val consumedLines: Int
    )
    private val tableDivider = Regex("""^:?-{3,}:?$""")

    private fun tableCells(line: String): List<String>? {
        val trimmed = line.trim()
        if (!trimmed.contains('|') || trimmed.length > 1_000 ||
            trimmed.contains("\\|") || trimmed.contains('`')
        ) return null
        val withoutStart = trimmed.removePrefix("|")
        val withoutEnd = withoutStart.removeSuffix("|")
        val cells = withoutEnd.split('|').map { it.trim() }
        return cells.takeIf {
            it.size in 2..6 && it.all { cell -> cell.length <= 130 }
        }
    }

    private fun parseTable(lines: List<String>, start: Int): ParsedTable? {
        if (start + 2 >= lines.size) return null
        val headings = tableCells(lines[start]) ?: return null
        if (headings.any { it.isBlank() || it.length > 70 }) return null
        val separators = tableCells(lines[start + 1]) ?: return null
        if (separators.size != headings.size ||
            separators.any { !tableDivider.matches(it) }
        ) return null
        val rows = mutableListOf<List<String>>()
        var next = start + 2
        while (next < lines.size) {
            val cells = tableCells(lines[next]) ?: break
            if (cells.size != headings.size) break
            // Never partially reformat an oversized streamed table.
            if (rows.size == 24) return null
            rows.add(cells)
            next++
        }
        if (rows.isEmpty()) return null
        return ParsedTable(headings, rows, next - start)
    }

    fun render(source: String): AnnotatedString = buildAnnotatedString {
        val lines = source.replace("\r\n", "\n").split('\n')
        var inFence = false
        var renderedLine = false
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                index++
                continue
            }
            // A valid completed table becomes labelled, wrapping-friendly
            // data rows. A partial, malformed, huge or code-fenced table
            // is preserved literally instead of guessing at missing cells.
            val table = if (!inFence) parseTable(lines, index) else null
            if (table != null) {
                for (row in table.rows) {
                    if (renderedLine) append('\n')
                    renderedLine = true
                    append("• ")
                    row.forEachIndexed { column, cell ->
                        if (column > 0) append("\n   ")
                        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                        appendInline(table.headings[column])
                        pop()
                        append(": ")
                        appendInline(cell)
                    }
                }
                index += table.consumedLines
                continue
            }
            if (renderedLine) append('\n')
            renderedLine = true
            if (inFence) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                append(line)
                pop()
                index++
                continue
            }
            val title = heading.matchEntire(line)
            val item = bullet.matchEntire(line)
            val ordered = numbered.matchEntire(line)
            when {
                title != null -> {
                    val level = title.groupValues[1].length
                    pushStyle(SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (level <= 2) 1.14.em else 1.05.em
                    ))
                    appendInline(title.groupValues[2])
                    pop()
                }
                item != null -> {
                    append("• ")
                    appendInline(item.groupValues[1])
                }
                ordered != null -> {
                    append(ordered.groupValues[1])
                    append(". ")
                    appendInline(ordered.groupValues[2])
                }
                else -> appendInline(line)
            }
            index++
        }
    }

    private fun AnnotatedString.Builder.appendInline(line: String) {
        var i = 0
        while (i < line.length) {
            val marker = when {
                line.startsWith("**", i) -> "**"
                line.startsWith("__", i) -> "__"
                line[i] == '`' -> "`"
                line[i] == '*' || line[i] == '_' -> line[i].toString()
                else -> null
            }
            if (marker == null) {
                append(line[i])
                i++
                continue
            }
            val after = i + marker.length
            val end = line.indexOf(marker, after)
            // Do not mistake multiplication or unfinished streaming markers
            // for markup, and do not format empty/whitespace-only spans.
            val valid = end > after && line.substring(after, end).any { !it.isWhitespace() } &&
                (marker == "`" || (after < line.length && !line[after].isWhitespace() &&
                    !line[end - 1].isWhitespace())) &&
                (marker.length > 1 || marker == "`" || i == 0 ||
                    !line[i - 1].isLetterOrDigit())
            if (!valid) {
                append(marker)
                i = after
                continue
            }
            val style = when (marker) {
                "`" -> SpanStyle(fontFamily = FontFamily.Monospace)
                "**", "__" -> SpanStyle(fontWeight = FontWeight.Bold)
                else -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
            pushStyle(style)
            append(line, after, end)
            pop()
            i = end + marker.length
        }
    }
}
