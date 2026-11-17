package com.astra.agent

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** A single, original visual identity: Aurora. All artwork is drawn offline. */
internal object AstraAurora {
    val background = Color(0xFF080C18)
    val surface = Color(0xFF121C2D)
    val raised = Color(0xFF1B2940)
    val night = Color(0xFF0D1525)
    val cyan = Color(0xFF7CE6F6)
    val violet = Color(0xFFB8A4FF)
    val mint = Color(0xFF9CF5D4)
    val ink = Color(0xFFEAF5FF)
    val muted = Color(0xFFB3C5DD)

    // All orbital animation is optional; the core still makes sense when static.
    fun motionEnabled(scale: Float): Boolean = scale.isFinite() && scale > 0f

    /** Fixed geometry keeps the vector recognizable independent of display density. */
    fun pointAngle(index: Int, count: Int): Double {
        require(count in 1..64 && index in 0 until count)
        return 2.0 * Math.PI * index / count
    }

    /** Relative text luminance, useful for preventing accidental low contrast. */
    internal fun luminance(color: Color): Double {
        fun linear(x: Float): Double {
            val c = x.toDouble()
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }

    internal fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}

/**
 * Reusable Astra core for header and new chat.
 * No downloaded image, GPU shader, camera, audio analysis, network or file access.
 * A slow orbit runs only while this composable is visible and system motion allows it.
 */
@Composable
internal fun AstraOrb(
    diameter: Dp,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    listening: Boolean = false
) {
    val context = LocalContext.current
    val motion = remember(context) {
        AstraAurora.motionEnabled(runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        }.getOrDefault(1f))
    }
    // Keep the persistent header mark static until voice input is active.
    // The large new-chat hero animates only while that panel is visible.
    val rotation = if (motion && (!compact || listening)) {
        val transition = rememberInfiniteTransition(label = "astra-aurora")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (compact) 14000 else 18000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbital-drift"
        )
        value
    } else 0f

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val unit = size.minDimension
            val cx = center.x
            val cy = center.y
            val glow = if (listening) AstraAurora.mint else AstraAurora.cyan
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        glow.copy(alpha = if (compact) 0.25f else 0.28f),
                        AstraAurora.violet.copy(alpha = 0.10f),
                        Color.Transparent
                    ), center = center, radius = unit * 0.49f
                ),
                radius = unit * 0.49f
            )

            val rings = if (compact) 2 else 3
            repeat(rings) { index ->
                val radius = unit * (0.24f + index * 0.085f)
                drawCircle(
                    color = AstraAurora.cyan.copy(alpha = 0.08f + index * 0.035f),
                    radius = radius, style = Stroke(width = unit * 0.006f)
                )
                val start = rotation * (if (index % 2 == 0) 1 else -0.7f) +
                    35f + index * 115f
                drawArc(
                    color = if (index == 1) AstraAurora.violet else AstraAurora.cyan,
                    startAngle = start,
                    sweepAngle = if (index == 1) 155f else 93f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = unit * (if (compact) 0.017f else 0.012f),
                        cap = StrokeCap.Round)
                )
            }

            val dots = if (compact) 5 else 16
            repeat(dots) { index ->
                val theta = AstraAurora.pointAngle(index, dots) +
                    Math.toRadians(rotation.toDouble() * 0.12)
                val radius = unit * (if (index % 3 == 0) 0.43f else 0.37f)
                val dot = Offset(
                    cx + radius * cos(theta).toFloat(),
                    cy + radius * sin(theta).toFloat()
                )
                drawCircle(
                    if (index % 4 == 0) AstraAurora.violet else AstraAurora.cyan,
                    radius = unit * (if (compact) 0.009f else 0.008f),
                    center = dot,
                    alpha = if (index % 3 == 0) 0.86f else 0.48f
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF368CC4), Color(0xFF1E3D71), Color(0xFF101B35)),
                    center = Offset(cx - unit * 0.045f, cy - unit * 0.06f),
                    radius = unit * 0.25f
                ), radius = unit * 0.205f
            )
            // Original stylized "A" is retained as the brand's recognisable mark.
            val stroke = unit * 0.025f
            drawLine(
                AstraAurora.ink,
                Offset(cx - unit * 0.09f, cy + unit * 0.11f),
                Offset(cx, cy - unit * 0.12f),
                strokeWidth = stroke, cap = StrokeCap.Round
            )
            drawLine(
                AstraAurora.ink,
                Offset(cx, cy - unit * 0.12f),
                Offset(cx + unit * 0.09f, cy + unit * 0.11f),
                strokeWidth = stroke, cap = StrokeCap.Round
            )
            drawLine(
                AstraAurora.mint,
                Offset(cx - unit * 0.045f, cy + unit * 0.04f),
                Offset(cx + unit * 0.052f, cy + unit * 0.005f),
                strokeWidth = stroke * 0.9f, cap = StrokeCap.Round
            )
        }
    }
}
