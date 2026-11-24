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

    // Keep this stateless helper in a companion: nested Entry is not an
    // inner class and cannot call an instance member of the enclosing cache.
    // Unknown file keys still fail closed (require another full SHA-256).
    private companion object {
        fun fileIdentity(file: File): Any? = runCatching {
            Files.readAttributes(
                file.toPath(), BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS
            ).fileKey()
        }.getOrNull()
    }

    @Volatile private var entry: Entry? = null

    /**
     * A verified GGUF was promoted or backup-recovered. Its previous inode
     * cache is no longer trusted, even if Android reuses a file key, length
     * and timestamp. This never deletes installed weights.
     */
    fun invalidate() {
        entry = null
    }

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

/**
 * Hashing a 1.7B GGUF costs substantial phone I/O. All Activity-created
 * brains in the SAME process may reuse a successful SHA-256 verification,
 * but only while file identity/size/mtime still match. New processes never
 * inherit a digest: no disk preferences or trust-on-first-use shortcuts.
 * Every SAF/HTTPS import independently validates the official SHA-256.
 */
internal object AstraProcessVerifiedModelCache {
    val cache = AstraVerifiedModelCache()
}
