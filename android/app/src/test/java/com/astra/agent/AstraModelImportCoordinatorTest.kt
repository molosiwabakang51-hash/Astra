package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AstraModelImportCoordinatorTest {
    @Test fun onlyOneOwnerSelectedImportCanRunAtATime() {
        val gate = AstraModelImportGate()
        assertFalse(gate.isRunning())
        assertTrue(gate.acquire())
        assertTrue(gate.isRunning())
        assertFalse(gate.acquire())
        gate.release()
        assertFalse(gate.isRunning())
        assertTrue(gate.acquire())
        gate.release()
    }

    @Test fun verifiedGGUFStatusDoesNotPretendNativeGenerationSucceeded() {
        val status = AstraModelImportCoordinator.verifiedFileMessage(1_278_000_000L)
        assertTrue(status.contains("FILE VERIFIED"))
        assertTrue(status.contains("SHA-256 verified"))
        assertTrue(status.contains("Test on-device AI"))
        assertTrue(status.contains("confirm offline generation"))
        assertFalse(status.contains("Offline conversation is available"))
        assertFalse(status.contains("OFFLINE TEST VERIFIED"))
        assertFalse(status.contains("READY"))
    }

    @Test fun concurrentPickerResultsCannotStartTwoCopyJobs() {
        val gate = AstraModelImportGate()
        val pool = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val go = CountDownLatch(1)
        try {
            val futures = (0 until 8).map {
                pool.submit<Boolean> {
                    ready.countDown()
                    if (!go.await(5, TimeUnit.SECONDS)) return@submit false
                    gate.acquire()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertEquals(1, futures.count { it.get(5, TimeUnit.SECONDS) })
            assertTrue(gate.isRunning())
        } finally {
            gate.release()
            pool.shutdownNow()
        }
    }

    @Test fun failureDiagnosticsNeverPersistProviderUriOrPrivatePath() {
        val selected = "content://com.provider/private/model?token=secret"
        val path = "/storage/emulated/0/Downloads/private-model.gguf"
        val provider = IOException(
            "Cannot open $selected or $path: Permission denied"
        )
        val message = AstraModelImportCoordinator.failureMessage(provider)
        assertTrue(message.startsWith("MODEL IMPORT FAILED:"))
        assertTrue(message.contains("Android Files"))
        assertFalse(message.contains(selected))
        assertFalse(message.contains(path))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("token"))
    }

    @Test fun expectedModelAndStorageErrorsGiveActionableSafeMessages() {
        val disk = AstraModelImportCoordinator.failureMessage(
            IllegalArgumentException("Not enough phone storage for model.")
        )
        assertTrue(disk.contains("Not enough internal storage"))
        val wrongWeights = AstraModelImportCoordinator.failureMessage(
            IllegalArgumentException(
                "Only the official verified Qwen3 1.7B GGUF is supported."
            )
        )
        assertTrue(wrongWeights.contains("not the pinned Qwen3"))
        val stalled = AstraModelImportCoordinator.failureMessage(
            IllegalStateException("Selected model file provider stopped responding.")
        )
        assertTrue(stalled.contains("local Downloads"))
        val generic = AstraModelImportCoordinator.failureMessage(
            IOException("Could not parse /private/path with a_secret")
        )
        assertTrue(generic.contains("could not be imported"))
        assertFalse(generic.contains("/private/"))
        assertFalse(generic.contains("a_secret"))
    }
}
