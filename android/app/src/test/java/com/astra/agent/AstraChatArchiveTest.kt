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
