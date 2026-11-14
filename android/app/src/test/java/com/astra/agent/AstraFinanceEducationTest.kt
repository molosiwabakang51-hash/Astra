package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraFinanceEducationTest {
    @Test fun compoundInterestQuestionFromUserScreenshotWorksWithoutModel() {
        val reply = AstraFinanceEducation.respond("explain compound interest")
        assertNotNull(reply)
        assertTrue(reply!!.contains("P1,331"))
        assertTrue(reply.contains("P331"))
        assertTrue(reply.contains("(1 + r/n)^(n×t)"))
    }

    @Test fun commonNaturalLanguageQuestionFormsWork() {
        val queries = listOf(
            "What is compound interest?",
            "Please explain compound interest.",
            "Can you explain compound interest?",
            "Teach me about compound interest",
            "Help me understand compound interest!",
            "What is yield to maturity?",
            "Explain modified duration",
            "Explain bond convexity",
            "Define dividend yield",
            "What is diversification?",
            "Explain the present value",
            "What is an annuity?",
            "Explain inflation",
            "What is the time value of money?",
            "Explain the risk-return trade-off"
        )
        queries.forEach { query ->
            assertNotNull("Should answer: $query", AstraFinanceEducation.respond(query))
        }
    }

    @Test fun doesNotReplacePrivateQuestionsOrCommandsWithGenericContent() {
        assertNull(AstraFinanceEducation.respond("add task study compound interest"))
        assertNull(AstraFinanceEducation.respond("remember compound interest matters"))
        assertNull(AstraFinanceEducation.respond("What is my investment return?"))
        assertNull(AstraFinanceEducation.respond("Explain my bond duration"))
        assertNull(AstraFinanceEducation.respond("Explain this portfolio diversification"))
        assertNull(AstraFinanceEducation.respond("calculate compound interest"))
        assertNull(AstraFinanceEducation.respond("What is the latest stock price?"))
        assertNull(AstraFinanceEducation.respond(""))
        assertNull(AstraFinanceEducation.respond("Hello"))
    }

    @Test fun accurateDurationAndYieldDistinctions() {
        val duration = AstraFinanceEducation.respond("explain bond duration")!!
        assertTrue(duration.contains("1-percentage-point"))
        assertTrue(duration.contains("before convexity"))
        val ytm = AstraFinanceEducation.respond("What is YTM")!!
        assertTrue(ytm.contains("not the coupon rate"))
        assertTrue(ytm.contains("Realized return may differ"))
    }

    @Test fun noClaimsOfGuaranteedReturn() {
        val y = AstraFinanceEducation.respond("What is dividend yield?")!!
        assertTrue(y.contains("Dividends are not guaranteed"))
        val r = AstraFinanceEducation.respond("Explain risk and return")!!
        assertTrue(r.contains("does not guarantee"))
        assertFalse(r.contains("guaranteed higher"))
    }
}
