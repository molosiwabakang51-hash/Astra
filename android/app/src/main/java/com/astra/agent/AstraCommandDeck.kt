package com.astra.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Sci-fi command deck. Status labels are deliberately factual; it does not
 * claim autonomous capabilities, model inference or connectivity it lacks.
 */
internal object AstraDeck {
    val prompts = listOf(
        "Show tasks",
        "Russian quiz",
        "Calculate 12 * 5",
        "Show notes",
        "Help"
    )

    fun status(listening: Boolean, researching: Boolean): String = when {
        listening -> "VOICE INPUT ACTIVE"
        researching -> "RESEARCH IN PROGRESS"
        else -> "LOCAL SYSTEM READY"
    }
}

@Composable
internal fun AstraCommandDeck(
    listening: Boolean,
    researching: Boolean,
    onCommand: (String) -> Unit
) {
    val status = AstraDeck.status(listening, researching)
    val animation = rememberInfiniteTransition(label = "astra-deck")
    val sweep by animation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Restart),
        label = "scanline"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF142A42), Color(0xFF0C1729))
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                "ASTRA // COMMAND DECK",
                color = Color(0xFFBBDFFF),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium
            )
            Text("v0.9", color = Color(0xFF7199BC), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(status, color = Color(0xFF6ED2FF), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(7.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val y = size.height / 2f
            drawLine(Color(0xFF1D4564), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
            drawLine(
                Color(0xFF70D7FF),
                Offset((size.width * sweep).coerceAtMost(size.width), y),
                Offset((size.width * (sweep + 0.12f)).coerceAtMost(size.width), y),
                2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AstraDeck.prompts.forEach { command ->
                Text(
                    text = command,
                    modifier = Modifier
                        .background(Color(0xFF20364F), RoundedCornerShape(50))
                        .clickable { onCommand(command) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color(0xFFE4F5FF),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
