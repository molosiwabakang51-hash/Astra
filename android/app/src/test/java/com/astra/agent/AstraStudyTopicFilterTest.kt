package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraStudyTopicFilterTest {
    private fun sample() = listOf(
        AstraStudyCatalog.Topic("Bond duration", "Interest-rate sensitivity", "explain bond duration"),
        AstraStudyCatalog.Topic("Portfolio weights", "Weighted return examples", "weighted return 40 10 60 5"),
        AstraStudyCatalog.Topic("Vocabulary quiz", "Review Cyrillic terms", "russian quiz")
    )

    @Test fun emptySearchPreservesOriginalOrder() {
        assertEquals(sample(), AstraStudyTopicFilter.apply(sample(), ""))
        assertEquals(sample(), AstraStudyTopicFilter.apply(sample(), "    "))
    }

    @Test fun searchesTitleDescriptionAndCommandCaseInsensitively() {
        assertEquals("Bond duration", AstraStudyTopicFilter.apply(sample(), "BOND").single().title)
        assertEquals("Portfolio weights", AstraStudyTopicFilter.apply(sample(), "  WEIGHTED RETURN  ").single().title)
        assertEquals("Vocabulary quiz", AstraStudyTopicFilter.apply(sample(), "cyrillic").single().title)
    }

    @Test fun filteringDoesNotReorderOrMutateCurriculum() {
        val original = sample()
        val filtered = AstraStudyTopicFilter.apply(original, "return")
        assertEquals(1, filtered.size)
        assertEquals("Portfolio weights", filtered[0].title)
        assertEquals(3, original.size)
        assertEquals("Bond duration", original.first().title)
    }

    @Test fun missingKeywordReturnsEmptyNotAnAiPrompt() {
        assertTrue(AstraStudyTopicFilter.apply(sample(), "quantum biology").isEmpty())
        assertFalse(AstraStudyTopicFilter.apply(sample(), "math").any { it.requiresModel })
    }

    @Test fun realTwentyOneLessonCatalogRemainsSearchableWithoutInternet() {
        val russian = AstraStudyCatalog.find("russian")!!
        val lesson20 = AstraStudyTopicFilter.apply(russian.topics, "lesson 20")
        assertEquals("russian lesson 20", lesson20.single().command)
        assertTrue(AstraStudyTopicFilter.apply(russian.topics, "lesson").size >= 20)
        assertEquals(russian.topics, AstraStudyTopicFilter.apply(russian.topics, ""))
    }

    @Test fun queriesAreBoundedToEightyCharacters() {
        val content = sample()
        assertEquals(
            AstraStudyTopicFilter.apply(content, "bond"),
            AstraStudyTopicFilter.apply(content, "bond" + " ".repeat(120))
        )
    }
}
