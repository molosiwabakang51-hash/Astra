package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AstraModelHealthTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun failedAdvancedModelIsSkippedOnRepeatedConversationTurns() {
        val model = folder.newFile("Qwen3-1.7B-Q4_K_M.gguf")
        model.writeText("BAD MODEL")
        val health = AstraModelHealth()
        assertTrue(health.shouldAttempt(model))
        health.recordFailure(model)
        assertFalse(health.shouldAttempt(model))
        assertFalse(health.shouldAttempt(model))
    }

    @Test fun replacingFailedWeightsAllowsAHealthyAdvancedModelRetry() {
        val model = folder.newFile("replacement.gguf")
        model.writeText("BROKEN")
        val health = AstraModelHealth()
        health.recordFailure(model)
        assertFalse(health.shouldAttempt(model))
        // A verified replacement has a new on-disk identity.
        model.writeText("NEW VERIFIED MODEL WITH DIFFERENT SIZE")
        assertTrue(health.shouldAttempt(model))
    }

    @Test fun sameSizeSameTimestampAtomicReplacementClearsFailedLoad() {
        val model = folder.newFile("same-path.gguf")
        model.writeText("BROKEN-WEIGHTS")
        val health = AstraModelHealth()
        health.recordFailure(model)
        assertFalse(health.shouldAttempt(model))
        val timestamp = model.lastModified()
        val replacement = File(folder.root, "new-weights.tmp")
        replacement.writeText("WORKING-WEIGHT")
        assertTrue(replacement.setLastModified(timestamp))
        assertTrue(model.length() == replacement.length())
        // Replacing the path rather than rewriting in place must invalidate
        // failure quarantine, even when size and mtime are unchanged.
        Files.move(replacement.toPath(), model.toPath(),
            StandardCopyOption.REPLACE_EXISTING)
        assertTrue(health.shouldAttempt(model))
    }

    @Test fun missingOrDeletedModelDoesNotStayQuarantined() {
        val model = File(folder.root, "not-installed.gguf")
        val health = AstraModelHealth()
        health.recordFailure(model)
        assertTrue(health.shouldAttempt(model))
        model.writeText("old model")
        health.recordFailure(model)
        model.delete()
        assertTrue(health.shouldAttempt(model))
    }

    @Test fun failureMemoryIsNeverPersistedAcrossNewAppSessions() {
        val model = folder.newFile("advanced.gguf")
        model.writeText("needs fallback")
        val first = AstraModelHealth()
        first.recordFailure(model)
        assertFalse(first.shouldAttempt(model))
        assertTrue(AstraModelHealth().shouldAttempt(model))
    }
}
