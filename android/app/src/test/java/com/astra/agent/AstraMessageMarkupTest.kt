package com.astra.agent

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraMessageMarkupTest {
    @Test fun financeLessonIsReadableWithoutMarkdownSymbols() {
        val source = "# Finance\n**Duration** estimates interest-rate risk.\n" +
            "- Bonds have cash flows\n1. Calculate the price\n2. Compare yields"
        val formatted = AstraMessageMarkup.render(source)
        assertEquals(
            "Finance\nDuration estimates interest-rate risk.\n" +
                "• Bonds have cash flows\n1. Calculate the price\n2. Compare yields",
            formatted.text
        )
        assertTrue(formatted.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(formatted.spanStyles.any { it.item.fontSize.value > 1f })
    }

    @Test fun inlineCodeAndFencedCodeAreMonospacedAndNotExecuted() {
        val source = "Run `print(2 * 3)` locally.\n```kotlin\nval answer = 2 * 3\n```"
        val formatted = AstraMessageMarkup.render(source)
        assertEquals("Run print(2 * 3) locally.\nval answer = 2 * 3", formatted.text)
        assertTrue(formatted.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertFalse(formatted.text.contains("```"))
    }

    @Test fun unmatchedStreamingMarkersAndOrdinaryMathRemainVisible() {
        val source = "Price = 2 * 3.\n**unfinished\nBond formula: P = 1 / (1 + r)"
        val formatted = AstraMessageMarkup.render(source)
        assertEquals(source, formatted.text)
    }

    @Test fun italicEmphasisAndNonEnglishScriptsRemainLegible() {
        val formatted = AstraMessageMarkup.render(
            "## Срок облигации\n*Риск* и возврат. Français: **durée**."
        )
        assertEquals("Срок облигации\nРиск и возврат. Français: durée.", formatted.text)
        assertTrue(formatted.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(formatted.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test fun hostileHtmlAndLinksRemainLiteralNotInteractiveOrLoaded() {
        val raw = """Do not run <script>leak()</script>. Use [example](https://test.invalid)."""
        assertEquals(raw, AstraMessageMarkup.render(raw).text)
    }

    @Test fun mixedCrLfAndPlainResponseWorkWithoutInventedFormatting() {
        assertEquals("Hello.\nHow are you?",
            AstraMessageMarkup.render("Hello.\r\nHow are you?").text)
        assertEquals("", AstraMessageMarkup.render("").text)
        assertEquals("Astra is offline.", AstraMessageMarkup.render("Astra is offline.").text)
    }
}
