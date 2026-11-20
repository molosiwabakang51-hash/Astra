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

    @Test fun liveMotionPolicyRespectsAllDisabledOrInvalidSystemScales() {
        for (scale in listOf(
            0f, -0.1f, -100f, Float.NaN, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        )) {
            assertTrue("Reduced motion must stay static for scale $scale",
                !AstraAurora.motionEnabled(scale))
        }
        // A tiny positive custom animator scale must still behave as the
        // user's enabled choice, while negative zero must remain disabled.
        assertTrue(!AstraAurora.motionEnabled(-0.0f))
        assertTrue(AstraAurora.motionEnabled(Float.MIN_VALUE))
        for (scale in listOf(0.1f, 0.5f, 1f, 2f, 10f, Float.MAX_VALUE)) {
            assertTrue("Enabled system motion remains opt-in for $scale",
                AstraAurora.motionEnabled(scale))
        }
        assertTrue(!AstraAurora.motionEnabled(0f))
        assertTrue(AstraAurora.motionEnabled(1f))
        assertTrue(!AstraAurora.motionEnabled(0f))
    }

    @Test fun headerStatusUsesObservedVoiceAndGenerationState() {
        org.junit.Assert.assertEquals("LOCAL ASSISTANT", AstraAurora.statusLabel(false, false))
        org.junit.Assert.assertEquals("GENERATING", AstraAurora.statusLabel(false, true))
        org.junit.Assert.assertEquals("LISTENING", AstraAurora.statusLabel(true, false))
        org.junit.Assert.assertEquals("LISTENING", AstraAurora.statusLabel(true, true))
    }

    @Test fun coreStatusAndDisabledTrayUseHighContrastThemeText() {
        // The status colours must stay readable on Astra's dark base;
        // disabled navigation actions use the same un-faded muted colour.
        for (statusColor in listOf(
            AstraAurora.muted, AstraAurora.mint, AstraAurora.violet
        )) {
            assertTrue(
                "Core status needs readable contrast on the dark header.",
                AstraAurora.contrast(statusColor, AstraAurora.background) >= 4.5
            )
        }
        assertTrue(
            AstraAurora.contrast(AstraAurora.muted, AstraAurora.raised) >= 4.5
        )
    }

    @Test fun readableTypographyIsNotThin() {
        assertTrue(AstraReadableTypography.bodyLarge.fontWeight!!.weight >= FontWeight.Medium.weight)
        assertTrue(AstraReadableTypography.bodySmall.fontWeight!!.weight >= FontWeight.Medium.weight)
        assertTrue(AstraReadableTypography.titleMedium.fontWeight!!.weight >= FontWeight.SemiBold.weight)
        assertTrue(AstraReadableTypography.bodyMedium.fontSize.value >= 15f)
    }
}
