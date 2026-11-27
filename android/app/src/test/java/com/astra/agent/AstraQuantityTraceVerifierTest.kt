package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraQuantityTraceVerifierTest {
    private fun q(body: String) = "Trace quantity: $body. What is the current amount?"
    private fun check(body: String) = requireNotNull(AstraQuantityTraceVerifier.verify(q(body)))

    @Test fun generatedChronologicalTracesMatchIndependentIntegerOracle() {
        val rng = java.util.Random(416295L)
        repeat(400) {
            val initial = rng.nextInt(1000) - 500
            val a = rng.nextInt(500)
            val b = rng.nextInt(500)
            val replacement = rng.nextInt(1000) - 500
            val c = rng.nextInt(500)
            val noReplacement = check("initial amount is $initial coins; increase it by $a coins; subtract $b coins from it")
            assertEquals((initial + a - b).toString(), noReplacement.result)
            val withReplacement = check("initial amount is $initial coins; add $a coins to it; make it $replacement coins; decrease it by $c coins")
            assertEquals((replacement - c).toString(), withReplacement.result)
            assertEquals(4, withReplacement.steps)
        }
    }

    @Test fun decimalRateAndWithdrawalSemantics() {
        assertEquals("4.75", check("initial amount is 4 percent; raise it by 125 basis points; reduce it by .5 percentage points").result)
        assertEquals("-1.25", check("initial amount is −0.5 coins; decrease it by 0.75 coins").result)
        assertEquals("UNKNOWN", check("initial amount is 50 coins; add 20 coins to it; the current amount is unknown").result)
        assertEquals("7.25", check("initial amount is 50 coins; i do not know the current amount; set it to 7 coins; add .25 coin to it").result)
        val actual = check("initial amount is 4 percent; increase it by 0.25 percentage points")
        assertTrue(AstraQuantityTraceVerifier.report(actual, "4.2500")!!.contains(" — correct."))
        assertTrue(AstraQuantityTraceVerifier.report(actual, "UNKNOWN")!!.contains("incorrect; corrected result: 4.25."))
        assertNull(AstraQuantityTraceVerifier.report(actual, "About 4.25"))
        val wide = check("initial amount is 999999999999 coins; add 999999999999 coins to it")
        assertEquals("1999999999998", wide.result)
        assertTrue(AstraQuantityTraceVerifier.report(wide, wide.result)!!.contains(" — correct."))
        val precise = check("initial amount is 4 percent; increase it by 0.00000001 basis points")
        assertEquals("4.0000000001", precise.result)
        assertTrue(AstraQuantityTraceVerifier.report(precise, precise.result)!!.contains(" — correct."))
    }

    @Test fun explicitProportionalChangesAreExactAndBounded() {
        assertEquals("108", check("initial amount is 100 coins; increase it by 20 percent relative to its current amount; decrease it by 10 percent relative to its current amount").result)
        assertEquals("5.4", check("initial amount is 4 percent; increase it by 10 percent relative to its current amount; raise it by 1 percentage point").result)
        assertEquals("0", check("initial amount is 50 coins; reduce it by 100 percent relative to its current amount").result)
        val rng = java.util.Random(271126L)
        repeat(300) {
            val initial = rng.nextInt(10000)
            val rate = rng.nextInt(101)
            val expected = java.math.BigDecimal(initial.toLong() * (100 - rate)).movePointLeft(2).stripTrailingZeros().toPlainString()
            val actual = check("initial amount is $initial coins; decrease it by $rate percent relative to its current amount")
            assertEquals(expected, actual.result)
            assertTrue(AstraQuantityTraceVerifier.report(actual, expected)!!.contains(" — correct."))
        }
        for (body in listOf(
            "initial amount is -4 coins; increase it by 5 percent relative to its current amount",
            "initial amount is 4 coins; decrease it by 101 percent relative to its current amount",
            "initial amount is 4 coins; the current amount is unknown; increase it by 5 percent relative to its current amount",
            "initial amount is .00000001 coins; increase it by .00000001 percent relative to its current amount"
        )) assertNull(body, AstraQuantityTraceVerifier.verify(q(body)))
    }

    @Test fun invalidOrAmbiguousStepsFailClosed() {
        for (body in listOf(
            "initial amount is 4 percent; increase it by 5 percent",
            "initial amount is 4 coins; increase it by 5 percent",
            "initial amount is 4 coins; add 5 dollars to it",
            "initial amount is 4 coins; set it to 7 dollars",
            "initial amount is 4 coins; decrease it by 5 basis points",
            "initial amount is 4 coins; the current amount is unknown; add 5 coins to it",
            "initial amount is 4 coins; increase it by -5 coins",
            "initial amount is 4 coins; increase it by 1e3 coins",
            "initial amount is 4 coins; increase it by 1.123456789 coins",
            "initial amount is 4 coins; if it increases by 5 coins",
            "initial amount is 4 coins; add 5 coins to it then withdraw",
            "initial amount is 4 coins; \"add 5 coins to it\"",
            "initial amount is 4 coins; add 5\ncoins to it",
            "initial amount is 4 coins; ",
            "initial amount is 4 coins" + "; add 1 coin to it".repeat(24)
        )) assertNull(body, AstraQuantityTraceVerifier.verify(q(body)))
        assertNull(AstraQuantityTraceVerifier.verify("Please explain " + q("initial amount is 4 coins")))
    }

    @Test fun explicitReviewUsesLocalVerifierAndRejectsMalformedTrace() {
        val raw = "Verify this quantity trace: " + q("initial amount is 500 coins; increase it by 50 coins; subtract 20 coins from it")
        val result = AstraCognitiveReview.resolve(raw, emptyList()) as AstraCognitiveReview.Result.Local
        assertTrue(result.answer.contains("INDEPENDENT QUANTITY CHECK — result: 530."))
        for (body in listOf("", "not a trace", q("initial amount is 4 coins; add 3 dollars to it"))) {
            val rejected = AstraCognitiveReview.resolve("Check this quantity trace: $body", emptyList()) as AstraCognitiveReview.Result.Local
            assertFalse(rejected.answer.contains("INDEPENDENT QUANTITY CHECK"))
        }
        assertEquals(AstraCognitiveReview.Result.NotRequested,
            AstraCognitiveReview.resolve("Translate: $raw", emptyList()))
    }
}
