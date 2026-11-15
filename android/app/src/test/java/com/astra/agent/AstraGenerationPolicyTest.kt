package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AstraGenerationPolicyTest {
    @Test fun largerLocalModelCanGiveSubstantiveLongAnswers() {
        assertEquals(768,
            AstraGenerationPolicy.maxAnswerTokens("What is a bond?", true))
        assertEquals(448,
            AstraGenerationPolicy.maxAnswerTokens("What is a bond?", false))
        assertEquals(1152, AstraGenerationPolicy.maxAnswerTokens(
            "Explain duration and convexity step by step and give examples", true))
        assertEquals(672, AstraGenerationPolicy.maxAnswerTokens(
            "Teach me Russian in detail.", false))
        assertEquals(1152, AstraGenerationPolicy.maxAnswerTokens(
            "What do my books say about Russian grammar?", true, hasReferences = true))
        assertEquals(672, AstraGenerationPolicy.maxAnswerTokens(
            "What is described in this imported book?", false, hasReferences = true))
    }

    @Test fun userConcisenessBeatsEvidenceAndDetailHeuristics() {
        assertEquals(256, AstraGenerationPolicy.maxAnswerTokens(
            "Briefly explain this chapter in one sentence", true, hasReferences = true))
        assertEquals(192, AstraGenerationPolicy.maxAnswerTokens(
            "short answer about these references please", false, hasReferences = true))
        assertEquals(192, AstraGenerationPolicy.maxAnswerTokens("", false))
    }

    @Test fun requestsForCourseLessonsAndTutorialsGetDetailedBudget() {
        for (request in listOf(
            "Prepare a Russian study guide", "Write a detailed course",
            "Create a lesson plan for language practice",
            "Explain the derivation of duration and give examples"
        )) {
            assertEquals(AstraGenerationPolicy.Depth.DETAILED,
                AstraGenerationPolicy.depth(request))
        }
    }

    @Test fun fuzzedRequestsHaveStrictOfflineGenerationCap() {
        val rng = Random(1009009)
        val alphabet = "abc?!@#\$中文Привет 12345\n\t"
        repeat(10_000) {
            val candidate = buildString {
                repeat(rng.nextInt(1700)) {
                    append(alphabet[rng.nextInt(alphabet.length)])
                }
            }
            for (advanced in listOf(true, false)) {
                for (evidence in listOf(true, false)) {
                    val budget = AstraGenerationPolicy.maxAnswerTokens(
                        candidate, advancedModel = advanced, hasReferences = evidence)
                    assertTrue(budget in 192..1152)
                }
            }
        }
    }

    @Test fun deterministicOfflineSelfTestRemainsBounded() {
        val test = AstraGenerationPolicy.maxAnswerTokens(
            AstraOfflineSelfTest.CHALLENGE, true)
        assertTrue(test in 192..1152)
    }
}
