package com.astra.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
internal fun AstraBrand(modifier: Modifier = Modifier, listening: Boolean = false) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AstraOrb(diameter = 38.dp, compact = true, listening = listening)
        Spacer(Modifier.width(6.dp))
        Text(
            "Astra",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
            color = AstraAurora.ink
        )
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
