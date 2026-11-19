package com.astra.agent

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraNaturalLessonFollowUpTest {
    private val ints = mutableMapOf<String, Int>()
    private val strings = mutableMapOf<String, String>()
    private fun tutor() = AstraMultilingualTutor(
        { k, d -> ints[k] ?: d },
        { k, v -> ints[k] = v },
        { k, d -> strings[k] ?: d },
        { k, v -> strings[k] = v }
    )

    @Test fun realFrenchTranscriptAdvancesToLessonThreeWithoutModel() {
        val tutor = tutor()
        assertTrue(tutor.respond("Teach me French").orEmpty().contains("lesson 1/21"))
        assertTrue(tutor.respond("Next language lesson").orEmpty().contains("lesson 2/21"))
        assertTrue(tutor.respond("oui").orEmpty().contains("means 'yes'"))
        val result = tutor.respond("Oui next lesson s'il vous plaît").orEmpty()
        assertTrue(result.contains("French lesson 3/21"))
        assertTrue(tutor.respond("french progress").orEmpty().contains("84 vocabulary cards"))
    }

    @Test fun recognizesPoliteRequestVariantsButNotUnrelatedModelQuestions() {
        val tutor = tutor()
        assertTrue(tutor.respond("next lesson").orEmpty().contains("Select a course"))
        tutor.respond("learn french")
        assertTrue(tutor.respond("Oui, next language lesson please").orEmpty()
            .contains("lesson 2/21"))
        assertTrue(tutor.respond("Continue french lesson s’il vous plaît").orEmpty()
            .contains("lesson 3/21"))
        assertNull(tutor.respond("Discuss the next lesson in quantum physics"))
        assertNull(tutor.respond("Add task buy French bread"))
        assertNull(tutor.respond("What did Shiller write?"))
    }
}
