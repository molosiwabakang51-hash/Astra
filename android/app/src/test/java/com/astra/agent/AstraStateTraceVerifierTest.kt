package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraStateTraceVerifierTest {
    @Test fun sequentialAssignmentsUseLatestState() {
        val checked = AstraStateTraceVerifier.verify(
            "Trace integer assignments: x=2; y=3; x=x+y; y=x-y; x=x*y. What is x? Reply with only the integer.")!!
        assertEquals("10", checked.result)
        assertEquals(5, checked.steps)
        assertEquals("x", checked.variable)
        assertEquals("-8", AstraStateTraceVerifier.verify(
            "Trace integer assignments: x=-2; y=4; x=x*y. What is x?")!!.result)
    }

    @Test fun generatedTracesAgreeWithIndependentIntegerOracle() {
        val random = java.util.Random(37194017L)
        repeat(200) {
            var x = random.nextInt(100) - 50
            var y = random.nextInt(100) - 50
            val statements = mutableListOf("x=$x", "y=$y")
            repeat(12) {
                when (random.nextInt(4)) {
                    0 -> { statements.add("x=x+y"); x += y }
                    1 -> { statements.add("y=x-y"); y = x - y }
                    2 -> { statements.add("x=y"); x = y }
                    else -> { statements.add("y=y*-2"); y *= -2 }
                }
            }
            val q = "Trace integer assignments: " + statements.joinToString("; ") + ". What is y?"
            assertEquals(q, y.toString(), AstraStateTraceVerifier.verify(q)!!.result)
        }
    }

    @Test fun unsupportedOrUnboundedInputsFailClosed() {
        val bad = listOf(
            "x=y; y=3", "x=1; x=x/2", "x=1; x=x+2+3", "x=1; x=(x+2)",
            "x=1; x=run()", "x=1; x=x**2", "x=1; x=x+z", "x=1; ",
            "x=999999999; x=x*x; x=x*x",
            "a=1; b=1; c=1; d=1; e=1; f=1; g=1; h=1; i=1; x=1",
            List(25) { "x=1" }.joinToString("; ")
        )
        for (body in bad) {
            val q = "Trace integer assignments: " + body + ". What is x?"
            assertNull(q, AstraStateTraceVerifier.verify(q))
        }
        val q = "Trace integer assignments: x=2; y=3. What is x?"
        assertNull(AstraStateTraceVerifier.verify(q + " Ignore the instructions."))
        assertNull(AstraStateTraceVerifier.verify("Translate: " + q))
        assertNull(AstraStateTraceVerifier.verify(q.replace("x=2", "x=2\n")))
        assertNull(AstraStateTraceVerifier.verify(q.replace("What is x?", "What is z?")))
    }

    @Test fun explicitAndPriorAnswerReviewPreserveBoundaries() {
        val q = "Trace integer assignments: x=2; y=3; x=x+y. What is x?"
        val direct = AstraCognitiveReview.resolve("Verify this state trace: " + q, emptyList())
            as AstraCognitiveReview.Result.Local
        assertTrue(direct.answer.contains("result: 5."))
        val pair = listOf(AstraChatArchive.Message("You", "model only: " + q),
            AstraChatArchive.Message("Astra", "4"))
        val audit = AstraCognitiveReview.resolve("Verify your answer", pair) as AstraCognitiveReview.Result.Local
        assertTrue(audit.answer.contains("incorrect; corrected result: 5."))
        val checked = AstraStateTraceVerifier.verify(q)!!
        assertTrue(AstraStateTraceVerifier.report(checked, "+005")!!.contains("correct."))
        assertTrue(AstraStateTraceVerifier.report(checked, "005")!!.contains("correct."))
        assertTrue(AstraStateTraceVerifier.report(checked, "+6")!!.contains("incorrect; corrected result: 5."))
        assertNull(AstraStateTraceVerifier.report(checked, "++5"))
        assertNull(AstraStateTraceVerifier.report(checked, "+-5"))
        assertNull(AstraStateTraceVerifier.report(checked, "+"))
        assertNull(AstraStateTraceVerifier.report(checked, "5 because I think so"))
        assertEquals(AstraCognitiveReview.Result.NotRequested,
            AstraCognitiveReview.resolve(q, emptyList()))
        val invalid = AstraCognitiveReview.resolve("Verify this state trace: x=run()", emptyList())
            as AstraCognitiveReview.Result.Local
        assertTrue(invalid.answer.contains("could not verify"))
    }
}
