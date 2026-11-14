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

    @Test fun findsSavedMessageTextEvenWhenTitleDoesNotMatch() {
        val hits = AstraChatLibrary.searchWithPreview(listOf(a, b), "MODIFIED")
        assertEquals(1, hits.size)
        assertEquals(a, hits.single().chat)
        assertTrue(hits.single().snippet!!.contains("Modified duration"))
        assertTrue(hits.single().snippet!!.startsWith("Astra: "))
        assertEquals(listOf(b), AstraChatLibrary.search(listOf(a, b), "спасибо"))
    }

    @Test fun titleMatchesHaveNoMessageSnippetAndKeepArchiveOrder() {
        val hits = AstraChatLibrary.searchWithPreview(listOf(a, b), "practice")
        assertEquals(1, hits.size)
        assertEquals(b, hits.single().chat)
        assertEquals(null, hits.single().snippet)
        assertEquals(listOf(a, b), AstraChatLibrary.searchWithPreview(listOf(a, b), "").map { it.chat })
    }

    @Test fun newestMatchIsShownWithoutReturningEntireChatHistory() {
        val message = "z".repeat(20_000) + " quantitative finance " + "y".repeat(20_000)
        val original = AstraChatArchive.Chat(
            "chat-long", "Portfolio",
            listOf(
                AstraChatArchive.Message("You", "quantitative finance in a draft"),
                AstraChatArchive.Message("Astra", message)
            )
        )
        val hit = AstraChatLibrary.searchWithPreview(listOf(original), "QUANTITATIVE").single()
        val snippet = hit.snippet!!
        assertTrue(snippet.startsWith("Astra: …"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.contains("quantitative finance"))
        assertTrue(snippet.length < 160)
        assertEquals(original, hit.chat)
    }

    @Test fun searchCanMatchUnicodeAndNeverMutatesArchivedData() {
        val snapshot = listOf(a, b)
        assertEquals(listOf(b), AstraChatLibrary.search(snapshot, "Спасибо"))
        assertTrue(AstraChatLibrary.search(snapshot, "unmentioned topic").isEmpty())
        assertEquals(listOf(a, b), snapshot)
    }

    @Test fun searchesFortyConversationsWithTwoThousandMessagesEach() {
        val chats = (0 until AstraChatArchive.MAX_CHATS).map { index ->
            AstraChatArchive.Chat(
                "id-$index", "Conversation $index",
                (0 until 2_000).map { msg ->
                    AstraChatArchive.Message(
                        if (msg % 2 == 0) "You" else "Astra",
                        if (index == 29 && msg == 1_985) {
                            "A rare topic about Pula bonds and duration."
                        } else "Other question #$msg"
                    )
                }
            )
        }
        val results = AstraChatLibrary.searchWithPreview(chats, "pula bonds")
        assertEquals(1, results.size)
        assertEquals("id-29", results.single().chat.id)
        assertTrue(results.single().snippet!!.contains("Pula bonds"))
    }

    @Test fun deleteOnlyRemovesChosenIdAndPreservesOtherChat() {
        val original = listOf(a, b)
        val remaining = AstraChatLibrary.delete(original, "chat-A")
        assertEquals(listOf(b), remaining)
        assertEquals(listOf(a, b), original)
        assertEquals(original, AstraChatLibrary.delete(original, "nonexistent"))
    }

    @Test fun fullArchiveDoesNotSilentlyEvictOnNewConversation() {
        val full = (0 until AstraChatArchive.MAX_CHATS).map { index ->
            AstraChatArchive.Chat(
                "id-$index", "Saved $index",
                listOf(AstraChatArchive.Message("You", "Content $index"))
            )
        }
        val current = AstraChatArchive.Chat(
            "active", "Current",
            listOf(AstraChatArchive.Message("You", "Private current message"))
        )
        assertEquals(null, AstraChatLibrary.archive(full, current))
        assertEquals(full, AstraChatLibrary.archive(full, null))
        assertEquals(40, full.size)
    }

    @Test fun switchingAcrossFullArchivePreservesEveryConversation() {
        val original = (0 until AstraChatArchive.MAX_CHATS).map { index ->
            AstraChatArchive.Chat(
                "id-$index", "Saved $index",
                listOf(AstraChatArchive.Message("You", "Content $index"))
            )
        }
        var archived = original
        var active = AstraChatArchive.Chat(
            "active", "Current",
            listOf(AstraChatArchive.Message("You", "Private current message"))
        )
        val allMessages = (original + active)
            .map { it.messages.single().text }.toSet()
        repeat(5_000) { iteration ->
            val target = archived[(iteration * 17) % archived.size]
            val result = AstraChatLibrary.activate(archived, target.id, active)
                ?: error("Archive lost target")
            archived = result.archived
            active = result.active
            assertEquals(40, archived.size)
            assertEquals(41, (archived + active).distinctBy { it.id }.size)
            assertEquals(
                allMessages,
                (archived + active).map { it.messages.single().text }.toSet()
            )
        }
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
