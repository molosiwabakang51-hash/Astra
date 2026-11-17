package com.astra.agent

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Stronger, clearer type hierarchy across chat, Study, Settings and dialogs.
 * Uses Android's system sans family; no downloaded font or binary assets.
 * Respects Android user font scaling because all sizes are in sp.
 */
internal val AstraReadableTypography: Typography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(
            fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold
        ),
        titleMedium = base.titleMedium.copy(
            fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold
        ),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(
            fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium
        ),
        bodyMedium = base.bodyMedium.copy(
            fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium
        ),
        bodySmall = base.bodySmall.copy(
            fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium
        ),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
    )
}
