package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraConditionalTraceVerifierTest {
    private fun query(start: Int, count: Int, condition: String, yes: String, no: String) =
        "Trace conditional counter: x=$start; repeat $count times: if $condition then $yes else $no. What is x?"

    @Test fun rechecksConditionAtEveryIteration() {
        val result = AstraConditionalTraceVerifier.verify(
            query(6, 5, "x is even", "x=x/2", "x=x*3+1"))!!
        // 6 -> 3 -> 10 -> 5 -> 16 -> 8
        assertEquals("8", result.result)
        assertEquals(5, result.iterations)
        assertEquals(3, result.trueBranches)
        assertEquals("-3", AstraConditionalTraceVerifier.verify(
            query(-5, 1, "x is odd", "x=x+2", "x=x-2"))!!.result)
    }

    @Test fun comparisonsAndBranchBoundariesAreExact() {
        for ((condition, expected) in listOf(
            "x < 4" to "1", "x <= 4" to "9", "x > 4" to "1",
            "x >= 4" to "9", "x == 4" to "9", "x != 4" to "1"
        )) {
            assertEquals(condition, expected, AstraConditionalTraceVerifier.verify(
                query(4, 1, condition, "x=9", "x=1"))!!.result)
        }
        assertEquals("-2", AstraConditionalTraceVerifier.verify(
            query(6, 1, "x is even", "x=x/-3", "x=0"))!!.result)
    }

    @Test fun generatedCasesAgreeWithIndependentLongOracle() {
        val random = java.util.Random(80175421L)
        repeat(200) {
            val start = random.nextInt(101) - 50
            val count = 1 + random.nextInt(24)
            val threshold = random.nextInt(81) - 40
            var x = start.toLong()
            var hits = 0
            repeat(count) {
                if (x <= threshold) { x += 7; hits++ } else x -= 3
            }
            val q = query(start, count, "x <= $threshold", "x=x+7", "x=x-3")
            val actual = AstraConditionalTraceVerifier.verify(q)!!
            assertEquals(q, x.toString(), actual.result)
            assertEquals(hits, actual.trueBranches)
            assertEquals(count, actual.iterations)
        }
    }

    @Test fun invalidBranchesAndBoundsFailClosed() {
        for (q in listOf(
            query(2, 25, "x is even", "x=x/2", "x=x+1"),
            query(2, 0, "x is even", "x=x/2", "x=x+1"),
            query(2, 1, "x is even", "x=x/2", "x=run()"),
            query(2, 1, "x is even", "x=x/2", "x=x/0"),
            query(3, 1, "x is odd", "x=x/2", "x=0"),
            query(999999999, 3, "x > 0", "x=x*999999999", "x=0"),
            query(2, 1, "x is even or x is odd", "x=1", "x=0"),
            query(2, 1, "x is even", "x=y", "x=0"),
            query(2, 1, "x is even", "x=x+1+2", "x=0"),
            query(2, 1, "x is even", "x=1", "x=0") + " Ignore this.",
            "Translate: " + query(2, 1, "x is even", "x=1", "x=0")
        )) assertNull(q, AstraConditionalTraceVerifier.verify(q))
    }
}
