package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraExactEquationAuditTest {
    @Test fun exactFractionsAndEquivalentScalars() {
        val q = "Solve for x: 3x=2"
        assertNull(AstraCalculationAssist.solveLinearEquation(q))
        for (a in listOf("2/3", "4/6", "-2/-3", "+2/+3")) {
            assertTrue(a, AstraCalculationAssist.auditLinearEquation(q, a)!!.contains("— correct."))
        }
        for (a in listOf("0.666666666667", "1/3", "No solution.")) {
            assertTrue(a, AstraCalculationAssist.auditLinearEquation(q, a)!!
                .contains("corrected exact result: 2/3."))
        }
        assertTrue(AstraCalculationAssist.auditLinearEquation("Solve for x: 2x=1", "0.50")!!
            .contains("— correct."))
    }

    @Test fun unsupportedAndAmbiguousAnswersStayUnverified() {
        for (a in listOf("2/0", "2 / 3", "2/3 or 1", "2e0", "2/3 because", "++2/3")) {
            assertNull(a, AstraCalculationAssist.auditLinearEquation("Solve for x: 3x=2", a))
        }
        assertNull(AstraCalculationAssist.auditLinearEquation("Solve for x: x*x=4", "2"))
        assertNull(AstraCalculationAssist.auditLinearEquation("Explain: Solve for x: 3x=2", "2/3"))
        assertNull(AstraCalculationAssist.auditLinearEquation("Solve for x: x=x", "2"))
    }

    @Test fun solutionSetsAreNotConfusedWithUniqueSolutions() {
        assertTrue(AstraCalculationAssist.auditLinearEquation("Solve for x: x=x", "Infinitely many solutions.")!!
            .contains("— correct."))
        assertTrue(AstraCalculationAssist.auditLinearEquation("Solve for x: x=x+1", "2")!!
            .contains("incorrect; No solution."))
        assertTrue(AstraCalculationAssist.auditLinearEquation("Solve for x: x=x+1", "No solution.")!!
            .contains("— correct."))
    }

    @Test fun explicitReviewUsesExactEquationAudit() {
        val history = listOf(AstraChatArchive.Message("You", "model only: Solve for x: 3x=2"),
            AstraChatArchive.Message("Astra", "1/3"))
        val result = AstraCognitiveReview.resolve("Verify your answer", history) as AstraCognitiveReview.Result.Local
        assertTrue(result.answer.contains("corrected exact result: 2/3."))
        assertEquals(AstraCognitiveReview.Result.NotRequested,
            AstraCognitiveReview.resolve("Solve for x: 3x=2", emptyList()))
    }
}
