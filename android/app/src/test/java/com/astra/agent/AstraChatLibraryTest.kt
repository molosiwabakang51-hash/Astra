package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Locale

class AstraChatLibraryTest {
    private val a = AstraChatArchive.Chat(
        "chat-A", "Bond Duration",
        listOf(
            AstraChatArchive.Message("You", "Review bonds"),
            AstraChatArchive.Message("Astra", "Modified duration")
        )
    )
    private val b = AstraChatArchive.Chat(
        "chat-B", "Russian Practice",
        listOf(
            AstraChatArchive.Message("You", "Спасибо"),
            AstraChatArchive.Message("Astra", "You're welcome")
        )
    )

    @Test fun renameDoesNotMutateOtherConversationsOrTheirMessages() {
        val original = listOf(a, b)
        val renamed = AstraChatLibrary.rename(
            original, "chat-A", "   Portfolio    Review  "
        )
        assertEquals("Portfolio Review", renamed[0].title)
        assertEquals(a.messages, renamed[0].messages)
        assertEquals(b, renamed[1])
        assertEquals("Bond Duration", original[0].title)
        assertEquals(listOf(a, b), original)
        assertEquals(listOf(a, b), AstraChatLibrary.rename(original, "absent", "Unused"))
    }

    @Test fun titleNormalizesWhitespaceAndBoundsInput() {
        val result = AstraChatLibrary.title("\n Important\t finance   project \r")
        assertEquals("Important finance project", result)
        assertEquals(100, AstraChatLibrary.title("z".repeat(10_000)).length)
        try {
            AstraChatLibrary.rename(listOf(a), "chat-A", "  \t ")
            fail("Blank titles must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun caseInsensitiveTitleSearchKeepsOriginalOrdering() {
        val original = listOf(a, b)
        assertEquals(listOf(a), AstraChatLibrary.search(original, "  BOND "))
        assertEquals(listOf(b), AstraChatLibrary.search(original, "practice"))
        assertEquals(original, AstraChatLibrary.search(original, "   "))
        assertTrue(AstraChatLibrary.search(original, "unknown").isEmpty())
    }

    @Test fun deleteOnlyRemovesChosenIdAndPreservesOtherChat() {
        val original = listOf(a, b)
        val remaining = AstraChatLibrary.delete(original, "chat-A")
        assertEquals(listOf(b), remaining)
        assertEquals(listOf(a, b), original)
        assertEquals(original, AstraChatLibrary.delete(original, "nonexistent"))
    }

    @Test fun thousandMixedOperationsSurviveArchiveCodec() {
        var chats = emptyList<AstraChatArchive.Chat>()
        repeat(1_000) { i ->
            val next = AstraChatArchive.Chat(
                "id-$i", "Study $i",
                listOf(AstraChatArchive.Message("You", "Notes $i"))
            )
            chats = listOf(next) + chats
            if (i % 3 == 0) {
                chats = AstraChatLibrary.rename(chats, "id-$i", "Review $i")
            }
            if (i % 4 == 0 && chats.size > 1) {
                chats = AstraChatLibrary.delete(chats, chats.last().id)
            }
            chats = AstraChatArchive.decode(AstraChatArchive.encode(chats))
            assertTrue(chats.size <= 40)
            assertEquals("id-$i", chats.first().id)
            assertEquals("Notes $i", chats.first().messages.single().text)
        }
        assertFalse(chats.isEmpty())
    }
}
