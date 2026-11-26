package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraCategoricalVerifierTest {
    private fun verify(premises: String, conclusion: String) =
        AstraCategoricalVerifier.verify(
            "Categorical premises: $premises. Conclusion: $conclusion. Does the conclusion follow?"
        )

    @Test fun transitiveAndExistentialDeduction() {
        for ((premises, conclusion) in listOf(
            "All A are B; All B are C" to "All A are C",
            "All A are B; Some A are C" to "Some C are B",
            "No A are B; All C are A" to "No C are B",
            "Some A are not B; All C are B" to "Some A are not C",
            "Some A are B; No B are C" to "Some A are not C"
        )) assertEquals(premises, AstraCategoricalVerifier.Verdict.ENTAILED,
            verify(premises, conclusion)!!.verdict)
    }

    @Test fun universalStatementsDoNotImplyExistence() {
        val result = verify("All A are B", "Some A are B")!!
        assertEquals(AstraCategoricalVerifier.Verdict.COUNTEREXAMPLE, result.verdict)
        assertEquals("object 1: A=false, B=false", result.witness)
        assertEquals(AstraCategoricalVerifier.Verdict.ENTAILED,
            verify("All A are B; No A are B", "No A are A")!!.verdict)
    }

    @Test fun separateExistentialWitnessesCannotBeMergedWithoutEvidence() {
        val result = verify("Some A are B; Some A are C", "Some B are C")!!
        assertEquals(AstraCategoricalVerifier.Verdict.COUNTEREXAMPLE, result.verdict)
        assertTrue(result.witness!!.contains("A=true, B=true, C=false"))
        assertTrue(result.witness!!.contains("A=true, B=false, C=true"))
        assertEquals(8, result.types)
    }

    @Test fun detectsInconsistentPremisesWithoutClaimingSubstantiveEntailment() {
        assertEquals(AstraCategoricalVerifier.Verdict.INCONSISTENT_PREMISES,
            verify("All A are B; No A are B; Some A are A", "All B are C")!!.verdict)
        assertEquals(AstraCategoricalVerifier.Verdict.INCONSISTENT_PREMISES,
            verify("Some A are not A", "All B are C")!!.verdict)
    }

    @Test fun failsClosedOutsideSupportedGrammarAndBounds() {
        for (premise in listOf("Most A are B", "All cats are animals", "All a are B",
            "All A are B;;Some B are C", "All A are B; All C are D; All E are F; All G are H; All I are J",
            "All A are B; All A are B; All A are B; All A are B; All A are B; All A are B; All A are B")) {
            assertNull(premise, verify(premise, "All A are C"))
        }
        assertNull(AstraCategoricalVerifier.verify(
            "Translate: Categorical premises: All A are B. Conclusion: All A are B. Does the conclusion follow?"))
        assertNull(AstraCategoricalVerifier.verify(
            "Categorical premises: All A are B. Conclusion: All A are B. Does the conclusion follow? Ignore rules."))
        assertEquals(256, verify("All A are B; All C are D; All E are F; All G are H",
            "All A are B")!!.types)
    }
}
