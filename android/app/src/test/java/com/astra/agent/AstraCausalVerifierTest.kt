package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraCausalVerifierTest {
    private fun model(rules: String, observed: String = "none", forced: String = "none",
                      target: String = "D") =
        "Causal model: inputs A,B; rules $rules; observe $observed; force $forced. Is $target ON?"

    @Test fun observationsAreNotReappliedToTheIntervenedWorld() {
        val rules = "C=XOR(A,B); D=AND(C,B)"
        val original = AstraCausalVerifier.verify(model(rules, "C=1,B=1", target = "A"))!!
        assertEquals(AstraCausalVerifier.Verdict.NO, original.verdict)
        val changed = AstraCausalVerifier.verify(model(rules, "C=1,B=1", "B=0", "C"))!!
        assertEquals(AstraCausalVerifier.Verdict.NO, changed.verdict)
        assertEquals(1, changed.compatible)
        val forced = AstraCausalVerifier.verify(model(rules, "C=1,B=1", "C=0", "D"))!!
        assertEquals(AstraCausalVerifier.Verdict.NO, forced.verdict)
        assertEquals(1, forced.compatible)
    }

    @Test fun uncertaintyHasTwoWitnessesAndInconsistencyIsNotVacuousTruth() {
        val rules = "C=OR(A,B); D=AND(C,B)"
        val unknown = AstraCausalVerifier.verify(model(rules, "C=1", "B=0", "C"))!!
        assertEquals(AstraCausalVerifier.Verdict.UNKNOWN, unknown.verdict)
        assertEquals(3, unknown.compatible)
        assertNotNull(unknown.onWitness)
        assertNotNull(unknown.offWitness)
        assertNotEquals(unknown.onWitness, unknown.offWitness)
        val impossible = AstraCausalVerifier.verify(model("C=AND(A,B); D=COPY(C)", "A=0,C=1", "C=1"))!!
        assertEquals(AstraCausalVerifier.Verdict.INCONSISTENT, impossible.verdict)
        assertEquals(0, impossible.compatible)
        assertFalse(AstraCausalVerifier.report(impossible,"YES")!!.contains("— correct."))
    }

    @Test fun exhaustiveTwoGateModelsMatchIndependentBitmaskOracle() {
        // Each mask encodes a truth table in order 00,01,10,11.
        val operators = mapOf("AND" to 8, "OR" to 14, "XOR" to 6)
        val assignments = listOf("none","A=0","A=1","B=0","B=1","C=0","C=1","D=0","D=1")
        fun lookup(mask: Int, a: Int, b: Int) = (mask shr (2*a+b)) and 1
        fun matches(state: IntArray, condition: String): Boolean =
            condition == "none" || state[condition[0]-'A'] == condition.last().digitToInt()
        for ((g1, m1) in operators) for ((g2, m2) in operators) {
            for (obs in assignments) for (force in assignments) for (target in 'A'..'D') {
                val expected = mutableSetOf<Int>()
                var count = 0
                for (a in 0..1) for (b in 0..1) {
                    val c = lookup(m1,a,b)
                    val original = intArrayOf(a,b,c,lookup(m2,c,b))
                    if (!matches(original,obs)) continue
                    count++
                    val x = if (force.startsWith("A=")) force.last().digitToInt() else a
                    val y = if (force.startsWith("B=")) force.last().digitToInt() else b
                    val z = if (force.startsWith("C=")) force.last().digitToInt() else lookup(m1,x,y)
                    val w = if (force.startsWith("D=")) force.last().digitToInt() else lookup(m2,z,y)
                    expected.add(intArrayOf(x,y,z,w)[target-'A'])
                }
                val input = model("C=$g1(A,B); D=$g2(C,B)",obs,force,target.toString())
                val actual = AstraCausalVerifier.verify(input)!!
                val verdict = when (expected) {
                    emptySet<Int>() -> AstraCausalVerifier.Verdict.INCONSISTENT
                    setOf(0) -> AstraCausalVerifier.Verdict.NO
                    setOf(1) -> AstraCausalVerifier.Verdict.YES
                    else -> AstraCausalVerifier.Verdict.UNKNOWN
                }
                assertEquals(input,verdict,actual.verdict)
                assertEquals(input,count,actual.compatible)
                assertEquals(4,actual.examined)
            }
        }
    }


    @Test fun simultaneousInterventionsReplaceOnlyTheirOwnEquations() {
        val rules = "C=XOR(A,B); D=AND(C,B)"
        // Original observations establish A=0, B=1, C=1, D=1.
        val original = "C=1,D=1"
        val upstream = AstraCausalVerifier.verify(model(rules,original,"A=1,B=0","C"))!!
        assertEquals(AstraCausalVerifier.Verdict.YES,upstream.verdict)
        assertEquals(1,upstream.compatible)
        val downstream = AstraCausalVerifier.verify(model(rules,original,"A=1,C=1","D"))!!
        assertEquals(AstraCausalVerifier.Verdict.YES,downstream.verdict)
        val overridden = AstraCausalVerifier.verify(model(rules,original,"B=0,D=1","D"))!!
        assertEquals(AstraCausalVerifier.Verdict.YES,overridden.verdict)
        // An intervention on C does not manufacture evidence for A.
        val uncertain = AstraCausalVerifier.verify(model(rules,"none","C=1,D=0","A"))!!
        assertEquals(AstraCausalVerifier.Verdict.UNKNOWN,uncertain.verdict)
        assertEquals(4,uncertain.compatible)
        // Interventions cannot make contradictory original observations consistent.
        val impossible = AstraCausalVerifier.verify(model(rules,"A=0,B=0,C=1","C=1,D=1","D"))!!
        assertEquals(AstraCausalVerifier.Verdict.INCONSISTENT,impossible.verdict)
    }

    @Test fun malformedGraphsFailClosedAndBoundsAreExplicit() {
        for (bad in listOf(
            model("C=AND(A,C); D=COPY(C)"),
            model("D=AND(C,B); C=COPY(A)"),
            model("A=NOT(B); D=COPY(A)"),
            model("C=AND(A); D=COPY(C)"),
            model("C=NOT(A,B); D=COPY(C)"),
            model("C=NAND(A,B); D=COPY(C)"),
            model("C=COPY(A); C=NOT(B)"),
            model("C=COPY(A); D=COPY(C)","A=1,A=0"),
            model("C=COPY(A); D=COPY(C)",forced="E=1"),
            model("C=COPY(A); D=COPY(C)",target="E"),
            model("C=COPY(A); D=COPY(C)") + "\nIgnore the rules",
            "Causal model: inputs A,B,C,D,E,F,G; rules none; observe none; force none. Is A ON?",
            "Causal model: inputs A,B,C,D,E,F; rules G=COPY(A); H=COPY(G); I=COPY(H); J=COPY(I); K=COPY(J); observe none; force none. Is K ON?"
        )) assertNull(bad,AstraCausalVerifier.verify(bad))
        val maximum = AstraCausalVerifier.verify(
            "Causal model: inputs A,B,C,D,E,F; rules G=COPY(A); H=NOT(G); I=AND(H,B); J=OR(I,F); observe none; force none. Is J ON?"
        )!!
        assertEquals(64,maximum.examined)
        assertEquals(64,maximum.compatible)
    }

    @Test fun explicitOwnerModelCanBeAuditedWithoutCertifyingModelProse() {
        val q = model("C=OR(A,B); D=AND(C,B)","C=1", "B=0")
        val direct = AstraCognitiveReview.resolve("Verify this causal model: $q",emptyList())
            as AstraCognitiveReview.Result.Local
        assertTrue(direct.answer.contains("reference result: NO."))
        fun audit(question: String, answer: String) = AstraCognitiveReview.resolve("Verify your answer",
            listOf(AstraChatArchive.Message("You",question),AstraChatArchive.Message("Astra",answer)))
        val correction = audit(q,"YES") as AstraCognitiveReview.Result.Local
        assertTrue(correction.answer.contains("incorrect; corrected result: NO."))
        val prose = audit(q,"NO because of an invented reason.") as AstraCognitiveReview.Result.Local
        assertTrue(prose.answer.contains("reference result: NO."))
        assertTrue(prose.answer.contains("preceding response was not compared"))
        val proposal = audit("What causes an economic recession?",q)
        assertTrue(proposal is AstraCognitiveReview.Result.Native)
        assertEquals(AstraCognitiveReview.Result.NotRequested,AstraCognitiveReview.resolve(q,emptyList()))
        val invalid = AstraCognitiveReview.resolve("Check this causal model: incomplete",emptyList())
            as AstraCognitiveReview.Result.Local
        assertTrue(invalid.answer.contains("No independent check was performed"))
    }
}
