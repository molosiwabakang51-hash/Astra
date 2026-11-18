package com.astra.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/** Compact identity reused by navigation without wasting the chat header. */
@Composable
internal fun AstraBrand(
    modifier: Modifier = Modifier,
    listening: Boolean = false,
    thinking: Boolean = false
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AstraOrb(
            diameter = 38.dp, compact = true,
            listening = listening, thinking = thinking
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                "Astra",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = AstraAurora.ink
            )
            Text(
                AstraAurora.statusLabel(listening, thinking),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = when {
                    listening -> AstraAurora.mint
                    thinking -> AstraAurora.violet
                    else -> AstraAurora.muted
                }
            )
        }
    }
}

/** Low-noise dark backdrop, no raster wallpaper or network dependency. */
@Composable
internal fun AstraAmbientBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(AstraAurora.background, AstraAurora.night, AstraAurora.background)
            )
        )
    )
}
