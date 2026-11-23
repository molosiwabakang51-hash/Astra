package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraGroundedCorrectionsTest {
    private fun msg(role: String, text: String) =
        AstraChatArchive.Message(role, text)

    @Test fun correctsHistoricalValueWithoutTreatingAssistantReplyAsEvidence() {
        val messages = listOf(
            msg("You", "My fictional bond maturity is seven years."),
            msg("Astra", "I guessed your bond maturity was 40 years."),
            msg("You", "Actually, make it three years.")
        )
        val annotated = AstraGroundedCorrections.annotated(
            messages, 0, messages[0].text
        )!!
        assertTrue(annotated.contains("seven years"))
        assertTrue(annotated.contains("three years"))
        assertFalse(annotated.contains("40 years"))
        assertTrue(annotated.indexOf("seven years") < annotated.indexOf("three years"))
    }

    @Test fun twoCorrectionsPreserveChronologicalFinalValue() {
        val messages = listOf(
            msg("You", "My bond allocation is 60 percent."),
            msg("Astra", "Okay."),
            msg("You", "Correction: use 55 percent."),
            msg("Astra", "Okay."),
            msg("You", "No, make it 50 percent.")
        )
        val result = AstraGroundedCorrections.annotated(
            messages, 0, messages[0].text
        )!!
        assertTrue(result.contains("60 percent"))
        assertTrue(result.contains("55 percent"))
        assertTrue(result.contains("50 percent"))
        assertTrue(result.indexOf("55 percent") < result.indexOf("50 percent"))
    }

    @Test fun unrelatedOwnerTurnEndsCorrectionChain() {
        val messages = listOf(
            msg("You", "My bond maturity is seven years."),
            msg("Astra", "Understood."),
            msg("You", "I need to change the dinner menu."),
            msg("You", "Actually, make it spicy noodles.")
        )
        assertNull(AstraGroundedCorrections.annotated(
            messages, 0, messages[0].text
        ))
    }

    @Test fun noCrossChatOrAssistantOnlyCorrections() {
        assertNull(AstraGroundedCorrections.annotated(
            listOf(msg("You", "My portfolio has bonds.")), 0, "My portfolio has bonds."
        ))
        assertNull(AstraGroundedCorrections.annotated(
            listOf(
                msg("Astra", "My portfolio has bonds."),
                msg("You", "Actually, make it equity.")
            ), 0, "My portfolio has bonds."
        ))
        assertNull(AstraGroundedCorrections.annotated(
            listOf(
                msg("You", "My portfolio has bonds."),
                msg("Astra", "Actually, I made it equity.")
            ), 0, "My portfolio has bonds."
        ))
    }

    @Test fun quotesStayShortAndSafeForMultilingualCharacters() {
        val original = "b".repeat(87) + "😀" + " secret"
        val corrected = "Actually, make it three years."
        val result = AstraGroundedCorrections.annotated(
            listOf(msg("You", original), msg("You", corrected)),
            0, original
        )!!
        assertTrue(result.length <= 220)
        assertFalse(result.contains(" secret"))
        for (i in result.indices) {
            if (Character.isHighSurrogate(result[i])) {
                assertTrue(i + 1 < result.length &&
                    Character.isLowSurrogate(result[i + 1]))
            }
            if (Character.isLowSurrogate(result[i])) {
                assertTrue(i > 0 && Character.isHighSurrogate(result[i - 1]))
            }
        }
    }
}
