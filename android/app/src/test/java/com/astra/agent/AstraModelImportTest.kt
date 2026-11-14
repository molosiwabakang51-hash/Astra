package com.astra.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class AstraModelImportTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val result = ByteArrayOutputStream()
        ZipOutputStream(result).use { output ->
            for ((name, data) in entries) {
                output.putNextEntry(ZipEntry(name))
                output.write(data)
                output.closeEntry()
            }
        }
        return result.toByteArray()
    }

    private suspend fun extract(data: ByteArray): Pair<ByteArray, Long> {
        val extracted = ByteArrayOutputStream()
        val hash = MessageDigest.getInstance("SHA-256")
        val copied = AstraModelImport.copyFromArchive(
            ZipInputStream(ByteArrayInputStream(data)),
            extracted, hash, 0, {}
        )
        return extracted.toByteArray() to copied
    }

    @Test fun importsExistingModelFromFullApkWithoutNetwork() = runBlocking {
        val gguf = byteArrayOf(0x47, 0x47, 0x55, 0x46, 1, 2, 3, 4)
        val apk = zip(
            "assets/open_source_licenses/SOURCES.txt" to "licenses".toByteArray(),
            "assets/qwen3-0.6b-q4km.gguf" to gguf
        )
        val (output, count) = extract(apk)
        assertArrayEquals(gguf, output)
        assertEquals(gguf.size.toLong(), count)
    }

    @Test fun importsModelFromGithubArtifactZipContainingFullApk() = runBlocking {
        val gguf = "GGUF test bytes".toByteArray()
        val apk = zip(
            "classes.dex" to byteArrayOf(0, 1),
            "assets/qwen3-0.6b-q4km.gguf" to gguf
        )
        val artifact = zip("Astra-v0.10.4-offline-arm64-debug.apk" to apk)
        val (output, count) = extract(artifact)
        assertArrayEquals(gguf, output)
        assertEquals(gguf.size.toLong(), count)
    }

    @Test fun archiveWithoutAstraModelIsRejected() = runBlocking {
        val archive = zip("assets/wrong.gguf" to byteArrayOf(1, 2, 3))
        val error = runCatching { extract(archive) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("No bundled Qwen3"))
    }
}
