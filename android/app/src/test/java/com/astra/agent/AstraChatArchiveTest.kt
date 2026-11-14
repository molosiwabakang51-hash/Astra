package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraChatArchiveTest {
    @Test fun roundTripPreservesIndependentConversations() {
        val chats = listOf(
            AstraChatArchive.Chat(
                "id-1", "Bond duration",
                listOf(
                    AstraChatArchive.Message("You", "Bond duration"),
                    AstraChatArchive.Message("Astra", "Duration measures rate sensitivity.")
                )
            ),
            AstraChatArchive.Chat(
                "id-2", "Russian",
                listOf(AstraChatArchive.Message("You", "Привет"))
            )
        )
        assertEquals(chats, AstraChatArchive.decode(AstraChatArchive.encode(chats)))
        assertEquals("Bond duration", AstraChatArchive.title(chats[0].messages))
    }

    @Test fun fullLengthAiRepliesAndLargeUserMessagesSurviveArchiving() {
        // The cloud client accepts AI replies up to 16,000 characters.
        // Previously, archiving any message longer than 12,000 crashed.
        val modelReply = "A".repeat(16_000)
        val userText = "B".repeat(40_000) + " end"
        val original = listOf(
            AstraChatArchive.Chat(
                "long-1", "Extended explanation",
                listOf(
                    AstraChatArchive.Message("You", userText),
                    AstraChatArchive.Message("Astra", modelReply)
                )
            )
        )
        assertEquals(original, AstraChatArchive.decode(AstraChatArchive.encode(original)))
    }

    @Test fun arbitraryUtf8MessagesAndMalformedRolesDoNotBreakValidChats() {
        val unicode = "中文 Привет 🌍".repeat(3_000)
        val original = AstraChatArchive.Chat(
            "unicode", "Finance",
            listOf(
                AstraChatArchive.Message("You", unicode),
                AstraChatArchive.Message("SYSTEM", "invalid role"),
                AstraChatArchive.Message("Astra", "Response")
            )
        )
        val decoded = AstraChatArchive.decode(AstraChatArchive.encode(listOf(original)))
        assertEquals(1, decoded.size)
        assertEquals(2, decoded.single().messages.size)
        assertEquals(unicode, decoded.single().messages.first().text)
    }

    @Test fun messageCountRemainsBoundedWithoutLosingNewestMessages() {
        val messages = (0..2_200).map {
            AstraChatArchive.Message("You", "Message $it")
        }
        val chats = listOf(AstraChatArchive.Chat("bounded", "Test", messages))
        val decoded = AstraChatArchive.decode(AstraChatArchive.encode(chats))
        assertEquals(2_000, decoded.single().messages.size)
        assertEquals("Message 201", decoded.single().messages.first().text)
        assertEquals("Message 2200", decoded.single().messages.last().text)
    }

    @Test fun invalidDataIsIgnoredRatherThanCrashing() {
        assertTrue(AstraChatArchive.decode("{bad json").isEmpty())
        assertTrue(AstraChatArchive.decode("[]").isEmpty())
        assertEquals("New conversation", AstraChatArchive.title(emptyList()))
    }

    @Test fun archiveIsBoundedAndIdsAreDeduplicated() {
        val chats = (0 until 100).map {
            AstraChatArchive.Chat(
                "chat-$it", "Conversation $it",
                listOf(AstraChatArchive.Message("You", "Message $it"))
            )
        }
        val decoded = AstraChatArchive.decode(AstraChatArchive.encode(chats))
        assertEquals(40, decoded.size)
        assertEquals("chat-0", decoded.first().id)
        assertEquals("chat-39", decoded.last().id)
        val duplicates = AstraChatArchive.encode(listOf(chats[0], chats[0]))
        assertEquals(1, AstraChatArchive.decode(duplicates).size)
    }

    @Test fun tenThousandArchiveOperationsKeepMessagesIsolated() {
        var chats = emptyList<AstraChatArchive.Chat>()
        repeat(10_000) { index ->
            val chat = AstraChatArchive.Chat(
                "id-$index", "Chat $index",
                listOf(AstraChatArchive.Message("You", "Message $index"))
            )
            chats = AstraChatArchive.decode(
                AstraChatArchive.encode(listOf(chat) + chats)
            )
            assertTrue(chats.size <= 40)
            assertEquals("Message $index", chats.first().messages.first().text)
        }
    }
}
