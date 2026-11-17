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
 * bold/italic emphasis, unordered and numbered lists, inline code, and fenced
 * code. No HTML, image loading, links, networking, JavaScript or file access.
 * Original messages remain unchanged for persistence, copy, share and voice.
 */
internal object AstraMessageMarkup {
    private val heading = Regex("""^\s{0,3}(#{1,4})\s+(.+)$""")
    private val bullet = Regex("""^\s{0,4}[-*+]\s+(.+)$""")
    private val numbered = Regex("""^\s{0,4}(\d{1,3})[.)]\s+(.+)$""")

    fun render(source: String): AnnotatedString = buildAnnotatedString {
        val lines = source.replace("\r\n", "\n").split('\n')
        var inFence = false
        var renderedLine = false
        for (line in lines) {
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                continue
            }
            if (renderedLine) append('\n')
            renderedLine = true
            if (inFence) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                append(line)
                pop()
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
