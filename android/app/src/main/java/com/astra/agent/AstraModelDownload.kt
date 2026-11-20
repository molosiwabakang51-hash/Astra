package com.astra.agent

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * Optional one-time, resumable HTTPS download of public GGUF weights.
 *
 * No prompts or private data are uploaded. Network is used ONLY to obtain
 * the open GGUF model file. After SHA-256 verification, all inference is local.
 * Partial bytes are retained on cancellation so data is not redownloaded.
 */
internal object AstraModelDownload {
    private const val MODEL_URL =
        "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
    private const val MIN_BYTES = 1_150_000_000L
    private const val MAX_BYTES = 1_350_000_000L
    private const val HEADROOM = 64L * 1024L * 1024L

    suspend fun download(
        context: Context,
        onProgress: suspend (downloaded: Long, expected: Long) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        val target = AstraModelImport.advancedFile(context)
        val folder = target.parentFile ?: error("Offline model storage unavailable.")
        require(folder.isDirectory || folder.mkdirs()) { "Cannot create offline model storage." }

        // A correctly installed model needs no network call at all.
        if (target.isFile && target.length() in MIN_BYTES..MAX_BYTES &&
            sha256(target) == AstraModelImport.ADVANCED_SHA256
        ) {
            onProgress(target.length(), target.length())
            return@withContext target.length()
        }
        val partial = File(folder, AstraModelImport.ADVANCED_NAME + ".downloading")
        if (partial.isFile && partial.length() > MAX_BYTES) {
            check(partial.delete()) { "Cannot clear oversized incomplete download." }
        }
        // Process death may happen after all bytes are saved but before the
        // verified GGUF is atomically installed. Recover it before even
        // opening an HTTPS connection (no redownload/data use required).
        val recoveredBytes = AstraCompletedModelDownload.promoteIfVerified(
            partial, target, MIN_BYTES, MAX_BYTES,
            AstraModelImport.ADVANCED_SHA256, ::sha256
        )
        if (recoveredBytes != null) {
            onProgress(recoveredBytes, recoveredBytes)
            return@withContext recoveredBytes
        }
        val offset = partial.takeIf { it.isFile }?.length() ?: 0L
        val connection = URL(MODEL_URL).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 25_000
            connection.readTimeout = 35_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "Astra-Android-offline-model-setup")
            if (offset > 0L) connection.setRequestProperty("Range", "bytes=$offset-")
            val status = connection.responseCode
            if (status == 416 && offset in MIN_BYTES..MAX_BYTES) {
                // Completed bytes may have survived a crash before final rename.
                if (sha256(partial) != AstraModelImport.ADVANCED_SHA256) {
                    // A complete but invalid partial cannot be resumed; discard
                    // it so the next user-approved attempt starts cleanly.
                    check(partial.delete()) {
                        "Corrupted offline download could not be cleared."
                    }
                    error("The saved download failed SHA-256. Please retry.")
                }
                AstraModelStorage.installVerified(partial, target)
                onProgress(target.length(), target.length())
                return@withContext target.length()
            }
            val transfer = AstraDownloadRange.plan(
                status, offset, connection.getHeaderField("Content-Range"),
                connection.contentLengthLong
            )
            require(transfer.totalBytes in MIN_BYTES..MAX_BYTES) {
                "The model server returned an unexpected GGUF size."
            }
            val usedBytes = if (transfer.append) offset else 0L
            require(StatFs(folder.absolutePath).availableBytes >=
                transfer.totalBytes - usedBytes + HEADROOM) {
                "Insufficient storage to finish the offline model download."
            }

            currentCoroutineContext().ensureActive()
            connection.inputStream.use { input ->
                FileOutputStream(partial, transfer.append).use { output ->
                    // A provider or TLS input stream returning 0 must not
                    // create an unbounded CPU loop. A recoverable failure
                    // retains the partial file for the next ranged request.
                    copyResponse(
                        input, output, usedBytes, transfer.totalBytes, onProgress
                    )
                    currentCoroutineContext().ensureActive()
                    output.fd.sync()
                }
            }
            require(partial.length() == transfer.totalBytes) {
                "The model download stopped early. Start again to resume."
            }
            if (sha256(partial) != AstraModelImport.ADVANCED_SHA256) {
                // Never install corrupt bytes or keep retrying a complete
                // checksum-mismatched file indefinitely.
                check(partial.delete()) { "Cannot clear corrupted model download." }
                error("Downloaded GGUF failed SHA-256 verification. Please retry.")
            }
            AstraModelStorage.installVerified(partial, target)
            onProgress(target.length(), target.length())
            target.length()
        } catch (cancel: CancellationException) {
            throw cancel
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Copy a response body with bounded progress and cancellation. A 0-byte
     * bulk read is neither EOF nor useful progress; retry up to 16 times
     * before surfacing a resumable error. Tested without an Android network.
     */
    internal suspend fun copyResponse(
        input: InputStream,
        output: OutputStream,
        initialBytes: Long,
        expectedBytes: Long,
        onProgress: suspend (Long, Long) -> Unit
    ): Long {
        require(initialBytes >= 0L && expectedBytes >= initialBytes &&
            expectedBytes <= MAX_BYTES) {
            "Invalid expected offline model transfer size."
        }
        val data = ByteArray(1024 * 1024)
        var total = initialBytes
        var lastUpdate = initialBytes
        var consecutiveEmptyReads = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(data)
            if (count < 0) break
            if (count == 0) {
                require(++consecutiveEmptyReads <= 16) {
                    "Model server stopped sending data. Tap Download once " +
                        "again to resume without starting over."
                }
                continue
            }
            consecutiveEmptyReads = 0
            total += count
            require(total <= expectedBytes && total <= MAX_BYTES) {
                "The model download exceeded its declared size."
            }
            output.write(data, 0, count)
            if (total - lastUpdate >= 16L * 1024 * 1024) {
                onProgress(total, expectedBytes)
                lastUpdate = total
            }
        }
        currentCoroutineContext().ensureActive()
        return total
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val scope = currentCoroutineContext()
        file.inputStream().buffered().use { input ->
            val block = ByteArray(1024 * 1024)
            while (true) {
                scope.ensureActive()
                val read = input.read(block)
                if (read < 0) break
                digest.update(block, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
