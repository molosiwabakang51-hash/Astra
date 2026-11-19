package com.astra.agent

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraCourseNaturalLanguageTest {
    private val state = mutableMapOf<String, Int>()
    private val read: (String, Int) -> Int = { k, default -> state[k] ?: default }
    private val write: (String, Int) -> Unit = { k, v -> state[k] = v }

    @Test fun reportedFinanceCommandStartsRealOfflineSyllabus() {
        val course = AstraFinanceCourse(read, write)
        val result = course.respond("teach me finance")
        assertTrue(result.orEmpty().contains("20 OFFLINE LESSONS"))
        assertTrue(result.orEmpty().contains("finance lesson 1"))
        assertNull(course.respond("teach me finance and calculate my exact return"))
    }

    @Test fun mathAndEnglishStartTheirBundledLessonsNaturally() {
        val math = AstraMathCourse(read, write)
        val english = AstraEnglishCourse(read, write)
        assertTrue(math.respond("teach me math").orEmpty().contains("16 OFFLINE LESSONS"))
        assertTrue(math.respond("teach me mathematics").orEmpty().contains("16 OFFLINE LESSONS"))
        assertTrue(english.respond("Teach me English").orEmpty().contains("16 OFFLINE LESSONS"))
        assertNull(english.respond("teach me a long paragraph on business analysis"))
    }
}
