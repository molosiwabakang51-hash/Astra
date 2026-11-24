package com.astra.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File identity is checked on every process-cache hit. No successful SHA
 * may survive replacement of the installed model or an app process restart.
 * Fixtures are tiny; these tests do NOT hash a 1.7B model or time a handset.
 */
class AstraProcessVerifiedModelCacheTest {
    private val name = AstraModelImport.ADVANCED_NAME
    private val digest = AstraModelImport.ADVANCED_SHA256

    @Test fun separateActivityBrainsCanShareUnchangedVerifiedInode() {
        val directory = Files.createTempDirectory("astra-cache-across-brains").toFile()
        val firstBrainCache = AstraProcessVerifiedModelCache.cache
        val secondBrainCache = AstraProcessVerifiedModelCache.cache
        try {
            val file = File(directory, name)
            file.writeText("GGUF verified fixture")
            firstBrainCache.invalidate()
            firstBrainCache.rememberVerified(file, digest)
            assertSame(firstBrainCache, secondBrainCache)
            assertTrue(secondBrainCache.hasVerifiedFile())
            assertSame(file, secondBrainCache.matching(name, digest))
        } finally {
            firstBrainCache.invalidate()
            directory.deleteRecursively()
        }
    }

    @Test fun verifiedPromotionClearsProcessDigestBeforeNewActivityUsesIt() {
        val directory = Files.createTempDirectory("astra-cache-after-promotion").toFile()
        val cache = AstraProcessVerifiedModelCache.cache
        try {
            val installed = File(directory, name)
            installed.writeText("GGUF original model")
            val next = File(directory, "next-model.importing")
            next.writeText("GGUF replacement model")
            cache.invalidate()
            cache.rememberVerified(installed, digest)
            assertTrue(cache.hasVerifiedFile())
            AstraModelStorage.installVerified(next, installed)
            assertFalse(cache.hasVerifiedFile())
            assertNull(cache.matching(name, digest))
            assertTrue(installed.isFile)
        } finally {
            cache.invalidate()
            directory.deleteRecursively()
        }
    }

    @Test fun failedPromotionIsConservativeAndNeverKeepsCachedChecksum() {
        val directory = Files.createTempDirectory("astra-cache-promotion-error").toFile()
        val cache = AstraProcessVerifiedModelCache.cache
        try {
            val installed = File(directory, name)
            installed.writeText("GGUF original")
            cache.invalidate()
            cache.rememberVerified(installed, digest)
            val missing = File(directory, "missing.importing")
            assertTrue(runCatching {
                AstraModelStorage.installVerified(missing, installed)
            }.isFailure)
            // A file rejected before any rename is unchanged, so its
            // verified identity is still intact and safe to reuse.
            assertTrue(cache.hasVerifiedFile())
        } finally {
            cache.invalidate()
            directory.deleteRecursively()
        }
    }

    @Test fun replacingModelWithDifferentInodeNeverUsesOldDigest() {
        val directory = Files.createTempDirectory("astra-cache-file-key").toFile()
        val cache = AstraProcessVerifiedModelCache.cache
        try {
            val target = File(directory, name)
            val next = File(directory, "next-file")
            target.writeText("GGUF identity A")
            next.writeText("GGUF identity B")
            val oldModified = target.lastModified()
            assertTrue(next.setLastModified(oldModified))
            cache.invalidate()
            cache.rememberVerified(target, digest)
            assertTrue(cache.hasVerifiedFile())
            AstraModelStorage.installVerified(next, target)
            assertNull(cache.matching(name, digest))
        } finally {
            cache.invalidate()
            directory.deleteRecursively()
        }
    }
}
