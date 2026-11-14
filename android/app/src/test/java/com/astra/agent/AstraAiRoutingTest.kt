package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraAiRoutingTest {
    private val unsupported =
        "I cannot answer that reliably with Astra's current offline tools. Try help."

    @Test fun unsupportedQuestionsUseCloudOnlyWhenOptedInAndConfigured() {
        assertTrue(AstraAiRouting.shouldUseCloud(true, true, unsupported, false, false))
        assertFalse(AstraAiRouting.shouldUseCloud(false, true, unsupported, false, false))
        assertFalse(AstraAiRouting.shouldUseCloud(true, false, unsupported, false, false))
    }

    @Test fun neverUploadLocalToolResultsSensitiveOutputsOrActions() {
        assertFalse(AstraAiRouting.shouldUseCloud(true, true, "Task added: study", false, false))
        assertFalse(AstraAiRouting.shouldUseCloud(true, true, "Note saved.", false, false))
        assertFalse(AstraAiRouting.shouldUseCloud(true, true, unsupported, true, false))
        assertFalse(AstraAiRouting.shouldUseCloud(true, true, unsupported, false, true))
        assertFalse(AstraAiRouting.shouldUseCloud(true, true, "", false, false))
    }
}
