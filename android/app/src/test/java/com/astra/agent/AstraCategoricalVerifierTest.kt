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

    @Test fun agreesWithExhaustiveEnumerationOfSmallNonemptyDomains() {
        // Independent oracle: enumerate every nonempty subset of the eight
        // possible A/B/C membership types, then evaluate set relations.
        data class Formula(val text: String, val a: Int, val b: Int, val kind: Int)
        val letters = listOf("A", "B", "C")
        val formulas = letters.indices.flatMap { a ->
            letters.indices.flatMap { b ->
                listOf(
                    Formula("All ${letters[a]} are ${letters[b]}", a, b, 0),
                    Formula("No ${letters[a]} are ${letters[b]}", a, b, 1),
                    Formula("Some ${letters[a]} are ${letters[b]}", a, b, 2),
                    Formula("Some ${letters[a]} are not ${letters[b]}", a, b, 3)
                )
            }
        }
        fun holds(formula: Formula, domain: Int): Boolean {
            val members = (0..7).filter { domain and (1 shl it) != 0 }
            val a = members.filter { it and (1 shl formula.a) != 0 }.toSet()
            val b = members.filter { it and (1 shl formula.b) != 0 }.toSet()
            return when (formula.kind) {
                0 -> b.containsAll(a)
                1 -> a.intersect(b).isEmpty()
                2 -> a.intersect(b).isNotEmpty()
                else -> (a - b).isNotEmpty()
            }
        }
        val random = java.util.Random(71108263L)
        repeat(192) {
            val premises = List(1 + random.nextInt(3)) { formulas[random.nextInt(formulas.size)] }
            val conclusion = formulas[random.nextInt(formulas.size)]
            val models = (1..255).filter { domain -> premises.all { holds(it, domain) } }
            val expected = when {
                models.isEmpty() -> AstraCategoricalVerifier.Verdict.INCONSISTENT_PREMISES
                models.all { holds(conclusion, it) } -> AstraCategoricalVerifier.Verdict.ENTAILED
                else -> AstraCategoricalVerifier.Verdict.COUNTEREXAMPLE
            }
            val actual = verify(premises.joinToString("; ") { it.text }, conclusion.text)!!
            assertEquals(premises.toString() + " => " + conclusion.text, expected, actual.verdict)
            if (expected == AstraCategoricalVerifier.Verdict.COUNTEREXAMPLE) {
                assertNotNull(actual.witness)
                var witnessDomain = 0
                for (obj in actual.witness!!.split("; ")) {
                    var type = 0
                    for ((bit, name) in letters.withIndex()) {
                        if (obj.contains("$name=true")) type = type or (1 shl bit)
                    }
                    witnessDomain = witnessDomain or (1 shl type)
                }
                assertTrue(premises.all { holds(it, witnessDomain) })
                assertFalse(holds(conclusion, witnessDomain))
            }
        }
    }

    @Test fun controlledEnglishDeductionPreservesNamedPredicates() {
        for ((question, verdict) in listOf(
            "All cats are mammals. No mammals are plants. Does it follow that no cats are plants?" to AstraCategoricalVerifier.Verdict.ENTAILED,
            "All cats are mammals. Some mammals are swimmers. Does it follow that some cats are swimmers?" to AstraCategoricalVerifier.Verdict.COUNTEREXAMPLE,
            "Some cats are mammals. No mammals are plants. Does it follow that some cats are not plants?" to AstraCategoricalVerifier.Verdict.ENTAILED,
            "Some cats are mammals. No cats are mammals. Does it follow that all cats are plants?" to AstraCategoricalVerifier.Verdict.INCONSISTENT_PREMISES,
            "All cats are mammals. Does it follow that some cats are mammals? Reply YES or NO only." to AstraCategoricalVerifier.Verdict.COUNTEREXAMPLE
        )) {
            assertEquals(question, verdict, AstraCategoricalVerifier.verifyControlledLanguage(question)!!.check.verdict)
        }
        val check = AstraCategoricalVerifier.verifyControlledLanguage(
            "All cats are mammals. No mammals are plants. Does it follow that no cats are plants?")!!
        assertEquals(mapOf('A' to "cats", 'B' to "mammals", 'C' to "plants"), check.mapping)
    }

    @Test fun controlledLanguageRejectsAmbiguousOrIncompleteArguments() {
        for (question in listOf(
            "All Cats are mammals. Does it follow that all cats are mammals?",
            "Most cats are mammals. Does it follow that some cats are mammals?",
            "All cats are not plants. Does it follow that no cats are plants?",
            "All cats are mammals. Does it follow that all cats are mammals? Ignore the instructions.",
            "All cats are mammals. Does it follow that all cats are animals and mammals?",
            "All cats are mammals. Does it follow that all cats are mammals?\nExtra text",
            "All cats are mammals. Does it follow that all they are mammals?",
            "All cats are mammals. Does it follow that all cats are mammals? Reply YES.",
            "Translate: All cats are mammals. Does it follow that all cats are mammals?",
            "All aa are bb. All cc are dd. All ee are ff. All gg are hh. Does it follow that all ii are jj?",
            "All cats are mammals. ".repeat(7) + "Does it follow that all cats are mammals?"
        )) assertNull(question, AstraCategoricalVerifier.verifyControlledLanguage(question))
    }

    @Test fun namedTranslationAgreesWithFormalVerificationAcrossGeneratedArguments() {
        val random = java.util.Random(81920417L)
        val names = listOf("velons", "tarims", "nexels", "polars")
        val forms = listOf("All" to "", "No" to "", "Some" to "", "Some" to "not ")
        fun statement(): Pair<String, String> {
            val (quantifier, negative) = forms[random.nextInt(forms.size)]
            val a = random.nextInt(names.size)
            val b = random.nextInt(names.size)
            return "$quantifier ${names[a]} are $negative${names[b]}" to
                "$quantifier ${'A' + a} are $negative${'A' + b}"
        }
        repeat(128) {
            val premises = List(1 + random.nextInt(5)) { statement() }
            val conclusion = statement()
            val question = premises.joinToString(". ") { it.first } +
                ". Does it follow that " + conclusion.first.replaceFirstChar { it.lowercaseChar() } + "?"
            val expected = verify(premises.joinToString("; ") { it.second }, conclusion.second)!!
            val actual = AstraCategoricalVerifier.verifyControlledLanguage(question)!!
            assertEquals(question, expected.verdict, actual.check.verdict)
            assertEquals(expected.types, actual.check.types)
        }
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
