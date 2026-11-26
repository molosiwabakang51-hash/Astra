package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraExactArithmeticAuditTest {
    @Test fun repeatingQuotientsUseExactCrossMultiplication() {
        val q = "What is 2 divided by 3?"
        assertNull(AstraVerifiedArithmetic.answer(q))
        for (value in listOf("2/3", "4/6", "-2/-3", "+2/+3")) {
            assertTrue(value, AstraVerifiedArithmetic.audit(q, value)!!.contains("— correct."))
        }
        for (value in listOf("0.666666666667", "1/3", "-2/3")) {
            assertTrue(value, AstraVerifiedArithmetic.audit(q, value)!!
                .contains("corrected result: 2/3."))
        }
        assertTrue(AstraVerifiedArithmetic.audit("What is 0.4 divided by -0.6?", "2/3")!!
            .contains("corrected result: -2/3."))
    }

    @Test fun terminatingArithmeticPreservesExactDecimalCorrections() {
        for ((q, a) in listOf("What is 0.25 plus 0.25?" to "1/2",
            "What is 1 minus 2.5?" to "-3/2",
            "What is 2.5 times 0.2?" to "5/10",
            "What is 3 divided by 4?" to "0.7500")) {
            assertTrue(q, AstraVerifiedArithmetic.audit(q, a)!!.contains("— correct."))
        }
        assertTrue(AstraVerifiedArithmetic.audit("What is 3 divided by 4?", "1/2")!!
            .contains("corrected result: 0.75."))
        assertTrue(AstraVerifiedArithmetic.audit("What is 1 divided by 0?", "0")!!
            .contains("division by zero is undefined"))
    }

    @Test fun generatedEquivalentRatiosAndWrongValues() {
        val random = java.util.Random(884129L)
        repeat(200) {
            val a = random.nextInt(2001) - 1000
            val b = 1 + random.nextInt(1000)
            val k = 1 + random.nextInt(20)
            val q = "What is $a divided by $b?"
            assertTrue(AstraVerifiedArithmetic.audit(q, "${a*k}/${b*k}")!!.contains("— correct."))
            assertTrue(AstraVerifiedArithmetic.audit(q, "${a*k+1}/${b*k}")!!.contains("incorrect;"))
        }
    }

    @Test fun rejectsUnsupportedAnswersAndPreservesExplicitReviewBoundary() {
        val q = "What is 2 divided by 3?"
        for (a in listOf("2/0", "2 / 3", "2/3 or 1", "2e-1000000000", "2/3 because",
            "1".repeat(100), "++2/3", "2/3\n1")) {
            assertNull(a, AstraVerifiedArithmetic.audit(q, a))
        }
        assertNull(AstraVerifiedArithmetic.audit("Translate: What is 2 divided by 3?", "2/3"))
        val history = listOf(AstraChatArchive.Message("You", "model only: $q"),
            AstraChatArchive.Message("Astra", "1/3"))
        val result = AstraCognitiveReview.resolve("Verify your answer", history) as AstraCognitiveReview.Result.Local
        assertTrue(result.answer.contains("corrected result: 2/3."))
        assertEquals(AstraCognitiveReview.Result.NotRequested,
            AstraCognitiveReview.resolve(q, emptyList()))
    }
}
