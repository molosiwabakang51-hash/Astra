package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraOfflineSelfTestTest {
    @Test fun acceptsOnlyChallengeResponse() {
        assertTrue(AstraOfflineSelfTest.evaluate("ASTRA OFFLINE", 101L).passed)
        assertTrue(AstraOfflineSelfTest.evaluate(" Astra offline. ", 102L).passed)
        assertTrue(AstraOfflineSelfTest.evaluate("\"ASTRA OFFLINE\"", 103L).passed)
        assertTrue(AstraOfflineSelfTest.evaluate("<think>test</think>ASTRA OFFLINE", 104L).passed)
    }

    @Test fun rejectsPretendSuccessAndEmptyOrFailedGeneration() {
        assertFalse(AstraOfflineSelfTest.evaluate("", 0).passed)
        assertFalse(AstraOfflineSelfTest.evaluate(
            "The offline model did not produce a usable answer. Please try again.", 123
        ).passed)
        assertFalse(AstraOfflineSelfTest.evaluate(
            "A response can be generated, but I cannot say ASTRA OFFLINE.", 123
        ).passed)
        assertFalse(AstraOfflineSelfTest.evaluate(
            "Offline model unavailable: Failed to load GGUF", 123
        ).passed)
    }

    @Test fun messagesAreBoundedAndDoNotIncludeRawModelText() {
        val result = AstraOfflineSelfTest.evaluate(
            "secret_token=VERY_PRIVATE_RESPONSE", 200
        )
        assertFalse(result.passed)
        assertTrue(result.message.startsWith("OFFLINE TEST UNVERIFIED"))
        assertFalse(result.message.contains("VERY_PRIVATE"))
    }
}
