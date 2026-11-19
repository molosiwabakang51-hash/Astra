package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraStudySearchTest {
    private val topics = listOf(
        AstraStudyCatalog.Topic("Équations", "Résoudre des problèmes", "math equations"),
        AstraStudyCatalog.Topic("Français", "Grammaire française", "learn french"),
        AstraStudyCatalog.Topic("किताब", "हिन्दी अभ्यास", "hindi reading"),
        AstraStudyCatalog.Topic("Книга", "Урок чтения", "russian reading")
    )

    @Test fun nonAccentedQueriesFindLatinLanguageLessonsWithoutReordering() {
        val searched = AstraStudyTopicFilter.apply(topics, "francais")
        assertEquals(listOf("Français"), searched.map { it.title })
        assertEquals("Équations",
            AstraStudyTopicFilter.apply(topics, "equations").single().title)
        assertEquals("Français",
            AstraStudyTopicFilter.apply(topics, "FRANÇAISE").single().title)
        assertEquals("Équations",
            AstraStudyTopicFilter.apply(topics, "problemes").single().title)
        assertEquals(topics, AstraStudyTopicFilter.apply(topics, ""))
    }

    @Test fun subjectSearchIncludesAccentedDescriptionsAndTopicTitles() {
        val subjects = listOf(
            AstraStudyCatalog.Subject(
                "languages", "Français", "Fr", "Économie et langues",
                listOf(topics[0], topics[1])
            ),
            AstraStudyCatalog.Subject(
                "hindi", "Hindi", "Hi", "देवनागरी और हिन्दी",
                listOf(topics[2])
            )
        )
        assertEquals("languages", AstraStudySubjectFilter.apply(
            subjects, "ECONOMIE").single().id)
        assertEquals("languages", AstraStudySubjectFilter.apply(
            subjects, "equations").single().id)
        assertEquals("hindi", AstraStudySubjectFilter.apply(
            subjects, "किताब").single().id)
        assertEquals(subjects, AstraStudySubjectFilter.apply(subjects, ""))
    }

    @Test fun vowelMarksInNonLatinScriptsAreNotDiscarded() {
        assertEquals("किताब", AstraStudySearch.key("किताब"))
        assertEquals("عَرَبِيّ", AstraStudySearch.key("عَرَبِيّ"))
        assertFalse(AstraStudySearch.matches("किताब", AstraStudySearch.query("कतब")))
        assertFalse(AstraStudySearch.matches("عَرَبِيّ", AstraStudySearch.query("عربي")))
        assertEquals("ёж", AstraStudySearch.key("Ёж"))
        assertFalse(AstraStudySearch.matches("Ёж", AstraStudySearch.query("Еж")))
        assertTrue(AstraStudySearch.matches("École", AstraStudySearch.query("ecole")))
        assertEquals("strasse", AstraStudySearch.key("Straße"))
    }

    @Test fun queryIsBoundedAndOriginalCurriculumRemainsUnchanged() {
        val snapshot = topics.toList()
        assertEquals("equations", AstraStudySearch.query("  ÉQUATIONS  "))
        assertEquals(80, AstraStudySearch.query("z".repeat(800)).length)
        assertEquals(snapshot, topics)
    }
}
