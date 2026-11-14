package com.astra.agent

import android.content.Context
import android.os.StatFs
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Qwen3 inference runs entirely on the Android device.
 *
 * CI bundles the verified GGUF inside the APK. At first use it is copied to
 * app-private no-backup storage for llama.cpp's file-path API; no network
 * request, separate computer, account or model server is involved.
 */
internal class AstraOnDeviceBrain(private val context: Context) {
    data class Turn(val role: String, val content: String)

    companion object {
        const val ASSET_NAME = "qwen3-0.6b-q4km.gguf"
        const val MODEL_SHA256 =
            "7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d"
        const val MODEL_LABEL = "Qwen3 0.6B · Q4_K_M · offline"
        private const val MIN_SIZE_BYTES = 450_000_000L
        private const val DISK_HEADROOM_BYTES = 64L * 1024 * 1024
    }

    private val mutex = Mutex()
    private var engine: InferenceEngine? = null
    private var loadedGeneration: Int? = null
    private var verifiedFile: File? = null

    fun isModelBundled(): Boolean =
        try {
            context.assets.list("")?.contains(ASSET_NAME) == true
        } catch (_: Exception) {
            false
        }

    suspend fun reply(turns: List<Turn>, conversationGeneration: Int): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val finalMessage = turns.lastOrNull { it.role == "user" }?.content
                    ?.trim()?.take(3000).orEmpty()
                require(finalMessage.isNotEmpty()) { "Message cannot be empty." }
                val modelPath = ensureBundledModel()
                var current = engine
                if (current == null) {
                    current = AiChat.getInferenceEngine(context.applicationContext)
                    engine = current
                }
                if (loadedGeneration != conversationGeneration) {
                    // The upstream llama.cpp binding retains chat context in memory.
                    // Unload when changing chats so private conversations cannot mix.
                    if (loadedGeneration != null) current.cleanUp()
                    current.loadModel(modelPath.absolutePath)
                    val recent = turns.dropLast(1).takeLast(8)
                        .filter { it.role in setOf("user", "assistant") }
                        .joinToString("\n") {
                            (if (it.role == "user") "User" else "Astra") +
                                ": " + it.content.trim().take(550)
                        }
                    current.setSystemPrompt(buildString {
                        append("You are Astra, a concise, helpful Android assistant. ")
                        append("You run fully offline on this phone. ")
                        append("Do not pretend to browse the internet, know today's live prices, ")
                        append("or control apps. State uncertainty. Do not reveal chain-of-thought. ")
                        if (recent.isNotBlank()) {
                            append("\nRecent conversation for context:\n")
                            append(recent)
                        }
                    })
                    loadedGeneration = conversationGeneration
                }

                val answer = StringBuilder()
                current.sendUserPrompt(finalMessage, 384).collect { part ->
                    // Bound memory even if a backend unexpectedly emits too much text.
                    if (answer.length + part.length <= 16_000) answer.append(part)
                }
                answer.toString().trim().ifBlank {
                    "The offline model did not produce an answer. Please try again."
                }
            }
        }

    private fun ensureBundledModel(): File {
        verifiedFile?.takeIf { it.isFile }?.let { return it }
        val dir = File(context.noBackupFilesDir, "offline-models")
        require(dir.isDirectory || dir.mkdirs()) { "Cannot create private model storage." }
        val target = File(dir, ASSET_NAME)
        if (target.exists() && target.length() >= MIN_SIZE_BYTES &&
            sha256(target).equals(MODEL_SHA256, ignoreCase = true)
        ) {
            verifiedFile = target
            return target
        }
        if (target.exists()) target.delete()
        val free = StatFs(dir.absolutePath).availableBytes
        require(free >= MIN_SIZE_BYTES + DISK_HEADROOM_BYTES) {
            "Not enough free phone storage for Astra's bundled offline model."
        }

        val temp = File(dir, "$ASSET_NAME.partial")
        temp.delete()
        try {
            context.assets.open(ASSET_NAME).use { source ->
                FileOutputStream(temp).use { sink ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                    }
                    sink.fd.sync()
                }
            }
            require(temp.length() >= MIN_SIZE_BYTES &&
                sha256(temp).equals(MODEL_SHA256, ignoreCase = true)
            ) { "The bundled model failed its integrity check." }
            require(temp.renameTo(target)) { "Could not prepare the bundled model." }
            verifiedFile = target
            return target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val block = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                digest.update(block, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
