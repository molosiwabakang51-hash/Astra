package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AstraSharedStorageSearchTest {
    @Test fun recognizedExplicitCommandsDoNotRequireRepeatingPermissions() {
        assertEquals("bond", AstraSharedStorageSearch.parse("search phone bond"))
        assertEquals("report", AstraSharedStorageSearch.parse("search my phone for report?"))
        assertEquals("receipt", AstraSharedStorageSearch.parse("find file on my phone receipt"))
        assertNull(AstraSharedStorageSearch.parse("search phone"))
        assertNull(AstraSharedStorageSearch.parse("look through every app privately"))
        assertNull(AstraSharedStorageSearch.parse("search phone ../Android/data"))
    }

    @Test fun findsOnlyFilenamesAndDoesNotCopyContents() {
        val root = Files.createTempDirectory("astra-shared-root").toFile()
        try {
            val notes = File(root, "Documents").apply { mkdirs() }
            val report = File(notes, "bond report.txt")
            report.writeText("SECRET_CONTENT_DO_NOT_INDEX_OR_DISPLAY")
            val video = File(root, "video.mp4")
            video.writeText("example")
            val result = AstraSharedStorageSearch.search(root, "bond")
            assertEquals(1, result.hits.size)
            assertEquals("Documents/bond report.txt", result.hits[0].relativePath.replace('\\', '/'))
            assertFalse(result.render("bond").contains("SECRET_CONTENT"))
            assertEquals("SECRET_CONTENT_DO_NOT_INDEX_OR_DISPLAY", report.readText())
            assertTrue(video.isFile)
            assertFalse(File(root, "astra-search-index").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun systemAppPrivateAndHiddenDirectoriesAreNotSearched() {
        val root = Files.createTempDirectory("astra-private-root").toFile()
        try {
            File(root, "Android/data/com.private/files").apply { mkdirs() }
                .resolve("secret-report.pdf").writeText("secret")
            File(root, ".secrets").apply { mkdirs() }
                .resolve("secret-report.pdf").writeText("secret")
            File(root, "Downloads").apply { mkdirs() }
                .resolve("public-report.pdf").writeText("safe")
            val result = AstraSharedStorageSearch.search(root, "report")
            assertEquals(1, result.hits.size)
            assertTrue(result.hits[0].relativePath.endsWith("public-report.pdf"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsInvalidSearchTermsWithoutFilesystemEffects() {
        assertNull(AstraSharedStorageSearch.parse("search phone " + "a".repeat(101)))
        assertNull(AstraSharedStorageSearch.parse("search phone ./foo"))
        assertNull(AstraSharedStorageSearch.parse("calculate 12 * 5"))
    }
}
