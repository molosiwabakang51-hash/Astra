package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraSpeechChunksTest {
    @Test fun shortRepliesAreSingleUtterances() {
        val result = AstraSpeechChunks.partition("Bonjour. Je suis Astra.")
        assertEquals(listOf("Bonjour. Je suis Astra."), result.utterances)
        assertFalse(result.truncated)
        assertEquals("Bonjour. Je suis Astra.".length, result.totalChars)
        assertTrue(AstraSpeechChunks.partition("   \n \t").utterances.isEmpty())
    }

    @Test fun longDetailedCoursePreservesOrderAndNeverExceedsAndroidSafeLimit() {
        val source = (1..180).joinToString(" ") { index ->
            "Leçon $index. Je peux vous aider avec la grammaire française. " +
                "Voici un exemple et une application pratique."
        }
        val segments = AstraSpeechChunks.partition(source, maxChars = 320)
        assertTrue(segments.utterances.size > 20)
        assertTrue(segments.utterances.all { it.length in 1..320 })
        assertFalse(segments.truncated)
        val reconstructed = segments.utterances.joinToString(" ")
            .replace(Regex("""\s+"""), " ").trim()
        assertEquals(source.replace(Regex("""\s+"""), " ").trim(), reconstructed)
        assertTrue(segments.utterances.first().contains("Leçon 1"))
        assertTrue(segments.utterances.last().contains("Leçon 180"))
    }

    @Test fun cjkRepliesWithoutSpacesAreSplitAndRemainIntact() {
        val source = ("日本語の説明と練習をします。").repeat(80)
        val result = AstraSpeechChunks.partition(source, maxChars = 45)
        assertTrue(result.utterances.size > 3)
        assertFalse(result.truncated)
        assertEquals(source, result.utterances.joinToString(""))
    }

    @Test fun neverCutsUnicodeSurrogatePairAcrossBoundary() {
        val source = "a".repeat(16) + "🧠" + "漢字".repeat(25)
        val result = AstraSpeechChunks.partition(source, maxChars = 17)
        assertFalse(result.truncated)
        assertEquals(source, result.utterances.joinToString(""))
        for (piece in result.utterances) {
            assertTrue(piece.length <= 17)
            assertFalse(Character.isHighSurrogate(piece.last()))
            assertFalse(Character.isLowSurrogate(piece.first()))
        }
    }

    @Test fun cappedAudioReportsTruncationWithoutDamagingChatText() {
        val source = "A detailed course explanation. ".repeat(700)
        val result = AstraSpeechChunks.partition(source, maxChars = 180, capChunks = 3)
        assertEquals(3, result.utterances.size)
        assertTrue(result.truncated)
        assertTrue(result.utterances.all { it.length <= 180 })
        // The caller retains the complete original text in the chat;
        // this function is read-only and returns no rewritten transcript.
        assertTrue(source.length > result.totalChars)
    }

    @Test fun malformedCapsAreRejected() {
        for (max in listOf(-1, 0, 15, 3501)) {
            assertTrue(runCatching {
                AstraSpeechChunks.partition("Bonjour", maxChars = max)
            }.isFailure)
        }
        assertTrue(runCatching {
            AstraSpeechChunks.partition("Bonjour", capChunks = 0)
        }.isFailure)
    }
}
