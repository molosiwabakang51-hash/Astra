package com.astra.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class AstraCompletedModelDownloadTest {
    @get:Rule val folder = TemporaryFolder()

    private fun hash(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data).joinToString("") { "%02x".format(it) }

    @Test fun completeVerifiedSavedBytesInstallWithoutAnotherDownload() = runBlocking {
        val bytes = "GGUF COMPLETE OFFLINE FIXTURE".toByteArray()
        val partial = folder.newFile("model.gguf.downloading").apply { writeBytes(bytes) }
        val target = folder.newFile("model.gguf").apply { writeText("old-installed") }
        var verified = 0
        val restored = AstraCompletedModelDownload.promoteIfVerified(
            partial, target, bytes.size.toLong(), bytes.size.toLong(),
            hash(bytes).uppercase()
        ) { file ->
            verified++
            hash(file.readBytes())
        }
        assertEquals(bytes.size.toLong(), restored)
        assertEquals(1, verified)
        assertArrayEquals(bytes, target.readBytes())
        assertFalse(partial.exists())
        assertFalse(File(target.parentFile, target.name + ".before-import").exists())
    }

    @Test fun shortSavedBytesRemainAvailableForSafeHttpResume() = runBlocking {
        val bytes = byteArrayOf(0x47, 0x47, 0x55, 0x46)
        val partial = folder.newFile("short.downloading").apply { writeBytes(bytes) }
        val target = folder.newFile("target.gguf").apply { writeText("old-good") }
        val recovered = AstraCompletedModelDownload.promoteIfVerified(
            partial, target, 10L, 20L, hash(bytes)
        ) { error("SHA should not run for a short file") }
        assertNull(recovered)
        assertTrue(partial.exists())
        assertArrayEquals(bytes, partial.readBytes())
        assertEquals("old-good", target.readText())
    }

    @Test fun sizeCompatibleChecksumMismatchDoesNotDiscardUsefulResumableBytes() = runBlocking {
        val bytes = "incomplete but inside broad model size window".toByteArray()
        val partial = folder.newFile("incomplete.downloading").apply {
            writeBytes(bytes)
        }
        val target = folder.newFile("target.gguf").apply { writeText("old-good") }
        val incorrectHash = hash("different".toByteArray())
        val restored = AstraCompletedModelDownload.promoteIfVerified(
            partial, target, 10L, 200L, incorrectHash
        ) { hash(it.readBytes()) }
        assertNull(restored)
        assertArrayEquals(bytes, partial.readBytes())
        assertEquals("old-good", target.readText())
    }

    @Test fun missingSavedBytesDoNotTriggerChecksumOrRemoveInstalledModel() = runBlocking {
        val partial = File(folder.root, "not-yet-downloaded")
        val target = folder.newFile("target.gguf").apply { writeText("verified") }
        assertNull(AstraCompletedModelDownload.promoteIfVerified(
            partial, target, 1L, 100L, hash("anything".toByteArray())
        ) { error("No local partial exists") })
        assertEquals("verified", target.readText())
    }

    @Test fun digestCancellationCannotDeleteSavedBytesOrInstalledModel() = runBlocking {
        val bytes = "in-progress".toByteArray()
        val partial = folder.newFile("staging.downloading").apply { writeBytes(bytes) }
        val target = folder.newFile("existing.gguf").apply { writeText("working") }
        val failure = runCatching {
            AstraCompletedModelDownload.promoteIfVerified(
                partial, target, 1L, 100L, hash(bytes)
            ) { throw CancellationException("user cancelled before promotion") }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertArrayEquals(bytes, partial.readBytes())
        assertEquals("working", target.readText())
    }

    @Test fun invalidChecksumAndRangeDoNotTouchFiles() = runBlocking {
        val bytes = "saved".toByteArray()
        val partial = folder.newFile("partial").apply { writeBytes(bytes) }
        val target = folder.newFile("target.gguf").apply { writeText("working") }
        for ((min, max, sha) in listOf(
            Triple(0L, 50L, hash(bytes)),
            Triple(10L, 5L, hash(bytes)),
            Triple(1L, 50L, "not-a-checksum")
        )) {
            val outcome = runCatching {
                AstraCompletedModelDownload.promoteIfVerified(
                    partial, target, min, max, sha
                ) { hash(it.readBytes()) }
            }
            assertTrue(outcome.isFailure)
        }
        assertTrue(partial.isFile)
        assertEquals("working", target.readText())
    }
}
