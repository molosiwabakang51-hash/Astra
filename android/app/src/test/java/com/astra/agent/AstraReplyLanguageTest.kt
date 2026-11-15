package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraReplyLanguageTest {
    @Test fun allElevenAnswerModesAreSelectableWithoutNetwork() {
        assertEquals(11, AstraReplyLanguage.Choice.entries.size)
        for (choice in AstraReplyLanguage.Choice.entries) {
            val selected = AstraReplyLanguage.parseCommand("reply in " + choice.id)
            assertEquals(choice, selected)
            assertEquals(choice, AstraReplyLanguage.fromId(choice.id))
        }
        assertEquals(AstraReplyLanguage.Choice.SETSWANA,
            AstraReplyLanguage.parseCommand("Respond in Tswana"))
        assertEquals(AstraReplyLanguage.Choice.MANDARIN,
            AstraReplyLanguage.parseCommand("answer in Chinese"))
        assertEquals(AstraReplyLanguage.Choice.SWAHILI,
            AstraReplyLanguage.parseCommand("reply in Kiswahili"))
    }

    @Test fun unknownCommandsNeverChangeTheResponseLanguage() {
        assertNull(AstraReplyLanguage.parseCommand("answer in 5 minutes"))
        assertNull(AstraReplyLanguage.parseCommand("add task reply in Setswana"))
        assertNull(AstraReplyLanguage.parseCommand("reply in Elvish"))
        assertEquals(AstraReplyLanguage.Choice.AUTO,
            AstraReplyLanguage.fromId("unknown"))
    }

    @Test fun automaticLanguageDoesNotAlterOrTruncateConversation() {
        val question = "What is modified bond duration?"
        assertEquals(question,
            AstraReplyLanguage.prepare(question, AstraReplyLanguage.Choice.AUTO))
    }

    @Test fun selectedLanguageIsClearAndLatestQuestionAlwaysSurvives() {
        val prompt = "REFERENCE:" + "Citation text ".repeat(260) +
            "\nCURRENT USER MESSAGE:\nExplain bond convexity."
        val text = AstraReplyLanguage.prepare(
            prompt, AstraReplyLanguage.Choice.FRENCH
        )
        assertTrue(text.contains("Answer in French"))
        assertTrue(text.endsWith("Explain bond convexity."))
        assertTrue(text.length <= 3000)
        assertFalse(text.contains("not a training event"))
    }

    @Test fun userExplicitDifferentLanguageOverridesSavedPreference() {
        val text = AstraReplyLanguage.prepare(
            "Answer in English instead.", AstraReplyLanguage.Choice.JAPANESE)
        assertTrue(text.contains("explicitly asks for another"))
        assertTrue(text.endsWith("Answer in English instead."))
    }
}
