package com.astra.agent

import java.io.File

/**
 * A successful GGUF SHA-256 verification only remains valid for the same
 * file identity. Manual imports replace the path atomically; caching by
 * filename alone would silently skip verification after that replacement.
 *
 * File length and modification time are cheap on-device checks. An explicit
 * import still performs full SHA-256 validation independently.
 */
internal class AstraVerifiedModelCache {
    private data class Entry(
        val file: File,
        val size: Long,
        val modifiedAt: Long,
        val sha256: String
    ) {
        fun stillMatchesFile(): Boolean =
            file.isFile && file.length() == size && file.lastModified() == modifiedAt
    }

    private var entry: Entry? = null

    fun hasVerifiedFile(): Boolean = entry?.stillMatchesFile() == true

    fun matching(fileName: String, expectedSha256: String): File? =
        entry?.takeIf {
            it.file.name == fileName &&
                it.sha256.equals(expectedSha256, ignoreCase = true) &&
                it.stillMatchesFile()
        }?.file

    /** True if a same-named file was removed, replaced, or changed. */
    fun changed(fileName: String): Boolean =
        entry?.let { it.file.name == fileName && !it.stillMatchesFile() } == true

    fun rememberVerified(file: File, expectedSha256: String) {
        require(file.isFile && file.length() > 0L) {
            "Cannot cache verification of a missing GGUF."
        }
        entry = Entry(file, file.length(), file.lastModified(), expectedSha256.lowercase())
    }
}
