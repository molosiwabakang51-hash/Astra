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

    @Test fun compactImportStartsWithoutRequiringFullAdvancedModelSpace() {
        val mib = 1024L * 1024L
        assertTrue(AstraModelImport.hasImportStartingSpace(550L * mib))
        assertTrue(!AstraModelImport.hasImportStartingSpace(450L * mib))
        // The old blanket 1.5 GB requirement would incorrectly block 550 MiB.
        assertTrue(550L * mib < 1_400_000_000L + 64L * mib)
    }

    @Test fun advancedGgufFailsEarlyWhenOnlyCompactFreeSpaceIsAvailable() {
        val mib = 1024L * 1024L
        val advancedFileSize = 1223L * mib
        val available = 700L * mib
        assertTrue(AstraModelImport.hasImportStartingSpace(available))
        assertTrue(!AstraModelImport.hasSpaceForKnownGguf(available, advancedFileSize))
        assertTrue(AstraModelImport.hasSpaceForKnownGguf(
            availableBytes = advancedFileSize + 64L * mib,
            sizeBytes = advancedFileSize
        ))
    }

    @Test fun knownCompactGgufStillRequiresRealFileSizePlusHeadroom() {
        val mib = 1024L * 1024L
        val compactSize = 523L * mib
        assertTrue(!AstraModelImport.hasSpaceForKnownGguf(550L * mib, compactSize))
        assertTrue(AstraModelImport.hasSpaceForKnownGguf(600L * mib, compactSize))
    }

    @Test fun invalidOrMissingGgufSizeCannotBypassSafeImportChecks() {
        val free = 2_000_000_000L
        assertTrue(!AstraModelImport.hasSpaceForKnownGguf(free, 0L))
        assertTrue(!AstraModelImport.hasSpaceForKnownGguf(free, -1L))
        assertTrue(!AstraModelImport.hasSpaceForKnownGguf(free, 1_400_000_001L))
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

    @Test fun acceptsAdvancedQwen3ModelFromLocalArchive() = runBlocking {
        val gguf = "GGUF advanced fixture".toByteArray()
        val archive = zip("Qwen3-1.7B-Q4_K_M.gguf" to gguf)
        val (output, count) = extract(archive)
        assertArrayEquals(gguf, output)
        assertEquals(gguf.size.toLong(), count)
    }

    @Test fun archiveWithoutAstraModelIsRejected() = runBlocking {
        val archive = zip("assets/wrong.gguf" to byteArrayOf(1, 2, 3))
        val error = runCatching { extract(archive) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("No bundled Qwen3"))
    }
    @Test fun rejectsOversizedArchiveEntryTableBeforeReachingModel() = runBlocking {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { output ->
            repeat(4_097) { index ->
                output.putNextEntry(ZipEntry("unrelated/entry-$index.txt"))
                output.closeEntry()
            }
            output.putNextEntry(ZipEntry("assets/qwen3-0.6b-q4km.gguf"))
            output.write("GGUF ignored".toByteArray())
            output.closeEntry()
        }
        val failure = runCatching { extract(bytes.toByteArray()) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("too many entries"))
    }

    @Test fun nestedApkEntriesShareOuterArchiveScanLimit() = runBlocking {
        fun crowdedApk(count: Int, withModel: Boolean): ByteArray {
            val data = ByteArrayOutputStream()
            ZipOutputStream(data).use { output ->
                repeat(count) { index ->
                    output.putNextEntry(ZipEntry("unrelated/part-$index.txt"))
                    output.closeEntry()
                }
                if (withModel) {
                    output.putNextEntry(ZipEntry("assets/qwen3-0.6b-q4km.gguf"))
                    output.write("GGUF should not be reached".toByteArray())
                    output.closeEntry()
                }
            }
            return data.toByteArray()
        }
        // Each nested APK individually has fewer than 4096 entries, and
        // the outer ZIP has only two. Combined scanning must still stop.
        val archive = zip(
            "part-one.apk" to crowdedApk(2_050, false),
            "part-two.apk" to crowdedApk(2_050, true)
        )
        val failure = runCatching { extract(archive) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("too many entries"))
    }

    @Test fun nestedApkUnderSharedBudgetStillImportsModel() = runBlocking {
        val model = "GGUF shared budget".toByteArray()
        val apk = zip(
            "META-INF/manifest" to byteArrayOf(1),
            "assets/qwen3-0.6b-q4km.gguf" to model
        )
        val outer = zip("notes.txt" to byteArrayOf(2), "model.apk" to apk)
        val (extracted, count) = extract(outer)
        assertArrayEquals(model, extracted)
        assertEquals(model.size.toLong(), count)
    }

    @Test fun smallArchiveWithUnrelatedEntriesStillImportsModel() = runBlocking {
        val model = "GGUF bounded archive".toByteArray()
        val archive = zip(
            "META-INF/MANIFEST.MF" to "manifest".toByteArray(),
            "assets/unrelated.txt" to "other".toByteArray(),
            "assets/qwen3-0.6b-q4km.gguf" to model
        )
        val (extracted, count) = extract(archive)
        assertArrayEquals(model, extracted)
        assertEquals(model.size.toLong(), count)
    }

    @Test fun stalledProviderCannotSpinForeverOnZeroByteReads() = runBlocking {
        val source = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        val output = ByteArrayOutputStream()
        val failure = runCatching {
            AstraModelImport.copyBounded(
                source, output, MessageDigest.getInstance("SHA-256"), {}
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("stopped making read progress"))
        assertEquals(0, output.size())
    }

    @Test fun intermittentZeroReadsRecoverWhenProviderMakesProgress() = runBlocking {
        val source = object : java.io.InputStream() {
            var calls = 0
            override fun read(): Int = error("bulk reads only")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                calls++
                return when (calls) {
                    in 1..8, in 10..17 -> 0
                    9 -> { bytes[offset] = 71; 1 }
                    18 -> { bytes[offset] = 72; 1 }
                    else -> -1
                }
            }
        }
        val output = ByteArrayOutputStream()
        val copied = AstraModelImport.copyBounded(
            source, output, MessageDigest.getInstance("SHA-256"), {}
        )
        assertEquals(2L, copied)
        assertArrayEquals(byteArrayOf(71, 72), output.toByteArray())
    }

}
