package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraRepeatedStateTraceTest {
    private fun trace(body: String, target: String = "x") =
        "Trace integer assignments: $body. What is $target?"

    @Test fun repeatedSequentialAndSimultaneousGroupsDiffer() {
        val sequential = AstraStateTraceVerifier.verify(trace(
            "x=2; y=3; repeat 3 times: x=x+y then y=x-y; z=x+y", "z"))!!
        val simultaneous = AstraStateTraceVerifier.verify(trace(
            "x=2; y=3; repeat 3 times: simultaneous x=x+y, y=x-y; z=x+y", "z"))!!
        assertEquals("19", sequential.result)
        assertEquals("8", simultaneous.result)
        assertEquals(9, sequential.steps)
        assertEquals(9, simultaneous.steps)
        assertEquals(3, simultaneous.simultaneousGroups)
        assertEquals(1, sequential.repeatBlocks)
    }

    @Test fun generatedStateTransitionsMatchIndependentLongOracle() {
        val random = java.util.Random(618423L)
        repeat(200) {
            val x0 = random.nextInt(21) - 10
            val y0 = random.nextInt(21) - 10
            val n = random.nextInt(10) + 1
            for (simultaneous in listOf(false, true)) {
                var x = x0.toLong()
                var y = y0.toLong()
                repeat(n) {
                    val oldX = x
                    val oldY = y
                    x = oldX + oldY
                    y = (if (simultaneous) oldX else x) - oldY
                }
                val group = if (simultaneous) "simultaneous x=x+y, y=x-y" else "x=x+y then y=x-y"
                val checked = AstraStateTraceVerifier.verify(trace(
                    "x=$x0; y=$y0; repeat $n times: $group; z=x+y", "z"))!!
                assertEquals((x+y).toString(), checked.result)
                assertEquals(3+2*n, checked.steps)
                assertEquals(if (simultaneous) n else 0, checked.simultaneousGroups)
                assertEquals(1, checked.repeatBlocks)
            }
        }
    }

    @Test fun rejectsInvalidExpansionAndRuntimeStates() {
        val invalid = listOf(
            "x=1; repeat 0 times: x=x+1",
            "x=1; repeat 24 times: x=x+1",
            "x=1; repeat 12 times: x=x+1 then x=x+1",
            "x=1; repeat 2 times: repeat 2 times: x=x+1",
            "x=1; repeat 2 times: x=x+1 then then x=x+2",
            "x=1; repeat 2 times: x=x+y",
            "x=1; repeat 2 times: simultaneous x=x+1, x=x+2",
            "x=1; repeat 2 times: simultaneous y=2, x=y",
            "x=999999999; repeat 3 times: x=x*999999999",
            "x=1; repeat 2 times: x=system()"
        )
        for (body in invalid) assertNull(body, AstraStateTraceVerifier.verify(trace(body)))
        val checked = AstraStateTraceVerifier.verify(trace("x=1; repeat 23 times: x=x+1"))!!
        assertEquals("24", checked.result)
        assertEquals(24, checked.steps)
    }

    @Test fun explicitReviewRouteAndOrdinaryQuestionIsolation() {
        val q = trace("x=2; repeat 3 times: x=x*2")
        val explicit = AstraCognitiveReview.resolve("Verify this state trace: $q", emptyList())
            as AstraCognitiveReview.Result.Local
        assertTrue(explicit.answer.contains("result: 16."))
        assertTrue(explicit.answer.contains("1 bounded repetition blocks"))
        val prior = listOf(AstraChatArchive.Message("You", "model only: $q"),
            AstraChatArchive.Message("Astra", "8"))
        val checked = AstraCognitiveReview.resolve("Verify your answer", prior)
            as AstraCognitiveReview.Result.Local
        assertTrue(checked.answer.contains("corrected result: 16."))
        assertEquals(AstraCognitiveReview.Result.NotRequested,
            AstraCognitiveReview.resolve(q, emptyList()))
    }
}
