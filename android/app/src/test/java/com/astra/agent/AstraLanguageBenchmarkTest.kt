package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraLanguageBenchmarkTest {
    @Test fun arithmeticProbeRejectsIncorrectOrNonAnswer() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "42"))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "The answer is 42."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "43"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC,
            "The answer is 43, not 42."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "The offline model did not produce a usable answer."))
    }

    @Test fun citedSourcesCannotMakeAnIncorrectModelAnswerPass() {
        val misleading = "I cannot calculate that.\n\nOpen-course references:\n" +
            "[K1] A source that mentions 42."
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, misleading))
        val actualAnswer = "42.\n\nOpen-course references:\n[K1] Context"
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.ARITHMETIC, actualAnswer))
    }

    @Test fun sourceFootersCannotSupplyAnswersWithCrLfOrMixedCase() {
        val misleading = listOf(
            "Cannot calculate.\r\n\r\nOPEN-COURSE REFERENCES:\r\n[K1] 42",
            "Cannot calculate.\n\n  open-course references:  \n[K1] 42",
            "Cannot calculate.\r\nRetrieved Local Book Passages\r\n42",
            "Cannot calculate.\n  RETRIEVED LOCAL BOOK PASSAGES  \n42"
        )
        for (output in misleading) {
            assertFalse("Footer must not supply benchmark answer: $output",
                AstraLanguageBenchmark.score(
                    AstraLanguageBenchmark.Probe.ARITHMETIC, output))
        }
    }

    @Test fun correctAnswersBeforeSourceFootersStillPass() {
        val answers = listOf(
            "42\r\n\r\nOPEN-COURSE REFERENCES:\r\n[K1] other context",
            "42\nRetrieved Local Book Passages\n[K1] unrelated context",
            "42\n  open-course references:  \n[K1] 43"
        )
        for (output in answers) {
            assertTrue(AstraLanguageBenchmark.score(
                AstraLanguageBenchmark.Probe.ARITHMETIC, output))
        }
    }

    @Test fun diagnosticSamplesNeverEchoRetrievedPrivateBookPassages() {
        val answer = "I cannot calculate that.\n\nRetrieved Local Book Passages\n" +
            "[K1] PRIVATE_OWNER_BOOK_EXCERPT 42"
        val result = AstraLanguageBenchmark.outcome(
            AstraLanguageBenchmark.Probe.ARITHMETIC, answer, 50L
        )
        assertFalse(result.passed)
        assertEquals("I cannot calculate that.", result.sample)
        val report = AstraLanguageBenchmark.render(listOf(result), "Qwen3 1.7B")
        assertFalse(report.contains("PRIVATE_OWNER_BOOK_EXCERPT"))
        assertFalse(report.contains("[K1]"))
    }

    @Test fun mixedCaseCrLfFootersAreExcludedFromPassedAndFailedSamples() {
        for (answer in listOf(
            "42\r\n\r\nOPEN-COURSE REFERENCES:\r\n[K1] PRIVATE_DATA",
            "42\n  retrieved local book passages  \n[K1] PRIVATE_DATA"
        )) {
            val result = AstraLanguageBenchmark.outcome(
                AstraLanguageBenchmark.Probe.ARITHMETIC, answer, 1L
            )
            assertTrue(result.passed)
            assertEquals("42", result.sample)
            assertFalse(result.sample.contains("PRIVATE_DATA"))
        }
        val failure = AstraLanguageBenchmark.outcome(
            AstraLanguageBenchmark.Probe.ARITHMETIC,
            "43\r\nOpen-Course References:\r\nPRIVATE_DATA", 1L
        )
        assertFalse(failure.passed)
        assertEquals("43", failure.sample)
    }

    @Test fun RussianTranslationSupportsCommonEnglishExpressions() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Thank you!"))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Thanks."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Hello."))
    }

    @Test fun multiTurnProbeNeedsRealCodewordRetention() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.CONTEXT, "NIMBORA"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.CONTEXT, "I cannot recall that."))
    }

    @Test fun GrammarProbeNeedsTwoDistinctCorrectCases() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.GRAMMAR,
            "Russian has nominative and genitive cases."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.GRAMMAR, "Nominative is one case."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.GRAMMAR, "Russian language has many cases."))
    }

    @Test fun russianGrammarProbeRejectsExplicitlyNegatedCorrectWords() {
        for (wrong in listOf(
            "Russian has no nominative or genitive cases.",
            "The nominative and genitive are not Russian grammatical cases.",
            "Neither the genitive nor the instrumental exists.",
            "It is false that Russian has nominative and accusative cases."
        )) {
            assertFalse("Negated case names cannot count: $wrong",
                AstraLanguageBenchmark.score(
                    AstraLanguageBenchmark.Probe.GRAMMAR, wrong))
        }
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.GRAMMAR,
            "Nominative and genitive."))
    }

    @Test fun reportIsConservativeAndIdentifiesActualModel() {
        val result = AstraLanguageBenchmark.outcome(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Thanks", 125L)
        assertTrue(result.passed)
        assertEquals(125L, result.milliseconds)
        val report = AstraLanguageBenchmark.render(listOf(result), "Qwen3 1.7B · Q4_K_M")
        assertTrue(report.contains("1/1"))
        assertTrue(report.contains("Qwen3 1.7B"))
        assertTrue(report.contains("not proof"))
        assertFalse(report.contains("Full training complete"))
    }

    @Test fun compoundInterestProbeChecksExactThreeYearGrowth() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.COMPOUND_INTEREST, "P1,331.00"))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.COMPOUND_INTEREST, "1331"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.COMPOUND_INTEREST, "P1,300"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.COMPOUND_INTEREST, "P1,330, not P1,331"))
    }

    @Test fun deductionProbeRejectsContradictoryNarrative() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.DEDUCTION, "No."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.DEDUCTION, "Yes"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.DEDUCTION, "Yes, but maybe no."))
    }

    @Test fun setswanaProbeAcceptsActualShortTranslationOnly() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.SETSWANA, "Hello."))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.SETSWANA, "Hi"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.SETSWANA, "Goodbye"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.SETSWANA, "Dumela does not mean hello"))
    }

    @Test fun frenchTranslationMustBeConciseAndCorrect() {
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.FRENCH, "Hello."))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.FRENCH, "Good morning"))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.FRENCH, "Goodbye."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.FRENCH, "Not hello, goodbye."))
    }

    @Test fun germanAndSpanishTestsRejectQuotedIncorrectOrNegatedResponses() {
        for (probe in listOf(
            AstraLanguageBenchmark.Probe.GERMAN,
            AstraLanguageBenchmark.Probe.SPANISH
        )) {
            assertTrue(AstraLanguageBenchmark.score(probe, "Thanks."))
            assertTrue(AstraLanguageBenchmark.score(probe, "Thank you!"))
            assertFalse(AstraLanguageBenchmark.score(probe, "Hello"))
            assertFalse(AstraLanguageBenchmark.score(probe, "It is not thank you"))
            assertFalse(AstraLanguageBenchmark.score(probe,
                "Goodbye (not thanks)"))
        }
    }

    @Test fun negativeOrQuotedAnswersDoNotInflateTranslationOrMemoryScore() {
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.RUSSIAN, "Not thank you."))
        assertFalse(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.CONTEXT, "I forgot NIMBORA"))
        assertTrue(AstraLanguageBenchmark.score(
            AstraLanguageBenchmark.Probe.CONTEXT, "\"NIMBORA\""))
    }

    @Test fun expandedReportHasTenDistinctProbeSlots() {
        assertEquals(10, AstraLanguageBenchmark.Probe.entries.size)
        val outcomes = AstraLanguageBenchmark.Probe.entries.map { probe ->
            AstraLanguageBenchmark.outcome(probe, "", 100L)
        }
        val report = AstraLanguageBenchmark.render(outcomes, null)
        assertTrue(report.contains("0/10"))
        assertTrue(report.contains("Compound interest"))
        assertTrue(report.contains("Logical deduction"))
        assertTrue(report.contains("Setswana greeting"))
        assertTrue(report.contains("French translation"))
        assertTrue(report.contains("German translation"))
        assertTrue(report.contains("Spanish translation"))
        assertEquals(10, AstraLanguageBenchmark.Probe.entries.distinct().size)
    }

    @Test fun repeatedProbeOutcomesCannotInflateOnDeviceScore() {
        val arithmetic = AstraLanguageBenchmark.outcome(
            AstraLanguageBenchmark.Probe.ARITHMETIC, "42", 15L
        )
        val error = runCatching {
            AstraLanguageBenchmark.render(
                listOf(arithmetic, arithmetic), "Qwen3 1.7B"
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("Duplicate"))
    }

    @Test fun uniquePartialAndFullReportsStillRenderCorrectly() {
        val distinct = listOf(
            AstraLanguageBenchmark.outcome(
                AstraLanguageBenchmark.Probe.ARITHMETIC, "42", 10L
            ),
            AstraLanguageBenchmark.outcome(
                AstraLanguageBenchmark.Probe.RUSSIAN, "incorrect", 12L
            )
        )
        val report = AstraLanguageBenchmark.render(distinct, "Qwen3 1.7B")
        assertTrue(report.contains("1/2 checks passed"))
        assertTrue(report.contains("UNVERIFIED"))
        val full = AstraLanguageBenchmark.Probe.entries.map {
            AstraLanguageBenchmark.outcome(it, "", 0L)
        }
        assertTrue(AstraLanguageBenchmark.render(full, null)
            .contains("0/10 checks passed"))
    }

    @Test fun errorOutputsNeverCountAsSuccessfulUnderstanding() {
        for (probe in AstraLanguageBenchmark.Probe.entries) {
            assertFalse(AstraLanguageBenchmark.score(probe, ""))
            assertFalse(AstraLanguageBenchmark.score(probe,
                "Offline model unavailable: import a GGUF file."))
        }
    }
    @Test fun partialReportExplicitlyDisclosesMissingCoverage() {
        val partial = AstraLanguageBenchmark.render(
            listOf(AstraLanguageBenchmark.outcome(
                AstraLanguageBenchmark.Probe.ARITHMETIC, "42", 12L
            )), "Qwen3 1.7B"
        )
        assertTrue(partial.contains("1/1 checks passed"))
        assertTrue(partial.contains("Coverage: 1/10 distinct probes attempted"))
        assertTrue(partial.contains("partial; not a full ten-probe assessment"))
        assertFalse(partial.contains("Coverage: 10/10"))
    }

    @Test fun fullReportIdentifiesCompleteCoverageEvenWhenAnswersFail() {
        val all = AstraLanguageBenchmark.Probe.entries.map {
            AstraLanguageBenchmark.outcome(it, "", 1L)
        }
        val report = AstraLanguageBenchmark.render(all, "Qwen3 1.7B")
        assertTrue(report.contains("0/10 checks passed"))
        assertTrue(report.contains("Coverage: 10/10 distinct probes attempted (complete)."))
        assertFalse(report.contains("partial; not a full ten-probe assessment"))
    }

    @Test fun grammarProbeRejectsUnrelatedHypotheticalAndDuplicateCases() {
        for (wrong in listOf(
            "German has nominative and genitive cases.",
            "Are nominative and genitive Russian cases?",
            "If Russian had nominative and genitive cases.",
            "Nominative and genitive are examples of languages.",
            "Nominative and nominative.",
            "Nominative, genitive, ablative.",
            "I am unsure whether nominative and genitive exist."
        )) {
            assertFalse("Incorrect grammar answer: $wrong",
                AstraLanguageBenchmark.score(AstraLanguageBenchmark.Probe.GRAMMAR, wrong))
        }
    }

    @Test fun grammarProbeAcceptsConciseDistinctCaseNames() {
        for (answer in listOf(
            "Nominative, genitive.",
            "Dative and accusative",
            "Instrumental; prepositional.",
            "Russian has nominative and genitive cases.",
            "The two Russian grammatical cases are dative and genitive.",
            "Nominative case and genitive case.",
            "Nominative\nGenitive"
        )) {
            assertTrue("Correct grammar answer: $answer",
                AstraLanguageBenchmark.score(AstraLanguageBenchmark.Probe.GRAMMAR, answer))
        }
    }

    @Test fun colonTerminatedRetrievedBookFooterCannotLeakOrSupplyAnswer() {
        for (heading in listOf(
            "Retrieved local book passages:",
            "RETRIEVED LOCAL BOOK PASSAGES:  ",
            "  retrieved local book passages: "
        )) {
            val failed = AstraLanguageBenchmark.outcome(
                AstraLanguageBenchmark.Probe.ARITHMETIC,
                "Unknown.\r\n$heading\r\n[K1] PRIVATE_OWNER_PASSAGE 42", 10L
            )
            assertFalse(failed.passed)
            assertEquals("Unknown.", failed.sample)
            val report = AstraLanguageBenchmark.render(listOf(failed), "Qwen3 1.7B")
            assertFalse(report.contains("PRIVATE_OWNER_PASSAGE"))
            assertFalse(report.contains("[K1]"))
            val passed = AstraLanguageBenchmark.outcome(
                AstraLanguageBenchmark.Probe.ARITHMETIC,
                "42\n$heading\n[K1] PRIVATE_OWNER_PASSAGE", 10L
            )
            assertTrue(passed.passed)
            assertEquals("42", passed.sample)
        }
    }


}
