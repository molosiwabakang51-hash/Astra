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

    @Test fun advancedImportRequiresEnoughStorageBeforeCopying() {
        val mib = 1024L * 1024L
        assertTrue(!AstraModelImport.hasImportStartingSpace(550L * mib))
        assertTrue(AstraModelImport.hasImportStartingSpace(1_300_000_000L))
        assertTrue(!AstraModelImport.hasImportStartingSpace(1_100_000_000L))
    }

    @Test fun advancedGgufFailsEarlyWhenOnlyCompactFreeSpaceIsAvailable() {
        val mib = 1024L * 1024L
        val advancedFileSize = 1223L * mib
        val available = 700L * mib
        assertTrue(!AstraModelImport.hasImportStartingSpace(available))
        assertTrue(!AstraModelImport.hasSpaceForKnownGguf(available, advancedFileSize))
        assertTrue(AstraModelImport.hasSpaceForKnownGguf(
            availableBytes = advancedFileSize + 64L * mib,
            sizeBytes = advancedFileSize
        ))
    }

    @Test fun knownCompactGgufIsRejectedEvenWhenStorageIsPlentiful() {
        val mib = 1024L * 1024L
        val compactSize = 523L * mib
        assertTrue(!AstraModelImport.hasSpaceForKnownGguf(2_000_000_000L, compactSize))
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
            "assets/Qwen3-1.7B-Q4_K_M.gguf" to gguf
        )
        val (output, count) = extract(apk)
        assertArrayEquals(gguf, output)
        assertEquals(gguf.size.toLong(), count)
    }

    @Test fun importsModelFromGithubArtifactZipContainingFullApk() = runBlocking {
        val gguf = "GGUF test bytes".toByteArray()
        val apk = zip(
            "classes.dex" to byteArrayOf(0, 1),
            "assets/Qwen3-1.7B-Q4_K_M.gguf" to gguf
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
            output.putNextEntry(ZipEntry("assets/Qwen3-1.7B-Q4_K_M.gguf"))
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
                    output.putNextEntry(ZipEntry("assets/Qwen3-1.7B-Q4_K_M.gguf"))
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
            "assets/Qwen3-1.7B-Q4_K_M.gguf" to model
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
            "assets/Qwen3-1.7B-Q4_K_M.gguf" to model
        )
        val (extracted, count) = extract(archive)
        assertArrayEquals(model, extracted)
        assertEquals(model.size.toLong(), count)
    }

    @Test fun fullAssetCopyHandlesShortAndTransientZeroReadsWithoutDataLoss() = runBlocking {
        val fixture = "GGUF bundled asset with short reads and no network".toByteArray()
        val source = object : java.io.InputStream() {
            var offset = 0
            var calls = 0
            override fun read(): Int =
                if (offset == fixture.size) -1 else fixture[offset++].toInt() and 0xff
            override fun read(bytes: ByteArray, start: Int, length: Int): Int {
                calls++
                if (calls == 1 || calls == 3 || calls == 5) return 0
                if (offset == fixture.size) return -1
                val lengthToCopy = minOf(length, 3, fixture.size - offset)
                fixture.copyInto(bytes, start, offset, offset + lengthToCopy)
                offset += lengthToCopy
                return lengthToCopy
            }
        }
        val out = ByteArrayOutputStream()
        val digest = MessageDigest.getInstance("SHA-256")
        val progress = mutableListOf<Long>()
        val copied = AstraModelImport.copyBounded(source, out, digest) {
            progress.add(it)
        }
        assertEquals(fixture.size.toLong(), copied)
        assertArrayEquals(fixture, out.toByteArray())
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(fixture),
            digest.digest())
        assertEquals(listOf(fixture.size.toLong()), progress)
    }

    @Test fun fullAssetCopyStallFailsAndPreservesOnlyBoundedPartialOutput() = runBlocking {
        val source = object : java.io.InputStream() {
            var calls = 0
            override fun read(): Int = error("bulk reads only")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                calls++
                if (calls == 1) {
                    bytes[offset] = 71
                    return 1
                }
                return 0
            }
        }
        val out = ByteArrayOutputStream()
        val failure = runCatching {
            AstraModelImport.copyBounded(
                source, out, MessageDigest.getInstance("SHA-256"), {}
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("stopped making read progress"))
        assertArrayEquals(byteArrayOf(71), out.toByteArray())
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


    @Test fun modelSignatureProbeHandlesShortAndroidFilesReadsWithoutConsumingBytes() {
        val expected = "GGUF model bytes".toByteArray(Charsets.US_ASCII)
        val source = object : java.io.InputStream() {
            var position = 0
            override fun read(): Int =
                if (position == expected.size) -1
                else expected[position++].toInt() and 0xff
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= expected.size) return -1
                buffer[offset] = expected[position++]
                return 1
            }
        }
        val buffered = java.io.BufferedInputStream(source, 8)
        assertArrayEquals("GGUF".toByteArray(Charsets.US_ASCII),
            AstraModelImport.readPrefix4(buffered))
        val copied = ByteArrayOutputStream()
        buffered.copyTo(copied)
        assertArrayEquals(expected, copied.toByteArray())
    }

    @Test fun modelSignatureProbeRejectsStalledProviderAndShortPartialHeaders() {
        val stalled = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, off: Int, len: Int): Int = 0
        }
        val failure = runCatching {
            AstraModelImport.readPrefix4(java.io.BufferedInputStream(
                AstraModelImport.guardTransientEmptyReads(stalled), 8
            ))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("stopped responding"))
        assertArrayEquals(byteArrayOf(0x47, 0x47),
            AstraModelImport.readPrefix4(
                java.io.BufferedInputStream(ByteArrayInputStream(byteArrayOf(0x47, 0x47)))
            ))
    }

    @Test fun transientZeroByteDocumentProviderReadsStillRevealFullGgufHeader() {
        val bytes = "GGUF remaining".toByteArray(Charsets.US_ASCII)
        val provider = object : java.io.InputStream() {
            var index = 0
            var emptyCount = 0
            override fun read(): Int =
                if (index == bytes.size) -1 else bytes[index++].toInt() and 0xff
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (emptyCount < 8) {
                    emptyCount++
                    return 0
                }
                if (index == bytes.size) return -1
                b[off] = bytes[index++]
                return 1
            }
        }
        val buffer = java.io.BufferedInputStream(
            AstraModelImport.guardTransientEmptyReads(provider), 8
        )
        assertArrayEquals("GGUF".toByteArray(Charsets.US_ASCII),
            AstraModelImport.readPrefix4(buffer))
        assertEquals('G'.code, buffer.read())
        assertEquals('G'.code, buffer.read())
    }
}
