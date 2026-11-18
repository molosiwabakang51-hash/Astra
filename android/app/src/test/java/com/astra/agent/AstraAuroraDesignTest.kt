package com.astra.agent

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraAuroraDesignTest {
    @Test fun visibleTextOnDarkCardsExceedsWcagAaNormalTextContrast() {
        val surfaces = listOf(AstraAurora.background, AstraAurora.surface,
            AstraAurora.raised, AstraAurora.night)
        for (background in surfaces) {
            for (foreground in listOf(AstraAurora.ink, AstraAurora.muted,
                AstraAurora.cyan, AstraAurora.violet, AstraAurora.mint)) {
                assertTrue(
                    "Insufficient contrast between label and card.",
                    AstraAurora.contrast(foreground, background) >= 4.5
                )
            }
        }
    }

    @Test fun invalidAnimationSettingsFailClosed() {
        assertTrue(!AstraAurora.motionEnabled(0f))
        assertTrue(!AstraAurora.motionEnabled(-1f))
        assertTrue(!AstraAurora.motionEnabled(Float.NaN))
        assertTrue(!AstraAurora.motionEnabled(Float.POSITIVE_INFINITY))
        assertTrue(AstraAurora.motionEnabled(1f))
    }

    @Test fun headerStatusUsesObservedVoiceAndGenerationState() {
        org.junit.Assert.assertEquals("LOCAL ASSISTANT", AstraAurora.statusLabel(false, false))
        org.junit.Assert.assertEquals("GENERATING", AstraAurora.statusLabel(false, true))
        org.junit.Assert.assertEquals("LISTENING", AstraAurora.statusLabel(true, false))
        org.junit.Assert.assertEquals("LISTENING", AstraAurora.statusLabel(true, true))
    }

    @Test fun readableTypographyIsNotThin() {
        assertTrue(AstraReadableTypography.bodyLarge.fontWeight!!.weight >= FontWeight.Medium.weight)
        assertTrue(AstraReadableTypography.bodySmall.fontWeight!!.weight >= FontWeight.Medium.weight)
        assertTrue(AstraReadableTypography.titleMedium.fontWeight!!.weight >= FontWeight.SemiBold.weight)
        assertTrue(AstraReadableTypography.bodyMedium.fontSize.value >= 15f)
    }
}
