package com.astra.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight vector graphics, not a square image over a white surface.
 * Infinite motion is limited to the compact brand mark.
 */
@Composable
internal fun AstraBrand(modifier: Modifier = Modifier, listening: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "astra-orbit")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7200), RepeatMode.Restart),
        label = "orbit-rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (listening) 650 else 1800), RepeatMode.Reverse),
        label = "brand-pulse"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(34.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val radius = w * 0.43f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF205A91).copy(alpha = 0.22f * pulse), Color.Transparent),
                        center = Offset(cx, cy), radius = w * 0.65f
                    ),
                    radius = w * 0.5f
                )
                val start = Math.toRadians(rotation.toDouble())
                val sweep = Math.toRadians(245.0)
                val orbit = Path()
                for (i in 0..65) {
                    val angle = start + sweep * i / 65.0
                    val x = cx + radius * cos(angle).toFloat()
                    val y = cy + radius * 0.82f * sin(angle).toFloat()
                    if (i == 0) orbit.moveTo(x, y) else orbit.lineTo(x, y)
                }
                drawPath(orbit, Color(0xFF56BAFF).copy(alpha = pulse), style = Stroke(w * 0.055f, cap = StrokeCap.Round))
                val a = Path().apply {
                    moveTo(w * 0.27f, h * 0.76f)
                    lineTo(w * 0.53f, h * 0.22f)
                    lineTo(w * 0.73f, h * 0.76f)
                }
                drawPath(a, Color(0xFFE8F2FF), style = Stroke(w * 0.085f, cap = StrokeCap.Round))
                drawLine(
                    Color(0xFF9FD5FF),
                    Offset(w * 0.37f, h * 0.61f),
                    Offset(w * 0.70f, h * 0.53f),
                    strokeWidth = w * 0.07f,
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            "Astra",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.6).sp,
            color = Color(0xFFEAF4FF)
        )
    }
}

@Composable
internal fun AstraAmbientBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF080E19), Color(0xFF0A1424), Color(0xFF070B13))
            )
        )
    )
}
