package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AstraNaturalLanguageTest {
    @Test fun ordinaryEnglishReadsMapToExistingSafeCommands() {
        assertEquals("show tasks",
            AstraNaturalLanguage.canonical("Could you show my tasks?"))
        assertEquals("show notes",
            AstraNaturalLanguage.canonical("Please list my notes."))
        assertEquals("show completed tasks",
            AstraNaturalLanguage.canonical("Display my completed tasks"))
        assertEquals("open conversations",
            AstraNaturalLanguage.canonical("Open my previous conversations"))
    }

    @Test fun screenNavigationAcceptsPoliteParaphrases() {
        assertEquals("open settings",
            AstraNaturalLanguage.canonical("Please take me to settings"))
        assertEquals("open tasks",
            AstraNaturalLanguage.canonical("Can you switch to the tasks page?"))
        assertEquals("open notes",
            AstraNaturalLanguage.canonical("Go to my notes screen"))
        assertEquals("open tools",
            AstraNaturalLanguage.canonical("Navigate to the tools page"))
    }

    @Test fun everydayNavigationCanOpenChatAndStudyWithoutExactKeywords() {
        assertEquals("open chat",
            AstraNaturalLanguage.canonical("Please take me to the chat screen"))
        assertEquals("open chat",
            AstraNaturalLanguage.canonical("Could you return to my conversation?"))
        assertEquals("open study",
            AstraNaturalLanguage.canonical("I want you to open the Study Hub"))
        assertEquals("open study",
            AstraNaturalLanguage.canonical("Please go to my learning area"))
        assertEquals("open study",
            AstraNaturalLanguage.canonical("Could you start the study page?"))
    }

    @Test fun naturalReadOnlyTasksAndNotesStillPreserveExistingActions() {
        assertEquals("show tasks",
            AstraNaturalLanguage.canonical("Could you show me my pending tasks?"))
        assertEquals("show notes",
            AstraNaturalLanguage.canonical("Please bring up all my notes"))
        assertEquals("show notes",
            AstraNaturalLanguage.canonical("Pull up my notes"))
        assertEquals("How do I open the Study Hub?",
            AstraNaturalLanguage.canonical("How do I open the Study Hub?"))
        assertEquals("Don't open chat",
            AstraNaturalLanguage.canonical("Don't open chat"))
        assertEquals("Teach me finance",
            AstraNaturalLanguage.canonical("Teach me finance"))
    }

    @Test fun explicitTaskAndNoteWritesPreserveUserContent() {
        assertEquals("add task revise bond duration",
            AstraNaturalLanguage.canonical("I want you to add a task to revise bond duration"))
        assertEquals("remember that bonds have interest rate risk",
            AstraNaturalLanguage.canonical("Make a note of bonds have interest rate risk"))
        assertEquals("done 2",
            AstraNaturalLanguage.canonical("Could you mark task 2 as completed?"))
    }

    @Test fun naturalHelpQuestionsRemainReadOnly() {
        assertEquals("help",
            AstraNaturalLanguage.canonical("How can you help me?"))
        assertEquals("help",
            AstraNaturalLanguage.canonical("What can you do?"))
        assertEquals("help",
            AstraNaturalLanguage.canonical("Could you show me what you can do?"))
    }

    @Test fun ambiguousQuestionsAndNegationsRemainUnchanged() {
        val samples = listOf(
            "How do I add a task to review a bond?",
            "Why should I mark task 2 complete?",
            "Do not mark task 2 as done",
            "Don't add task book flights",
            "Explain how to create a task",
            "What happens when I add a task?"
        )
        samples.forEach { input ->
            assertEquals(input, AstraNaturalLanguage.canonical(input))
        }
    }

    @Test fun openEndedConversationsStayWithTheModel() {
        val samples = listOf(
            "Teach me finance in detail",
            "Explain the Macaulay duration of a bond",
            "How does inflation affect bond prices?",
            "Parle-moi des obligations",
            "Discuss the difference between nominal and real yields"
        )
        samples.forEach { input ->
            assertEquals(input, AstraNaturalLanguage.canonical(input))
        }
    }
}
