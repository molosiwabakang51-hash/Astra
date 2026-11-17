package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AstraVerifiedModelCacheTest {
    private val name = "Qwen3-1.7B-Q4_K_M.gguf"
    private val digest = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"

    private fun withModel(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("astra-model-cache-test").toFile()
        try {
            val file = File(directory, name)
            file.writeText("GGUF fixture")
            block(file)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun unchangedVerifiedModelCanBeReused() = withModel { file ->
        val cache = AstraVerifiedModelCache()
        assertFalse(cache.hasVerifiedFile())
        cache.rememberVerified(file, digest)
        assertTrue(cache.hasVerifiedFile())
        assertEquals(file, cache.matching(name, digest))
        assertFalse(cache.changed(name))
    }

    @Test fun differentExpectedDigestNeverUsesCachedVerification() = withModel { file ->
        val cache = AstraVerifiedModelCache()
        cache.rememberVerified(file, digest)
        assertNull(cache.matching(name, "0".repeat(64)))
        assertNull(cache.matching("qwen3-0.6b-q4km.gguf", digest))
    }

    @Test fun sameNamedModelReimportInvalidatesCachedChecksum() = withModel { file ->
        val cache = AstraVerifiedModelCache()
        cache.rememberVerified(file, digest)
        val previousModifiedAt = file.lastModified()
        // Simulate atomic replacement, including one with the same length.
        val replacement = File(file.parentFile, "new-model.partial")
        replacement.writeText("GGUF fixture")
        assertEquals(file.length(), replacement.length())
        assertTrue(replacement.setLastModified(previousModifiedAt + 20_000L))
        assertTrue(file.delete())
        assertTrue(replacement.renameTo(file))
        assertTrue(cache.changed(name))
        assertFalse(cache.hasVerifiedFile())
        assertNull(cache.matching(name, digest))
        cache.rememberVerified(file, digest)
        assertEquals(file, cache.matching(name, digest))
    }

    @Test fun partialOrDeletedModelCannotRetainVerifiedStatus() = withModel { file ->
        val cache = AstraVerifiedModelCache()
        cache.rememberVerified(file, digest)
        file.writeText("GGUF")
        assertTrue(cache.changed(name))
        assertNull(cache.matching(name, digest))
        assertTrue(file.delete())
        assertFalse(cache.hasVerifiedFile())
        assertNull(cache.matching(name, digest))
    }

    @Test fun missingFileCannotBeMarkedVerified() {
        val directory = Files.createTempDirectory("astra-missing-model-test").toFile()
        try {
            val cache = AstraVerifiedModelCache()
            val missing = File(directory, name)
            val failure = runCatching {
                cache.rememberVerified(missing, digest)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        } finally {
            directory.deleteRecursively()
        }
    }
}
