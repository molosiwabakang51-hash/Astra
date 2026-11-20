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

    @Test fun financeMarkdownTableBecomesReadableNarrowScreenRows() {
        val source = "Estimated yields\n" +
            "| Asset | Price | Yield |\n" +
            "| :--- | ---: | ---: |\n" +
            "| UK gilt | £95 | **4.2%** |\n" +
            "| Treasury | $98 | 3.8% |"
        val rendered = AstraMessageMarkup.render(source)
        assertEquals(
            "Estimated yields\n" +
                "• Asset: UK gilt\n   Price: £95\n   Yield: 4.2%\n" +
                "• Asset: Treasury\n   Price: $98\n   Yield: 3.8%",
            rendered.text
        )
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertFalse(rendered.text.contains("---"))
        assertFalse(rendered.text.contains("|"))
    }

    @Test fun malformedAndPartialTablesNeverLoseOriginalCells() {
        for (raw in listOf(
            "| Asset | Yield |\n|---|---|",
            "| Asset | Yield |\n|---|---|\n| Gilt |",
            "| Asset | Yield |\n|---|---|\n| Gilt | 4% | more |",
            "| Asset | Yield |\n|---|---|\n| Name \\| other | 4% |",
            "One | two\nJust a pipe, not a divider\nThree | four"
        )) {
            assertEquals(raw, raw, AstraMessageMarkup.render(raw).text)
        }
    }

    @Test fun markdownLookingTablesInCodeFencesStayExactAndMonospaced() {
        val source = "```md\n" +
            "| Asset | Yield |\n|---|---|\n| UK gilt | 4% |\n```"
        val rendered = AstraMessageMarkup.render(source)
        assertEquals("| Asset | Yield |\n|---|---|\n| UK gilt | 4% |",
            rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test fun excessiveTableRowsFallBackWithoutTruncationOrGuessing() {
        val header = "| Row | Value |\n|---|---|\n"
        val data = (1..25).joinToString("\n") {
            "| " + it + " | " + (it * 2) + " |"
        }
        val original = header + data
        assertEquals(original, AstraMessageMarkup.render(original).text)
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
