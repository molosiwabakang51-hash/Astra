package com.astra.agent

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Session-local backoff for an advanced GGUF that failed checksum preparation
 * or native loading. Avoid repeating a known failed load on every chat turn.
 * Changing/re-importing the model file
 * permits a retry; restarting the app also clears this failure record.
 *
 * The file identity is private to the process and never enters diagnostics.
 */
internal class AstraModelHealth {
    private data class Identity(
        val path: String,
        val size: Long,
        val modified: Long,
        val fileKey: Any
    )
    private var failedAdvanced: Identity? = null

    // File size and mtime alone cannot distinguish an atomic replacement
    // whose new weights happen to have the same size and timestamp.
    // If the filesystem provides no stable identity, fail open and allow a
    // fresh load attempt rather than indefinitely quarantining new weights.
    private fun identity(file: File): Identity? = runCatching {
        if (!file.isFile) return@runCatching null
        val attrs = Files.readAttributes(
            file.toPath(), BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        )
        val key = attrs.fileKey() ?: return@runCatching null
        Identity(file.absolutePath, attrs.size(), attrs.lastModifiedTime().toMillis(), key)
    }.getOrNull()

    fun shouldAttempt(advancedFile: File): Boolean =
        identity(advancedFile)?.let { it != failedAdvanced } ?: true

    fun recordFailure(advancedFile: File) {
        failedAdvanced = identity(advancedFile)
    }
}
