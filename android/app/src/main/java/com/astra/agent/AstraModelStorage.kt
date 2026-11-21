package com.astra.agent

import java.io.File

/**
 * Recoverable private model replacement.
 *
 * A model import is verified BEFORE calling installVerified. The previous
 * GGUF is kept under a separate backup name until the new file is in place.
 * If Android kills the app between the two renames, activeModel restores
 * the old copy during the next launch. No cloud storage is involved.
 */
internal object AstraModelStorage {
    private fun backupFor(target: File): File =
        File(target.parentFile, target.name + ".before-import")

    fun activeModel(directory: File, fileName: String): File {
        val target = File(directory, fileName)
        val backup = backupFor(target)
        if (!target.isFile && backup.isFile) {
            check(backup.renameTo(target)) {
                "Cannot restore the previous offline model after an interrupted import."
            }
        }
        return target
    }

    fun installVerified(verifiedTemporary: File, target: File) {
        require(verifiedTemporary.isFile) {
            "Verified model file is not available for installation."
        }
        val backup = backupFor(target)
        // The backup is the only recoverable copy after a crash between
        // renames. Never accept it as an import source: renaming the current
        // target over that same file could destroy both the backup and the
        // supposed verified replacement. Canonical paths also reject aliases
        // such as ".." and symlinks within private storage.
        val sourcePath = verifiedTemporary.canonicalPath
        require(sourcePath != target.canonicalPath) {
            "Cannot install a model over itself."
        }
        require(sourcePath != backup.canonicalPath) {
            "Cannot use the model recovery backup as an import source."
        }
        // Any backup left by a previous successful import is superseded by
        // the currently installed target, not by an unverified temporary file.
        if (target.isFile && backup.exists()) {
            check(backup.delete()) { "Cannot clear previous model backup." }
        }
        if (target.isFile) {
            check(target.renameTo(backup)) {
                "Cannot preserve the existing offline model before replacement."
            }
        }
        if (!verifiedTemporary.renameTo(target)) {
            if (backup.isFile && !target.exists()) {
                check(backup.renameTo(target)) {
                    "Model update failed and previous model could not be restored."
                }
            }
            error("Unable to finalize the verified offline model.")
        }
        // If deleting the backup fails, it is safe to leave it for cleanup
        // on the next import; the newly verified target is already installed.
        if (backup.isFile) backup.delete()
    }
}
