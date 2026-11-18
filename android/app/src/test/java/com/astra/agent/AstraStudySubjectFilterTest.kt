package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraStudySubjectFilterTest {
    private val subjects = AstraStudyCatalog.subjects

    @Test fun allSubjectsVisibleWhenQueryIsBlank() {
        assertEquals(subjects, AstraStudySubjectFilter.apply(subjects, ""))
        assertEquals(subjects, AstraStudySubjectFilter.apply(subjects, "   "))
    }

    @Test fun searchFindsSubjectNamesAndDescriptionsWithoutReordering() {
        assertEquals(listOf("russian"),
            AstraStudySubjectFilter.apply(subjects, "RUSSIAN").map { it.id })
        assertEquals(listOf("mathematics"),
            AstraStudySubjectFilter.apply(subjects, " algebra ").map { it.id })
        assertEquals(listOf("finance"),
            AstraStudySubjectFilter.apply(subjects, "investing").map { it.id })
    }

    @Test fun searchAlsoDiscoversLessonTopicsWithinASubject() {
        assertEquals(listOf("finance"),
            AstraStudySubjectFilter.apply(subjects, "bond duration").map { it.id })
        assertEquals(listOf("russian"),
            AstraStudySubjectFilter.apply(subjects, "Cyrillic").map { it.id })
    }

    @Test fun unmatchedQueryHasEmptyResultAndSearchIsPure() {
        val snapshot = subjects.toList()
        assertTrue(AstraStudySubjectFilter.apply(subjects, "zzzz-no-topic").isEmpty())
        assertEquals(snapshot, subjects)
    }

    @Test fun accessLabelsAreTruthfulAboutLocalModelRequirements() {
        assertEquals("OFFLINE", AstraStudyAccess.label(
            AstraStudyCatalog.find("english")!!
        ))
        assertEquals("OFFLINE", AstraStudyAccess.label(
            AstraStudyCatalog.find("day_trading")!!
        ))
        assertEquals("OFFLINE", AstraStudyAccess.label(
            AstraStudyCatalog.find("finance")!!
        ))
        assertEquals("OFFLINE", AstraStudyAccess.label(
            AstraStudyCatalog.find("mathematics")!!
        ))
        assertFalse(AstraStudyCatalog.find("russian")!!.topics.any { it.requiresModel })
    }
}
