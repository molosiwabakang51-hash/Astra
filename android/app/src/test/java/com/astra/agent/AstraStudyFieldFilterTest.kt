package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraStudyFieldFilterTest {
    private val subjects = AstraStudyCatalog.subjects

    @Test fun exactlyFifteenFieldsPlusCoreCoursesAreSelectable() {
        assertEquals(16, AstraStudyFieldFilter.fields.size)
        assertEquals("Core courses", AstraStudyFieldFilter.fields.first())
        assertEquals(16, AstraStudyFieldFilter.fields.distinct().size)
        assertEquals(13, AstraStudyFieldFilter.apply(subjects, "Core courses").size)
        assertEquals(285, AstraStudyFieldFilter.fields.drop(1).sumOf { field ->
            AstraStudyFieldFilter.apply(subjects, field).size
        })
        assertEquals(subjects, AstraStudyFieldFilter.apply(subjects, null))
    }

    @Test fun categoriesRemainDisjointAndOriginalOrderIsPreserved() {
        val flattened = AstraStudyFieldFilter.fields.drop(1).flatMap { field ->
            AstraStudyFieldFilter.apply(subjects, field)
        }
        assertEquals(285, flattened.size)
        assertEquals(flattened.size, flattened.map { it.id }.toSet().size)
        assertEquals(emptyList<AstraStudyCatalog.Subject>(),
            AstraStudyFieldFilter.apply(subjects, "Nonexistent discipline"))
        assertTrue(AstraStudyFieldFilter.apply(subjects, "Computer Science & AI")
            .any { it.title == "Android Development" })
        assertTrue(AstraStudyFieldFilter.apply(subjects, "Health & Medicine Education")
            .any { it.title == "Public Health" })
        assertTrue(AstraStudyFieldFilter.apply(subjects, "Design & Creative Arts")
            .any { it.title == "Graphic Design" })
    }

    @Test fun fieldSelectionAndSearchCanBeComposedWithoutChangingSourceLibrary() {
        val filtered = AstraStudyFieldFilter.apply(
            AstraStudySubjectFilter.apply(subjects, "physics"),
            "Physical Sciences"
        )
        assertTrue(filtered.any { it.id == "physics" })
        assertTrue(filtered.none { it.id == "finance" })
        assertEquals(13 + AstraSubjectAtlas.entries.size, subjects.size)
    }
}
