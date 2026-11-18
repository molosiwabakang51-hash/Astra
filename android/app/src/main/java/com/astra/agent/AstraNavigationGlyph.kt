package com.astra.agent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * One consistent, entirely offline vector family for the six Astra destinations.
 * Icons are decorative beside the existing labelled, >=48dp dropdown rows.
 * Avoid Unicode/emoji shapes that vary across phone fonts.
 */
internal enum class AstraNavSection {
    CHAT, TASKS, NOTES, TOOLS, SETTINGS, STUDY
}

@Composable
internal fun AstraNavigationGlyph(
    section: AstraNavSection,
    tint: Color = AstraAurora.cyan
) {
    Canvas(Modifier.size(23.dp)) {
        val w = size.width
        val h = size.height
        val line = 2.dp.toPx()
        val thinStroke = Stroke(width = line, cap = StrokeCap.Round)

        when (section) {
            AstraNavSection.CHAT -> {
                val bubble = Path().apply {
                    moveTo(w * 0.16f, h * 0.22f)
                    lineTo(w * 0.84f, h * 0.22f)
                    lineTo(w * 0.84f, h * 0.70f)
                    lineTo(w * 0.48f, h * 0.70f)
                    lineTo(w * 0.27f, h * 0.87f)
                    lineTo(w * 0.27f, h * 0.70f)
                    lineTo(w * 0.16f, h * 0.70f)
                    close()
                }
                drawPath(bubble, tint, style = thinStroke)
                drawLine(tint, Offset(w * 0.32f, h * 0.42f),
                    Offset(w * 0.68f, h * 0.42f),
                    strokeWidth = line, cap = StrokeCap.Round)
            }
            AstraNavSection.TASKS -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.14f, h * 0.16f),
                    size = Size(w * 0.72f, h * 0.70f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = thinStroke
                )
                drawLine(tint, Offset(w * 0.27f, h * 0.51f),
                    Offset(w * 0.42f, h * 0.66f),
                    strokeWidth = line, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.42f, h * 0.66f),
                    Offset(w * 0.73f, h * 0.34f),
                    strokeWidth = line, cap = StrokeCap.Round)
            }
            AstraNavSection.NOTES -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.20f, h * 0.11f),
                    size = Size(w * 0.60f, h * 0.78f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = thinStroke
                )
                repeat(3) { index ->
                    val y = h * (0.34f + 0.15f * index)
                    drawLine(tint, Offset(w * 0.32f, y), Offset(w * 0.68f, y),
                        strokeWidth = line, cap = StrokeCap.Round)
                }
            }
            AstraNavSection.TOOLS -> {
                repeat(4) { index ->
                    val x = w * (0.16f + (index % 2) * 0.38f)
                    val y = h * (0.16f + (index / 2) * 0.38f)
                    drawRoundRect(
                        tint,
                        topLeft = Offset(x, y),
                        size = Size(w * 0.28f, h * 0.28f),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = thinStroke
                    )
                }
            }
            AstraNavSection.SETTINGS -> {
                drawCircle(tint, radius = w * 0.285f, style = thinStroke)
                drawCircle(tint, radius = w * 0.075f, style = thinStroke)
                repeat(8) { index ->
                    val a = index * Math.PI / 4.0
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    drawLine(
                        tint,
                        Offset(w * (0.5f + dx * 0.31f), h * (0.5f + dy * 0.31f)),
                        Offset(w * (0.5f + dx * 0.43f), h * (0.5f + dy * 0.43f)),
                        strokeWidth = line, cap = StrokeCap.Round
                    )
                }
            }
            AstraNavSection.STUDY -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.25f),
                    Offset(w * 0.5f, h * 0.83f),
                    strokeWidth = line, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.29f),
                    Offset(w * 0.13f, h * 0.19f),
                    strokeWidth = line, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.29f),
                    Offset(w * 0.87f, h * 0.19f),
                    strokeWidth = line, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.13f, h * 0.19f),
                    Offset(w * 0.13f, h * 0.71f),
                    strokeWidth = line, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.87f, h * 0.19f),
                    Offset(w * 0.87f, h * 0.71f),
                    strokeWidth = line, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.13f, h * 0.71f),
                    Offset(w * 0.5f, h * 0.83f),
                    strokeWidth = line, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.87f, h * 0.71f),
                    Offset(w * 0.5f, h * 0.83f),
                    strokeWidth = line, cap = StrokeCap.Round)
            }
        }
    }
}
