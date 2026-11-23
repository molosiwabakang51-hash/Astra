package com.astra.agent

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AstraModelDownloadTest {
    @Test fun completedModelDownloadOnlyCertifiesFileIntegrity() {
        val displayed = AstraModelImportCoordinator.verifiedFileMessage(1_280_000_000L)
        assertTrue(displayed.contains("QWEN3 1.7B FILE VERIFIED"))
        assertTrue(displayed.contains("SHA-256 verified"))
        assertTrue(displayed.contains("Test on-device AI"))
        assertTrue(displayed.contains("confirm offline generation"))
        assertTrue(!displayed.contains("QWEN3 1.7B READY"))
        assertTrue(!displayed.contains("Offline conversation is available"))
    }

    @Test fun stalledNetworkSourceCannotSpinForeverOrDiscardWrittenPartial() = runBlocking {
        val alreadyDownloaded = "good-partial".toByteArray()
        val output = ByteArrayOutputStream().apply { write(alreadyDownloaded) }
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int) = 0
        }
        val failure = runCatching {
            AstraModelDownload.copyResponse(
                stalled, output, alreadyDownloaded.size.toLong(), 300L
            ) { _, _ -> }
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("resume without starting over"))
        assertArrayEquals(alreadyDownloaded, output.toByteArray())
    }

    @Test fun transientZeroReadsRecoverAndRangedTransferAppendsExactlyOnce() = runBlocking {
        val original = "previous-partial".toByteArray()
        val body = "download-remainder".toByteArray()
        val output = ByteArrayOutputStream().apply { write(original) }
        val input = object : InputStream() {
            var index = 0
            var zeros = 0
            override fun read(): Int = error("Only bulk reads expected")
            override fun read(dest: ByteArray, off: Int, len: Int): Int {
                if (zeros++ < 8) return 0
                if (index >= body.size) return -1
                val count = minOf(len, 3, body.size - index)
                body.copyInto(dest, off, index, index + count)
                index += count
                return count
            }
        }
        val copied = AstraModelDownload.copyResponse(
            input, output, original.size.toLong(),
            (original.size + body.size).toLong()
        ) { _, _ -> }
        assertEquals((original.size + body.size).toLong(), copied)
        assertArrayEquals(original + body, output.toByteArray())
    }

    @Test fun unexpectedExtraBytesNeverGetWrittenBeyondDeclaredResponse() = runBlocking {
        val output = ByteArrayOutputStream()
        val failure = runCatching {
            AstraModelDownload.copyResponse(
                ByteArrayInputStream("TOO-LONG".toByteArray()),
                output, 0L, 3L
            ) { _, _ -> }
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, output.size())
    }

    @Test fun cancelledBeforeFirstReadDoesNotConsumeNetworkOrTouchPartial() = runBlocking {
        val existing = "already-downloaded".toByteArray()
        val output = ByteArrayOutputStream().apply { write(existing) }
        var reads = 0
        val source = object : InputStream() {
            override fun read(): Int = error("bulk reads only")
            override fun read(dest: ByteArray, off: Int, len: Int): Int {
                reads++
                return -1
            }
        }
        val cancelled = Job().apply { cancel(CancellationException("owner stopped")) }
        val failure = runCatching {
            withContext(cancelled) {
                AstraModelDownload.copyResponse(
                    source, output, existing.size.toLong(), 200L
                ) { _, _ -> }
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(0, reads)
        assertArrayEquals(existing, output.toByteArray())
    }

    @Test fun cancellationFromProgressCallbackPreservesDownloadedBytes() = runBlocking {
        val original = "saved-partial".toByteArray()
        val body = ByteArray(16 * 1024 * 1024) { 0x47.toByte() }
        val output = ByteArrayOutputStream().apply { write(original) }
        var progressCalls = 0
        val failure = runCatching {
            AstraModelDownload.copyResponse(
                ByteArrayInputStream(body + byteArrayOf(0x55)),
                output, original.size.toLong(),
                (original.size + body.size + 1L)
            ) { _, _ ->
                progressCalls++
                throw CancellationException("owner stopped during progress")
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, progressCalls)
        assertEquals(original.size + body.size, output.size())
        assertArrayEquals(original, output.toByteArray().copyOfRange(0, original.size))
        assertFalse(output.toByteArray().last() == 0x55.toByte())
    }

    @Test fun stalledChecksumSourceStopsWithoutInventingVerifiedWeights() = runBlocking {
        var reads = 0
        val stalled = object : InputStream() {
            override fun read(): Int = error("bulk reads only")
            override fun read(dest: ByteArray, off: Int, len: Int): Int {
                reads++
                return 0
            }
        }
        val failure = runCatching {
            AstraModelDownload.checksumStream(stalled)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("stopped providing data"))
        assertEquals(17, reads)
    }

    @Test fun checksumTransientEmptyReadsStillProduceExactSha256() = runBlocking {
        val bytes = "GGUF-local-weights".toByteArray()
        val source = object : InputStream() {
            var index = 0
            var zeros = 0
            override fun read(): Int = error("bulk reads only")
            override fun read(dest: ByteArray, off: Int, len: Int): Int {
                if (zeros < 8) {
                    zeros++
                    return 0
                }
                if (index == bytes.size) return -1
                val size = minOf(len, 3, bytes.size - index)
                bytes.copyInto(dest, off, index, index + size)
                index += size
                return size
            }
        }
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, AstraModelDownload.checksumStream(source))
    }

    @Test fun cancelledChecksumDoesNotReadOrCertifyFile() = runBlocking {
        var reads = 0
        val source = object : InputStream() {
            override fun read(): Int = error("bulk reads only")
            override fun read(dest: ByteArray, off: Int, len: Int): Int {
                reads++
                return -1
            }
        }
        val cancelled = Job().apply { cancel(CancellationException("owner cancelled")) }
        val failure = runCatching {
            withContext(cancelled) { AstraModelDownload.checksumStream(source) }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(0, reads)
    }

    @Test fun invalidRangeCannotStartCopy() = runBlocking {
        val out = ByteArrayOutputStream()
        for ((offset, expected) in listOf(-1L to 10L, 20L to 10L,
            0L to 1_350_000_001L
        )) {
            assertTrue(runCatching {
                AstraModelDownload.copyResponse(
                    ByteArrayInputStream(byteArrayOf(1)), out, offset, expected
                ) { _, _ -> }
            }.isFailure)
        }
        assertEquals(0, out.size())
    }
}
