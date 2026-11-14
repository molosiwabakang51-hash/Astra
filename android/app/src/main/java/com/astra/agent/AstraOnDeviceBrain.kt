package com.astra.agent

import android.content.Context
import android.os.StatFs
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val session = AstraInferenceSession()
    private var verifiedFile: File? = null
    private var loadedModelPath: String? = null

    fun hasLocalModel(): Boolean = isModelBundled() ||
        AstraModelImport.appearsInstalled(context)

    fun isModelBundled(): Boolean =
        isBundled(AstraModelImport.ADVANCED_NAME) || isBundled(ASSET_NAME)

    fun hasAdvancedModel(): Boolean =
        AstraModelImport.advancedInstalled(context) ||
            isBundled(AstraModelImport.ADVANCED_NAME)

    private fun isBundled(name: String): Boolean =
        try {
            context.assets.list("")?.contains(name) == true
        } catch (_: Exception) {
            false
        }

    suspend fun reply(
        turns: List<Turn>,
        conversationGeneration: Int,
        onPartial: suspend (String) -> Unit = {}
    ): String = mutex.withLock {
            try {
                withContext(Dispatchers.IO) {
                val finalMessage = turns.lastOrNull { it.role == "user" }?.content
                    ?.trim()?.take(3000).orEmpty()
                require(finalMessage.isNotEmpty()) { "Message cannot be empty." }
                if (verifiedFile == null) {
                    onPartial("Verifying the local offline model...")
                }
                val modelPath = when (AstraModelChoice.choose(
                    advancedAvailable = AstraModelImport.advancedInstalled(context) ||
                        isBundled(AstraModelImport.ADVANCED_NAME),
                    compactAvailable = AstraModelImport.modelFile(context).isFile ||
                        isBundled(ASSET_NAME)
                )) {
                    AstraModelChoice.Variant.ADVANCED -> ensureModel(
                        AstraModelImport.ADVANCED_NAME,
                        AstraModelImport.ADVANCED_SHA256,
                        1_150_000_000L
                    )
                    AstraModelChoice.Variant.COMPACT -> ensureModel(
                        ASSET_NAME, MODEL_SHA256, MIN_SIZE_BYTES
                    )
                    AstraModelChoice.Variant.NONE -> error(
                        "No local language model installed. Open Settings to get the " +
                            "free Qwen3 1.7B model and import it once."
                    )
                }
                var current = engine
                if (current == null) {
                    current = AiChat.getInferenceEngine(context.applicationContext)
                    engine = current
                }
                // Upstream initializes JNI asynchronously. Wait for the engine
                // to be ready instead of racing model loading against init.
                val initialized = withTimeout(30_000L) {
                    current.state.first { state ->
                        state is InferenceEngine.State.Initialized ||
                            state is InferenceEngine.State.ModelReady ||
                            state is InferenceEngine.State.Error
                    }
                }
                if (initialized is InferenceEngine.State.Error) {
                    // JNI exposes Error as a resettable state. The next load
                    // must start with a clean native context.
                    current.cleanUp()
                    session.invalidate()
                }
                var restoringConversation = false
                if (session.requiresLoad(
                        conversationGeneration,
                        current.state.value is InferenceEngine.State.ModelReady
                    ) || loadedModelPath != modelPath.absolutePath
                ) {
                    onPartial("Loading the language model on this phone...")
                    // The native engine retains chat context. Always unload
                    // between conversations, including activity recreation.
                    if (current.state.value is InferenceEngine.State.ModelReady) {
                        current.cleanUp()
                    }
                    current.loadModel(modelPath.absolutePath)
                    loadedModelPath = modelPath.absolutePath
                    // Never place previous user text or model replies inside the
                    // higher-priority system prompt. Reloaded sessions receive
                    // quoted history once through a bounded user-level prompt.
                    current.setSystemPrompt(AstraConversationContext.systemPrompt())
                    restoringConversation = true
                    session.markLoaded(conversationGeneration)
                }

                val answer = StringBuilder()
                var lastUiUpdate = 0L
                val requestPrompt = AstraConversationContext.userPrompt(
                    finalMessage,
                    turns.dropLast(1).map {
                        AstraConversationContext.Turn(it.role, it.content)
                    },
                    restoringConversation
                )
                current.sendUserPrompt(requestPrompt, 384).collect { part ->
                    // Restrict accumulated output and cap main-thread UI updates.
                    val space = (16_000 - answer.length).coerceAtLeast(0)
                    if (space > 0) answer.append(part.take(space))
                    val now = System.nanoTime()
                    if (now - lastUiUpdate >= 200_000_000L) {
                        val partial = AstraOfflinePolicy.publicReply(answer.toString())
                        if (partial.isNotBlank()) onPartial(partial)
                        lastUiUpdate = now
                    }
                }
                AstraOfflinePolicy.publicReply(answer.toString()).ifBlank {
                    // Empty native generation is not a completed conversational
                    // turn. Force a clean reload before another user request.
                    session.invalidate()
                    "The offline model did not produce a usable answer. Please try again."
                }
                }
            } catch (error: Exception) {
                // Cancellation, JNI failures and timeouts can leave partial
                // tokens in the model's native KV-cache. A later request must
                // not inherit that incomplete assistant response.
                session.invalidate()
                loadedModelPath = null
                throw error
            }
        }

    /**
     * The strongest installed model is always selected. The 0.6B weights
     * remain a compatibility fallback; switching models never uses a server.
     * An app-private imported GGUF survives correctly signed app upgrades.
     */
    private suspend fun ensureModel(
        name: String,
        expectedSha256: String,
        minimumBytes: Long
    ): File {
        val workContext = currentCoroutineContext()
        verifiedFile?.takeIf { it.isFile && it.name == name }?.let { return it }
        val directory = File(context.noBackupFilesDir, "offline-models")
        require(directory.isDirectory || directory.mkdirs()) {
            "Cannot create private model storage."
        }
        val target = AstraModelStorage.activeModel(directory, name)
        if (target.isFile && target.length() >= minimumBytes &&
            sha256(target).equals(expectedSha256, ignoreCase = true)
        ) {
            verifiedFile = target
            return target
        }
        if (!isBundled(name)) {
            error(
                "Offline model missing or failed SHA-256 verification. " +
                    "Open Settings to import the official Qwen3 GGUF again."
            )
        }
        val requiredBytes = if (name == AstraModelImport.ADVANCED_NAME)
            1_350_000_000L else MIN_SIZE_BYTES
        require(StatFs(directory.absolutePath).availableBytes >=
            requiredBytes + DISK_HEADROOM_BYTES
        ) { "Not enough free phone storage to prepare the local language model." }

        val temporary = File(directory, "$name.partial")
        temporary.delete()
        try {
            context.assets.open(name).use { source ->
                FileOutputStream(temporary).use { sink ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        workContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                    }
                    workContext.ensureActive()
                    sink.fd.sync()
                }
            }
            require(temporary.length() >= minimumBytes &&
                sha256(temporary).equals(expectedSha256, ignoreCase = true)
            ) { "Bundled language model failed SHA-256 integrity verification." }
            AstraModelStorage.installVerified(temporary, target)
            verifiedFile = target
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private suspend fun sha256(file: File): String {
        val workContext = currentCoroutineContext()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val block = ByteArray(1024 * 1024)
            while (true) {
                workContext.ensureActive()
                val count = input.read(block)
                if (count < 0) break
                digest.update(block, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
