package com.astra.agent

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

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
        val identity: Any,
        val sha256: String
    ) {
        fun stillMatchesFile(): Boolean =
            file.isFile && file.length() == size && file.lastModified() == modifiedAt &&
                fileIdentity(file) == identity
    }

    // Android 13+ supports NIO file attributes. The inode-backed fileKey
    // changes when an atomic import replaces a GGUF even if its size and
    // timestamp are identical. Unknown identities fail closed (rehash).
    private fun fileIdentity(file: File): Any? = runCatching {
        Files.readAttributes(
            file.toPath(), BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        ).fileKey()
    }.getOrNull()

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
        val identity = fileIdentity(file)
        // Do not claim the checksum is reusable without a stable file identity.
        entry = if (identity != null) Entry(
            file, file.length(), file.lastModified(), identity, expectedSha256.lowercase()
        ) else null
    }
}
