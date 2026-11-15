package com.astra.agent

import java.io.File

/**
 * Session-local backoff for an advanced GGUF that failed checksum preparation
 * or native loading. Keep using a working compact model instead of paying the
 * 1.7B load penalty on every chat turn. Changing/re-importing the model file
 * permits a retry; restarting the app also clears this failure record.
 *
 * The file identity is private to the process and never enters diagnostics.
 */
internal class AstraModelHealth {
    private data class Identity(val path: String, val size: Long, val modified: Long)
    private var failedAdvanced: Identity? = null

    private fun identity(file: File): Identity? =
        file.takeIf { it.isFile }?.let {
            Identity(it.absolutePath, it.length(), it.lastModified())
        }

    fun shouldAttempt(advancedFile: File): Boolean =
        identity(advancedFile)?.let { it != failedAdvanced } ?: true

    fun recordFailure(advancedFile: File) {
        failedAdvanced = identity(advancedFile)
    }
}
