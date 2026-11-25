package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraMemoryRecallTest {
    private fun msg(role: String, text: String) =
        AstraChatArchive.Message(role, text)

    @Test fun recognizesExplicitQueriesWithoutInterceptingOrdinaryChat() {
        assertTrue(AstraMemoryRecall.parse("memory overview") is AstraMemoryRecall.Request.Overview)
        assertTrue(AstraMemoryRecall.parse("show memory overview") is AstraMemoryRecall.Request.Overview)
        for (query in listOf(
            "recall bond duration",
            "search memory for bond duration",
            "what did I say about bond duration?",
            "what do you remember about bond duration",
            "find my notes about bond duration",
            "find in my conversations about bond duration"
        )) {
            val request = AstraMemoryRecall.parse(query)
            assertNotNull("Must recognize $query", request)
            assertEquals(
                "bond duration",
                (request as AstraMemoryRecall.Request.Search).term
            )
        }
        assertNull(AstraMemoryRecall.parse("Explain bond duration"))
        assertNull(AstraMemoryRecall.parse("remember that I bought bonds"))
        assertNull(AstraMemoryRecall.parse("add task buy bonds"))
        assertNull(AstraMemoryRecall.parse("recall"))
        assertNull(AstraMemoryRecall.parse("recall " + "a".repeat(200)))
    }

    @Test fun ranksSavedEvidenceAndNamesItsOriginalSources() {
        val archived = listOf(
            AstraChatArchive.Chat(
                "old-bonds", "Old bond research",
                listOf(
                    msg("You", "We discussed bond duration and convexity."),
                    msg("Astra", "I invented a bond duration projection.")
                )
            )
        )
        val output = AstraMemoryRecall.find(
            "bond duration",
            notes = listOf("Remember that bond duration estimates sensitivity."),
            tasks = listOf("Read about bond duration this week."),
            active = listOf(msg("You", "My bond duration worksheet is complete.")),
            archived = archived
        )
        assertTrue(output.contains("Found 4 matching entries"))
        assertTrue(output.contains("Saved note #1"))
        assertTrue(output.contains("Open task #1"))
        assertTrue(output.contains("Current chat"))
        assertTrue(output.contains("Conversation 'Old bond research'"))
        assertFalse(output.contains("I invented"))
        assertTrue(output.indexOf("Saved note #1") < output.indexOf("Open task #1"))
    }

    @Test fun neverTreatsAssistantTextAsUserMemory() {
        val output = AstraMemoryRecall.find(
            "secret dividend",
            notes = emptyList(),
            tasks = emptyList(),
            active = listOf(
                msg("You", "Hello"),
                msg("Astra", "secret dividend is something I fabricated")
            ),
            archived = listOf(
                AstraChatArchive.Chat("a", "A", listOf(
                    msg("Astra", "secret dividend is in an old reply")
                ))
            )
        )
        assertTrue(output.startsWith("No matching information"))
        assertFalse(output.contains("fabricated"))
    }

    @Test fun normalizedWordSearchHandlesDividendsAndAvoidsUnrelatedMessages() {
        val result = AstraMemoryRecall.find(
            "dividends",
            notes = listOf("A dividend is corporate income.", "Duration measures bond risk."),
            tasks = emptyList(),
            active = listOf(msg("You", "I received two dividends in July.")),
            archived = emptyList()
        )
        assertTrue(result.contains("Saved note #1"))
        assertTrue(result.contains("Current chat"))
        assertFalse(result.contains("Duration measures bond risk"))
    }

    @Test fun notesOnlyRequestNeverReadsTasksOrPastChat() {
        val request = AstraMemoryRecall.parse("Find my notes about risk")
            as AstraMemoryRecall.Request.Search
        assertTrue(request.notesOnly)
        val reply = AstraMemoryRecall.find(
            request.term,
            notes = emptyList(),
            tasks = listOf("Finish risk assignment"),
            active = listOf(msg("You", "Risk exercise")),
            archived = emptyList(),
            notesOnly = request.notesOnly
        )
        assertTrue(reply.startsWith("No saved notes match"))
        assertFalse(reply.contains("Risk exercise"))
    }

    @Test fun selfReferentialSearchRequestsAreNotReturnedAsMemories() {
        val reply = AstraMemoryRecall.find(
            "bonds",
            notes = emptyList(),
            tasks = emptyList(),
            active = listOf(
                msg("You", "recall bonds"),
                msg("You", "I bought bonds last year")
            ),
            archived = emptyList()
        )
        assertTrue(reply.contains("Found 1 matching entries"))
        assertTrue(reply.contains("I bought bonds last year"))
        assertFalse(reply.contains("   recall bonds"))
    }

    @Test fun overviewUsesOnlyCountsNotPersonalContent() {
        val response = AstraMemoryRecall.overview(
            notes = listOf("SECRET-NOTE-DO-NOT-PRINT"),
            tasks = listOf("SECRET-TASK-DO-NOT-PRINT"),
            active = listOf(msg("You", "SECRET-CHAT-DO-NOT-PRINT")),
            archived = listOf(
                AstraChatArchive.Chat("old", "Archived secret", listOf(
                    msg("You", "SECRET-ARCHIVE-DO-NOT-PRINT")
                ))
            )
        )
        assertTrue(response.contains("Saved notes: 1"))
        assertTrue(response.contains("Open tasks: 1"))
        assertTrue(response.contains("Archived user messages: 1"))
        assertFalse(response.contains("SECRET"))
    }

    @Test fun hugeMessageExcerptAndWholeReplyAreBounded() {
        val result = AstraMemoryRecall.find(
            "duration",
            notes = (1..40).map { index ->
                "Note $index includes duration " + "x".repeat(900)
            },
            tasks = emptyList(),
            active = emptyList(),
            archived = emptyList()
        )
        assertTrue(result.length <= 3700)
        assertTrue(result.contains("showing 7"))
        assertFalse(result.contains("x".repeat(400)))
    }

    @Test fun savedAssistantReplyIsNotOwnerMemoryButCanBeReviewedAsANote() {
        val generated = AstraWorkspaceHandoff.noteFromChat(
            "Astra", "You bought copper because I guessed so."
        )!!
        val recall = AstraMemoryRecall.find("copper", listOf(generated),
            emptyList(), emptyList(), emptyList())
        assertTrue(recall.startsWith("No matching information"))
        val notes = AstraMemoryRecall.find("copper", listOf(generated),
            emptyList(), emptyList(), emptyList(), notesOnly = true)
        assertTrue(notes.contains("UNVERIFIED saved Astra reply #1"))
        assertTrue(notes.contains("I guessed so"))
        assertFalse(notes.contains("not AI guesses"))
    }

    @Test fun groundedMemoryQuestionsDoNotBecomeEvidenceInDirectRecall() {
        val entries = listOf(msg("You", "ask my memory why did I buy copper?"),
            msg("You", "analyse my notes about copper"))
        val archived = listOf(AstraChatArchive.Chat("old", "Earlier", entries))
        val result = AstraMemoryRecall.find("copper", emptyList(), emptyList(),
            entries, archived)
        assertTrue(result.startsWith("No matching information"))
    }
}
