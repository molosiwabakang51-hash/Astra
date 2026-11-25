package com.astra.agent

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AstraTransactionalLearningTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun learningPersistsAndRestoresIdenticalPrediction() {
        val file = File(folder.root, "adapter.bin")
        val adapter = AstraContinualLearning(file)
        repeat(5) { adapter.learn("Explain bond duration and convexity.") }
        assertEquals(5, adapter.status().steps)
        assertTrue(file.isFile)
        val restored = AstraContinualLearning(file)
        assertEquals(adapter.status(), restored.status())
        assertEquals(adapter.predict("bond duration"), restored.predict("bond duration"))
    }

    @Test fun failedPersistenceRollsBackWeightsAndCounters() {
        val blockedParent = folder.newFile("not-a-directory")
        val adapter = AstraContinualLearning(File(blockedParent, "adapter.bin"))
        val before = adapter.status()
        val prediction = adapter.predict("bond duration")
        val failure = runCatching { adapter.learn("Explain bond duration.") }
        assertTrue(failure.isFailure)
        assertEquals(before, adapter.status())
        assertEquals(prediction, adapter.predict("bond duration"))
    }

    @Test fun directCallsCannotTrainOnSensitiveOrOversizedMessages() {
        val file = File(folder.root, "adapter.bin")
        val adapter = AstraContinualLearning(file)
        val before = adapter.status()
        for (message in listOf("My password is example", "remember that bonds pay coupons",
            "I am not okay mentally", "physics ".repeat(100))) {
            assertEquals(before, adapter.learn(message))
        }
        assertFalse(file.exists())
    }

    @Test fun resetDeletesDurableAndTemporaryCheckpoints() {
        val file = File(folder.root, "adapter.bin")
        val adapter = AstraContinualLearning(file)
        adapter.learn("Explain bond duration.")
        val partial = File(folder.root, "adapter.bin.partial")
        partial.writeText("interrupted checkpoint")
        adapter.reset()
        assertEquals(0, adapter.status().steps)
        assertFalse(file.exists())
        assertFalse(partial.exists())
        assertEquals(AstraContinualLearning(file).predict("bond duration"),
            adapter.predict("bond duration"))
    }

    @Test fun trailingCheckpointDataIsRejected() {
        val file = File(folder.root, "adapter.bin")
        val adapter = AstraContinualLearning(file)
        adapter.learn("Explain bond duration.")
        file.appendBytes(byteArrayOf(1))
        assertEquals(0, AstraContinualLearning(file).status().steps)
    }
}
