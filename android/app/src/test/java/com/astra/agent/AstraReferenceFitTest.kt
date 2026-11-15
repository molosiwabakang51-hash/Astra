package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AstraReferenceFitTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun privateBookSourceAndCitationEligibilityShareTheExactLimit() {
        val book = AstraBookLibrary(folder.newFolder("private-book-index"))
        val passage = AstraBookLibrary.Passage(
            "book-id", "Sample finance book", 3, "Duration is interest rate sensitivity.", 9
        )
        val shortQuestion = "What is duration?"
        val oversized = "x".repeat(1_801)
        assertTrue(AstraReferenceFit.permitsBook("x".repeat(1_800)))
        assertFalse(AstraReferenceFit.permitsBook(oversized))
        assertTrue(book.enrich(shortQuestion, listOf(passage)).contains("[B1]"))
        assertEquals(oversized, book.enrich(oversized, listOf(passage)))
    }

    @Test fun openCourseSourcesCannotBeCitedWhenQuestionCrowdsOutExcerpts() {
        val passage = AstraOpenLibrary.Entry(
            "russian-cases", "Russian cases", "https://example.org/course",
            "CC BY-SA 4.0", "Russian has six common grammatical cases.", "cases grammar"
        )
        val ordinary = "What are Russian cases?"
        val oversized = "x".repeat(1_651)
        assertTrue(AstraReferenceFit.permitsOpenCourse("x".repeat(1_650)))
        assertFalse(AstraReferenceFit.permitsOpenCourse(oversized))
        assertTrue(AstraOpenLibrary.enrich(ordinary, listOf(passage)).contains("[K1]"))
        assertEquals(oversized, AstraOpenLibrary.enrich(oversized, listOf(passage)))
    }

    @Test fun bookCitationLimitIsIndependentOfOpenCourseLimit() {
        val medium = "x".repeat(1_720)
        assertTrue(AstraReferenceFit.permitsBook(medium))
        assertFalse(AstraReferenceFit.permitsOpenCourse(medium))
    }
}
