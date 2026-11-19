package com.astra.agent

import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class AstraCourseEntryTest {
    private val progress = mutableMapOf<String, Int>()
    private val read = { key: String, default: Int -> progress[key] ?: default }
    private val write = { key: String, value: Int -> progress[key] = value }

    @Test fun usersNaturalStudyRequestsOpenRealOfflineCurricula() {
        val finance = AstraFinanceCourse(read, write)
        val math = AstraMathCourse(read, write)
        val english = AstraEnglishCourse(read, write)
        val trading = AstraTradingCourse(read, write)
        assertTrue(finance.respond("teach me finance")!!
            .contains("FINANCE FOUNDATION · 20 OFFLINE LESSONS"))
        assertTrue(finance.respond("study finance")!!.contains("finance workshop"))
        assertTrue(math.respond("teach me mathematics")!!
            .contains("MATHEMATICS FOUNDATION · 16 OFFLINE LESSONS"))
        assertTrue(math.respond("teach me math")!!.contains("math workshop"))
        assertTrue(english.respond("teach me english")!!
            .contains("ENGLISH FOUNDATION · 16 OFFLINE LESSONS"))
        assertTrue(english.respond("english lessons")!!.contains("english workshop"))
        assertTrue(trading.respond("teach me forex")!!
            .contains("DAY TRADING FOUNDATION · 16 OFFLINE LESSONS"))
        assertTrue(trading.respond("teach me index derivatives")!!
            .contains("trading workshop"))
    }

    @Test fun unrelatedQuestionsStillReachTheGeneralAssistant() {
        val finance = AstraFinanceCourse(read, write)
        val math = AstraMathCourse(read, write)
        val trading = AstraTradingCourse(read, write)
        assertNull(finance.respond("What does finance mean in this paragraph?"))
        assertNull(math.respond("Please solve this homework with my actual notes"))
        assertNull(trading.respond("Should I trade EUR/USD right now?"))
    }
}
