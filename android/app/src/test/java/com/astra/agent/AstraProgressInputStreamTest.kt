package com.astra.agent

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraProgressInputStreamTest {
    private fun stalledProvider(): InputStream = object : InputStream() {
        override fun read(): Int = error("Only bulk reads are supported")
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
    }

    @Test fun permanentlyStalledSafProviderFailsWithoutUnboundedLoop() {
        val protected = AstraProgressInputStream(stalledProvider())
        val failure = runCatching { protected.read(ByteArray(128)) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("stopped returning data"))
        assertTrue(failure?.message.orEmpty().contains("Downloads"))
        assertFalse(failure?.message.orEmpty().contains("content://"))
    }

    @Test fun transientEmptyReadsRecoverWithoutDroppingValidBytes() {
        val data = "Bond duration and convexity are useful risk measurements."
            .toByteArray()
        val source = object : InputStream() {
            var cursor = 0
            var empty = 0
            override fun read(): Int = error("Only bulk reads are supported")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (empty++ < 8) return 0
                if (cursor >= data.size) return -1
                val n = minOf(data.size - cursor, length)
                data.copyInto(buffer, offset, cursor, cursor + n)
                cursor += n
                return n
            }
        }
        val protected = AstraProgressInputStream(source)
        val result = ByteArray(512)
        assertEquals(data.size, protected.read(result))
        assertArrayEquals(data, result.copyOf(data.size))
        assertEquals(-1, protected.read(result))
    }

    @Test fun emptyBulkRequestIsNotMisclassifiedAsProviderHang() {
        val guard = AstraProgressInputStream(stalledProvider())
        assertEquals(0, guard.read(ByteArray(0)))
        val error = runCatching { guard.read(ByteArray(4)) }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test fun invalidBufferOffsetsStillRejectWithoutTouchingProvider() {
        val protected = AstraProgressInputStream(ByteArrayInputStream(byteArrayOf(1, 2)))
        assertTrue(runCatching { protected.read(ByteArray(2), -1, 0) }.isFailure)
        assertTrue(runCatching { protected.read(ByteArray(2), 3, 0) }.isFailure)
        assertTrue(runCatching { protected.read(ByteArray(2), 1, 2) }.isFailure)
        assertEquals(1, protected.read())
    }

    @Test fun realTextImportFailsSafelyWithoutCreatingFalseKnowledge() {
        val library = AstraBookLibrary(
            java.nio.file.Files.createTempDirectory("astra-zero-read").toFile()
        )
        val error = runCatching {
            library.importBook("notes.txt", stalledProvider())
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(library.listBooks().isEmpty())
    }

    @Test fun directHtmlAndRtfExtractionHaveTheSameBoundedFailure() {
        assertTrue(runCatching {
            AstraDocumentText.extract("html", stalledProvider())
        }.exceptionOrNull() is IOException)
        assertTrue(runCatching {
            AstraRtfText.extract(stalledProvider())
        }.exceptionOrNull() is IOException)
        assertTrue(
            AstraDocumentText.extract(
                "html", ByteArrayInputStream(
                    "<h1>Offline finance</h1><p>Bond duration and convexity.</p>".toByteArray()
                )
            ).contains("Bond duration and convexity")
        )
    }
}
