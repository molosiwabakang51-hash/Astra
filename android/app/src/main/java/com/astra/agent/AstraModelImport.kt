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
    private const val MAX_BYTES = 1_400_000_000L
    private const val ADVANCED_MIN_BYTES = 1_150_000_000L
    private const val ADVANCED_MAX_BYTES = 1_350_000_000L
    const val ADVANCED_NAME = "Qwen3-1.7B-Q4_K_M.gguf"
    // Official ggml-org Qwen3-1.7B Q4_K_M GGUF SHA-256.
    const val ADVANCED_SHA256 = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
    private const val FREE_HEADROOM = 64L * 1024 * 1024
    private const val ASSET_PATH = "assets/" + AstraOnDeviceBrain.ASSET_NAME

    fun modelFile(context: Context): File =
        AstraModelStorage.activeModel(
            File(context.noBackupFilesDir, "offline-models"),
            AstraOnDeviceBrain.ASSET_NAME
        )

    fun advancedFile(context: Context): File =
        AstraModelStorage.activeModel(
            File(context.noBackupFilesDir, "offline-models"), ADVANCED_NAME
        )

    fun advancedInstalled(context: Context): Boolean =
        advancedFile(context).let {
            it.isFile && it.length() in ADVANCED_MIN_BYTES..ADVANCED_MAX_BYTES
        }

    fun appearsInstalled(context: Context): Boolean =
        modelFile(context).let { it.isFile && it.length() in MIN_BYTES..600_000_000L } ||
            advancedInstalled(context)

    suspend fun importDocument(
        context: Context,
        uri: Uri,
        onProgress: suspend (Long) -> Unit = {}
    ): Long = withContext(Dispatchers.IO) {
        val dir = modelFile(context).parentFile ?: throw IllegalStateException("Model storage unavailable.")
        require(dir.isDirectory || dir.mkdirs()) { "Cannot create model storage." }
        require(StatFs(dir.absolutePath).availableBytes >= MAX_BYTES + FREE_HEADROOM) {
            "Not enough free phone space. Keep at least 1.5 GB available."
        }

        val partial = File(dir, "qwen3-model.importing")
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
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val advanced = when {
                total in ADVANCED_MIN_BYTES..ADVANCED_MAX_BYTES &&
                    hash.equals(ADVANCED_SHA256, ignoreCase = true) -> true
                total in MIN_BYTES..600_000_000L &&
                    hash.equals(AstraOnDeviceBrain.MODEL_SHA256, ignoreCase = true) -> false
                else -> error("Unsupported model or checksum mismatch. Import an exact verified Qwen3 GGUF.")
            }
            val target = if (advanced) advancedFile(context) else modelFile(context)
            // Preserve the previously working GGUF until the fully verified
            // replacement is installed. A process death during the final
            // renames is recoverable at the next model-file lookup.
            AstraModelStorage.installVerified(partial, target)
            total
        } finally {
            if (partial.exists()) partial.delete()
        }
    }

    // Exposed within the module so JVM tests can validate nested APK ZIP
    // extraction with small fixtures, without a 462 MiB test model.
    internal suspend fun copyFromArchive(
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
                    name.endsWith("/" + ASSET_PATH) ||
                    name == ADVANCED_NAME || name.endsWith("/" + ADVANCED_NAME))) {
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
