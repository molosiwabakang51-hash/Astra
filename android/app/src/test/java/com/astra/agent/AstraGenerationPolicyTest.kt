package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AstraGenerationPolicyTest {
    @Test fun allowsLongerAndMoreUsefulResponsesFromAdvancedOfflineModel() {
        assertEquals(768, AstraGenerationPolicy.maxAnswerTokens("What is a bond?", true))
        assertEquals(448, AstraGenerationPolicy.maxAnswerTokens("What is a bond?", false))
        assertEquals(1152, AstraGenerationPolicy.maxAnswerTokens(
            "Explain modified duration step by step and compare it with convexity.", true
        ))
        assertEquals(672, AstraGenerationPolicy.maxAnswerTokens(
            "Teach me Russian in detail.", false
        ))
    }

    @Test fun preservesUserRequestedConciseness() {
        assertEquals(256, AstraGenerationPolicy.maxAnswerTokens(
            "Briefly compare equities and bonds", true
        ))
        assertEquals(192, AstraGenerationPolicy.maxAnswerTokens(
            "Answer in one sentence.", false
        ))
        assertEquals(192, AstraGenerationPolicy.maxAnswerTokens("", false))
    }

    @Test fun limitsAreAlwaysBoundedAcrossTenThousandUntrustedQuestions() {
        val rng = Random(17171)
        val alphabet = "abc?!@#\$中文Привет 12345\n\t🚀"
        repeat(10_000) {
            val input = buildString {
                repeat(rng.nextInt(2500)) { append(alphabet[rng.nextInt(alphabet.length)]) }
            }
            for (advanced in listOf(true, false)) {
                val budget = AstraGenerationPolicy.maxAnswerTokens(input, advanced)
                assertTrue(budget in 128..1152)
            }
        }
    }

    @Test fun fixedSelfTestCanStayShortAndNeverBecomesUnlimitedGeneration() {
        val result = AstraGenerationPolicy.maxAnswerTokens(
            AstraOfflineSelfTest.CHALLENGE, advancedModel = true
        )
        assertTrue(result in 128..1152)
    }
}
