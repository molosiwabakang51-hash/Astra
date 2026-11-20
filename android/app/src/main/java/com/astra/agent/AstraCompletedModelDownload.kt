package com.astra.agent

import java.io.File

/**
 * A complete verified download can survive a process kill immediately before
 * installation. Promote it locally before opening a new HTTPS connection.
 *
 * The official GGUF SHA must match exactly. A size-compatible partial with a
 * mismatching checksum may simply be incomplete; leave it for ranged resume,
 * rather than wasting the owner's downloaded bytes.
 */
internal object AstraCompletedModelDownload {
    suspend fun promoteIfVerified(
        partial: File,
        target: File,
        minBytes: Long,
        maxBytes: Long,
        expectedSha256: String,
        checksum: suspend (File) -> String
    ): Long? {
        require(minBytes > 0L && maxBytes >= minBytes) {
            "Invalid offline model size bounds."
        }
        require(Regex("[a-fA-F0-9]{64}").matches(expectedSha256)) {
            "Invalid official model checksum."
        }
        if (!partial.isFile || partial.length() !in minBytes..maxBytes) {
            return null
        }
        if (!checksum(partial).equals(expectedSha256, ignoreCase = true)) {
            // This can be a valid unfinished ranged download. Keep the bytes
            // and do not overwrite a previously working installed GGUF.
            return null
        }
        // installVerified preserves a previous model if the rename fails,
        // and an interrupted rename is recovered by activeModel at launch.
        AstraModelStorage.installVerified(partial, target)
        return target.length()
    }
}
