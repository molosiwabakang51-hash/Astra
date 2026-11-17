package com.astra.agent

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * New-conversation welcome surface, visible only until the first user turn.
 * Quick actions are real existing offline commands; no simulated intelligence.
 */
internal object AstraWelcomePolicy {
    val commands = listOf(
        "Teach me finance",
        "Memory overview",
        "Show tasks",
        "Show notes"
    )

    fun show(roles: List<String>): Boolean = roles.none { it == "You" || it == "user" }

    /** Treat Android's disabled animator scale as a hard stop for the orbit. */
    fun animateForScale(scale: Float): Boolean = scale.isFinite() && scale > 0f
}

@Composable
internal fun AstraWelcomePanel(
    onOpenNavigation: () -> Unit,
    onCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val animate = remember(context) {
        // Respect the owner's Android accessibility / developer animation
        // settings. No permission or persistent tracking is introduced.
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        }.getOrDefault(1f)
        AstraWelcomePolicy.animateForScale(scale)
    }
    val orbit = if (animate) {
        val transition = rememberInfiniteTransition(label = "astra-welcome")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "welcome-orbit"
        )
        angle
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF15253D), Color(0xFF0F1A2D), Color(0xFF0D1524))
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(94.dp)
                .semantics { contentDescription = "Open Astra navigation" }
                .clickable(onClick = onOpenNavigation),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(88.dp)) {
                val radius = size.minDimension * 0.36f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF386CB5).copy(alpha = 0.46f), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.5f
                    ),
                    radius = size.minDimension * 0.5f
                )
                drawCircle(
                    color = Color(0xFF4B86BE).copy(alpha = 0.78f),
                    radius = radius,
                    style = Stroke(width = 1.6.dp.toPx())
                )
                val angle = Math.toRadians(orbit.toDouble())
                val point = Offset(
                    center.x + radius * cos(angle).toFloat(),
                    center.y + radius * sin(angle).toFloat()
                )
                drawCircle(Color(0xFF8BD9FF), radius = 4.dp.toPx(), center = point)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF75CAFF), Color(0xFF20528B)),
                        center = center,
                        radius = size.minDimension * 0.14f
                    ),
                    radius = size.minDimension * 0.13f
                )
                drawLine(
                    color = Color(0xFFE5F6FF),
                    start = Offset(center.x - 5.dp.toPx(), center.y + 5.dp.toPx()),
                    end = Offset(center.x, center.y - 6.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFE5F6FF),
                    start = Offset(center.x, center.y - 6.dp.toPx()),
                    end = Offset(center.x + 5.dp.toPx(), center.y + 5.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            "Your offline workspace",
            color = Color(0xFFE7F4FF),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Ask a question or explore your local notes and tools. " +
                "Tap the orb to open navigation.",
            color = Color(0xFFADC4D8),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(17.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AstraStarterAction("✦  Study", AstraWelcomePolicy.commands[0], onCommand,
                Modifier.weight(1f))
            AstraStarterAction("◌  Recall", AstraWelcomePolicy.commands[1], onCommand,
                Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AstraStarterAction("☷  Tasks", AstraWelcomePolicy.commands[2], onCommand,
                Modifier.weight(1f))
            AstraStarterAction("▤  Notes", AstraWelcomePolicy.commands[3], onCommand,
                Modifier.weight(1f))
        }
    }
}

@Composable
private fun AstraStarterAction(
    label: String,
    command: String,
    onCommand: (String) -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(Color(0xFF213852), RoundedCornerShape(14.dp))
            .semantics { contentDescription = "$label: $command" }
            .clickable { onCommand(command) }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFFDBEEFF),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}
