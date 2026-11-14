package com.astra.agent

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Import a GGUF model from local storage once.
 *
 * Accepts the original Qwen3 GGUF, an old Astra APK containing the model,
 * or a GitHub artifact ZIP containing that APK. Nothing is downloaded.
 * The imported weights stay in the app's no-backup storage across upgrades
 * signed with the same key. The selected file in Downloads is not modified.
 */
internal object AstraModelImport {
    private const val MIN_BYTES = 450_000_000L
    private const val MAX_BYTES = 600_000_000L
    private const val FREE_HEADROOM = 64L * 1024 * 1024
    private const val ASSET_PATH = "assets/" + AstraOnDeviceBrain.ASSET_NAME

    fun modelFile(context: Context): File =
        File(File(context.noBackupFilesDir, "offline-models"), AstraOnDeviceBrain.ASSET_NAME)

    fun appearsInstalled(context: Context): Boolean =
        modelFile(context).let { it.isFile && it.length() >= MIN_BYTES }

    suspend fun importDocument(
        context: Context,
        uri: Uri,
        onProgress: suspend (Long) -> Unit = {}
    ): Long = withContext(Dispatchers.IO) {
        val target = modelFile(context)
        val dir = target.parentFile ?: throw IllegalStateException("Model storage unavailable.")
        require(dir.isDirectory || dir.mkdirs()) { "Cannot create model storage." }
        require(StatFs(dir.absolutePath).availableBytes >= MAX_BYTES + FREE_HEADROOM) {
            "Not enough free phone space. Keep at least 665 MB available."
        }

        val partial = File(dir, "${AstraOnDeviceBrain.ASSET_NAME}.importing")
        partial.delete()
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open selected model document.")
            val digest = MessageDigest.getInstance("SHA-256")
            val total = input.use { source ->
                BufferedInputStream(source, 64 * 1024).use { buffered ->
                    val signature = ByteArray(4)
                    buffered.mark(4)
                    val signatureSize = buffered.read(signature)
                    buffered.reset()
                    FileOutputStream(partial).use { output ->
                        val bytes = if (
                            signatureSize == 4 &&
                            signature[0] == 'P'.code.toByte() &&
                            signature[1] == 'K'.code.toByte()
                        ) {
                            // ZIP/APK: process directly as a stream, without
                            // extracting a second 500 MB archive to storage.
                            copyFromArchive(
                                ZipInputStream(buffered), output, digest, 0, onProgress
                            )
                        } else {
                            require(signatureSize == 4 &&
                                signature.contentEquals("GGUF".toByteArray(Charsets.US_ASCII))) {
                                "Select Qwen3 GGUF, a previous Astra APK or its GitHub ZIP."
                            }
                            copyBounded(buffered, output, digest, onProgress)
                        }
                        output.fd.sync()
                        bytes
                    }
                }
            }
            require(total in MIN_BYTES..MAX_BYTES &&
                digest.digest().joinToString("") { "%02x".format(it) }
                    .equals(AstraOnDeviceBrain.MODEL_SHA256, ignoreCase = true)
            ) { "This is not Astra's expected Qwen3 model (size or SHA-256 mismatch)." }
            // Do not replace a working model until the new file is verified.
            if (target.isFile && !target.delete()) {
                error("Cannot replace previously installed model.")
            }
            require(partial.renameTo(target)) { "Unable to finalize imported model." }
            total
        } finally {
            if (partial.exists()) partial.delete()
        }
    }

    private suspend fun copyFromArchive(
        zip: ZipInputStream,
        out: OutputStream,
        digest: MessageDigest,
        depth: Int,
        onProgress: suspend (Long) -> Unit
    ): Long {
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = zip.nextEntry ?: break
            val name = entry.name.replace('\\', '/').trimStart('/')
            if (!entry.isDirectory && (name == ASSET_PATH ||
                    name == AstraOnDeviceBrain.ASSET_NAME ||
                    name.endsWith("/" + ASSET_PATH))) {
                return copyBounded(zip, out, digest, onProgress)
            }
            // GitHub gives an outer ZIP containing the APK. This handles it
            // without needing to save/extract the APK first.
            if (depth == 0 && !entry.isDirectory &&
                name.endsWith(".apk", ignoreCase = true)) {
                val nested = copyFromArchive(
                    ZipInputStream(zip), out, digest, depth + 1, onProgress
                )
                if (nested > 0L) return nested
            }
            zip.closeEntry()
        }
        if (depth == 0) error("No bundled Qwen3 GGUF found in the selected archive.")
        return 0L
    }

    private suspend fun copyBounded(
        input: InputStream,
        out: OutputStream,
        digest: MessageDigest,
        onProgress: suspend (Long) -> Unit
    ): Long {
        val buffer = ByteArray(1024 * 1024)
        var total = 0L
        var nextUpdate = 32L * 1024 * 1024
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BYTES) { "Selected model exceeds safe size limit." }
            digest.update(buffer, 0, count)
            out.write(buffer, 0, count)
            if (total >= nextUpdate) {
                onProgress(total)
                nextUpdate = total + 32L * 1024 * 1024
            }
        }
        return total
    }
}
