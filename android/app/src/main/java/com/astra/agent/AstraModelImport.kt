package com.astra.agent

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
 * Accepts only the pinned Qwen3 1.7B GGUF, or an APK/artifact ZIP containing
 * that model. Obsolete 0.6B imports are rejected without deleting old data.
 * The imported weights stay in the app's no-backup storage across upgrades
 * signed with the same key. The selected file in Downloads is not modified.
 */
internal object AstraModelImport {
    private const val MIN_BYTES = 1_150_000_000L
    private const val MAX_BYTES = 1_350_000_000L
    private const val ADVANCED_MIN_BYTES = 1_150_000_000L
    private const val ADVANCED_MAX_BYTES = 1_350_000_000L
    const val ADVANCED_NAME = "Qwen3-1.7B-Q4_K_M.gguf"
    // Official ggml-org Qwen3-1.7B Q4_K_M GGUF SHA-256.
    const val ADVANCED_SHA256 = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
    private const val FREE_HEADROOM = 64L * 1024 * 1024
    // Prevent untrusted ZIPs with enormous entry tables from keeping the
    // importer busy indefinitely before reaching a real model asset.
    private const val MAX_ARCHIVE_ENTRIES = 4_096
    private const val ASSET_PATH = "assets/" + ADVANCED_NAME

    /** The required 1.7B model must fit before a large import begins. */
    internal fun hasImportStartingSpace(availableBytes: Long): Boolean =
        availableBytes >= MIN_BYTES + FREE_HEADROOM

    /**
     * For an ordinary GGUF SAF document, Android may report its size upfront.
     * Reject known impossible imports before reading 1.2 GB into a partial file.
     * Unknown or unreliable provider sizes fall back to streamed headroom
     * checks; ZIP/APK imports remain streamed because compressed size is not
     * a reliable estimate of the extracted model's space requirement.
     */
    internal fun hasSpaceForKnownGguf(availableBytes: Long, sizeBytes: Long): Boolean =
        sizeBytes in ADVANCED_MIN_BYTES..ADVANCED_MAX_BYTES &&
            availableBytes >= maxOf(MIN_BYTES, sizeBytes) + FREE_HEADROOM

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

    fun appearsInstalled(context: Context): Boolean = advancedInstalled(context)

    suspend fun importDocument(
        context: Context,
        uri: Uri,
        onProgress: suspend (Long) -> Unit = {}
    ): Long = withContext(Dispatchers.IO) {
        val dir = advancedFile(context).parentFile ?: throw IllegalStateException("Model storage unavailable.")
        require(dir.isDirectory || dir.mkdirs()) { "Cannot create model storage." }
        // A smaller GGUF is no longer accepted. Reserve enough room for the
        // pinned 1.7B model, then enforce headroom during streamed copying.
        require(hasImportStartingSpace(StatFs(dir.absolutePath).availableBytes)) {
            "Not enough free phone space to start a model import. " +
                "Free at least 1.22 GB for Qwen3 1.7B."
        }
        val safeProgress: suspend (Long) -> Unit = { copied ->
            require(StatFs(dir.absolutePath).availableBytes >= FREE_HEADROOM) {
                "Insufficient free phone storage to finish importing the model safely."
            }
            onProgress(copied)
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
                    var signatureSize = 0
                    var stalledHeaderReads = 0
                    // OpenDocument providers may return a short first read.
                    // Assemble all four signature bytes before deciding that
                    // a valid GGUF or APK is an unsupported file.
                    while (signatureSize < signature.size) {
                        val count = buffered.read(
                            signature, signatureSize, signature.size - signatureSize
                        )
                        if (count < 0) break
                        if (count == 0) {
                            require(++stalledHeaderReads <= 16) {
                                "Selected model file provider stopped responding."
                            }
                        } else {
                            signatureSize += count
                            stalledHeaderReads = 0
                        }
                    }
                    buffered.reset()
                    val isArchive = signatureSize == 4 &&
                        signature[0] == 'P'.code.toByte() &&
                        signature[1] == 'K'.code.toByte()
                    val isGguf = signatureSize == 4 &&
                        signature.contentEquals("GGUF".toByteArray(Charsets.US_ASCII))
                    if (isGguf) {
                        // A SAF provider can omit SIZE. Never trust it as an
                        // integrity check; SHA-256 is still required after copy.
                        val declaredBytes = runCatching {
                            context.contentResolver.query(
                                uri, arrayOf(OpenableColumns.SIZE), null, null, null
                            )?.use { cursor ->
                                val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (column >= 0 && cursor.moveToFirst() &&
                                    !cursor.isNull(column)
                                ) cursor.getLong(column).takeIf { it > 0L } else null
                            }
                        }.getOrNull()
                        if (declaredBytes != null) {
                            require(declaredBytes in
                                ADVANCED_MIN_BYTES..ADVANCED_MAX_BYTES
                            ) {
                                "Selected GGUF is ${declaredBytes / 1048576L} MiB; " +
                                    "the verified Qwen3 1.7B Q4_K_M model is " +
                                    "approximately 1.2 GiB. Your file may be " +
                                    "incomplete, a different quantization, or 0.6B."
                            }
                            val available = StatFs(dir.absolutePath).availableBytes
                            require(hasSpaceForKnownGguf(available, declaredBytes)) {
                                val required = maxOf(MIN_BYTES, declaredBytes) + FREE_HEADROOM
                                "Not enough phone storage for this local GGUF. " +
                                    "Need at least ${(required + 1048575) / 1048576} MiB free; " +
                                    "currently ${available / 1048576} MiB free. " +
                                    "The selected model was not modified."
                            }
                        }
                    }
                    FileOutputStream(partial).use { output ->
                        val bytes = if (isArchive) {
                            // ZIP/APK: process directly as a stream, without
                            // extracting a second 500 MB archive to storage.
                            copyFromArchive(
                                ZipInputStream(buffered), output, digest, 0, safeProgress
                            )
                        } else {
                            require(isGguf) {
                                "Select Qwen3 1.7B GGUF, or an Astra APK/ZIP that bundles 1.7B."
                            }
                            copyBounded(buffered, output, digest, safeProgress)
                        }
                        output.fd.sync()
                        bytes
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(total in ADVANCED_MIN_BYTES..ADVANCED_MAX_BYTES &&
                hash.equals(ADVANCED_SHA256, ignoreCase = true)) {
                "Only the official verified Qwen3 1.7B Q4_K_M GGUF is supported. " +
                    "The older 0.6B model cannot be used for conversation."
            }
            val target = advancedFile(context)
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
    ): Long = copyFromArchiveBudgeted(
        zip, out, digest, depth, onProgress, intArrayOf(0)
    )

    // One budget covers the outer artifact and every nested APK. A per-ZIP
    // counter would let a malicious artifact multiply the 4096-entry limit
    // by packing thousands of small APKs into its outer ZIP.
    private suspend fun copyFromArchiveBudgeted(
        zip: ZipInputStream,
        out: OutputStream,
        digest: MessageDigest,
        depth: Int,
        onProgress: suspend (Long) -> Unit,
        examined: IntArray
    ): Long {
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = zip.nextEntry ?: break
            require(++examined[0] <= MAX_ARCHIVE_ENTRIES) {
                "Selected archive has too many entries to inspect safely."
            }
            val name = entry.name.replace('\\', '/').trimStart('/')
            if (!entry.isDirectory && (name == ASSET_PATH ||
                    name.endsWith("/" + ASSET_PATH) ||
                    name == ADVANCED_NAME || name.endsWith("/" + ADVANCED_NAME))) {
                return copyBounded(zip, out, digest, onProgress)
            }
            // GitHub gives an outer ZIP containing the APK. This handles it
            // without needing to save/extract the APK first.
            if (depth == 0 && !entry.isDirectory &&
                name.endsWith(".apk", ignoreCase = true)) {
                val nested = copyFromArchiveBudgeted(
                    ZipInputStream(zip), out, digest, depth + 1, onProgress, examined
                )
                if (nested > 0L) return nested
            }
            zip.closeEntry()
        }
        if (depth == 0) error("No bundled Qwen3 GGUF found in the selected archive.")
        return 0L
    }

    internal suspend fun copyBounded(
        input: InputStream,
        out: OutputStream,
        digest: MessageDigest,
        onProgress: suspend (Long) -> Unit
    ): Long {
        val buffer = ByteArray(1024 * 1024)
        var total = 0L
        var nextUpdate = 32L * 1024 * 1024
        var consecutiveEmptyReads = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                // A faulty SAF provider can return zero indefinitely instead
                // of EOF. Fail safely rather than spin on the IO dispatcher.
                require(++consecutiveEmptyReads <= 16) {
                    "Selected model document stopped making read progress. Retry with a local file."
                }
                continue
            }
            consecutiveEmptyReads = 0
            total += count
            require(total <= MAX_BYTES) { "Selected model exceeds safe size limit." }
            digest.update(buffer, 0, count)
            out.write(buffer, 0, count)
            if (total >= nextUpdate) {
                onProgress(total)
                nextUpdate = total + 32L * 1024 * 1024
            }
        }
        // Recheck remaining disk space after the final, potentially partial
        // 32 MiB interval, before promoting the verified new model file.
        if (total > 0L) onProgress(total)
        return total
    }
}
