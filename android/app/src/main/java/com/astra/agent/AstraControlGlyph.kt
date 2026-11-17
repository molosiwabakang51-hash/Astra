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

/** Small original vector glyphs; icon labels are supplied by their clickable parents. */
internal enum class AstraControl { VOICE, SEND, STOP }

@Composable
internal fun AstraControlGlyph(kind: AstraControl, tint: Color = AstraAurora.ink) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val line = w * 0.095f
        when (kind) {
            AstraControl.VOICE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.42f, h * 0.12f),
                    size = Size(w * 0.16f, h * 0.45f),
                    cornerRadius = CornerRadius(w * 0.075f)
                )
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.29f, h * 0.29f),
                    size = Size(w * 0.42f, h * 0.45f),
                    style = Stroke(width = line, cap = StrokeCap.Round)
                )
                drawLine(tint, Offset(w * 0.5f, h * 0.72f),
                    Offset(w * 0.5f, h * 0.88f), strokeWidth = line,
                    cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.37f, h * 0.88f),
                    Offset(w * 0.63f, h * 0.88f), strokeWidth = line,
                    cap = StrokeCap.Round)
            }
            AstraControl.SEND -> {
                val arrow = Path().apply {
                    moveTo(w * 0.20f, h * 0.19f)
                    lineTo(w * 0.84f, h * 0.50f)
                    lineTo(w * 0.20f, h * 0.81f)
                    lineTo(w * 0.31f, h * 0.50f)
                    close()
                }
                drawPath(arrow, tint, style = Stroke(width = line,
                    cap = StrokeCap.Round))
                drawLine(tint, Offset(w * 0.31f, h * 0.50f),
                    Offset(w * 0.72f, h * 0.50f),
                    strokeWidth = line, cap = StrokeCap.Round)
            }
            AstraControl.STOP -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.26f, h * 0.26f),
                    size = Size(w * 0.48f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.08f)
                )
            }
        }
    }
}
