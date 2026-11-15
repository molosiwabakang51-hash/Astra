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
internal class AstraOnDeviceBrain(
    private val context: Context,
    private val learning: AstraContinualLearning? = null,
    private val books: AstraBookLibrary? = null
) {
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
    @Volatile
    private var loadedModelPath: String? = null
    // A change of consent must not leave old book snippets in native KV-cache.
    private var previousBookContextAllowed: Boolean? = null

    /**
     * Only reports the last model successfully loaded into the native engine.
     * The on-device self-test independently determines if it produced an answer.
     */
    fun loadedModelLabel(): String? = AstraModelChoice.loadedName(loadedModelPath)

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
                val explicitBookQuery = when {
                    finalMessage.startsWith("ask book ", ignoreCase = true) ->
                        finalMessage.substring(9).trim()
                    finalMessage.startsWith("ask my books ", ignoreCase = true) ->
                        finalMessage.substring(13).trim()
                    else -> null
                }
                val question = explicitBookQuery ?: finalMessage
                val permittedBooks = explicitBookQuery != null ||
                    context.getSharedPreferences("astra", Context.MODE_PRIVATE)
                        .getBoolean("astra_use_books_in_chat", false)
                if (previousBookContextAllowed != null &&
                    previousBookContextAllowed != permittedBooks
                ) {
                    session.invalidate()
                }
                previousBookContextAllowed = permittedBooks
                val bookReferences = if (permittedBooks)
                    books?.search(question, maxHits = 2).orEmpty()
                else emptyList()
                if (explicitBookQuery != null && bookReferences.isEmpty()) {
                    return@withContext "No relevant passage was found in your imported books. " +
                        "Import a TXT, Markdown or EPUB book in Settings, then ask again."
                }
                if (verifiedFile == null) {
                    onPartial("Verifying the local offline model...")
                }
                val candidates = AstraModelChoice.candidates(
                    advancedAvailable = AstraModelImport.advancedInstalled(context) ||
                        isBundled(AstraModelImport.ADVANCED_NAME),
                    compactAvailable = AstraModelImport.modelFile(context).isFile ||
                        isBundled(ASSET_NAME)
                )
                if (candidates.isEmpty()) {
                    error(
                        "No local language model installed. Open Settings to import " +
                            "the free Qwen3 1.7B model once."
                    )
                }
                // A model file's size alone cannot prove integrity. If the
                // preferred local file is truncated or corrupted, try the
                // compact GGUF instead of disabling chat.
                var selected: File? = null
                var lastPreparationError: Exception? = null
                for (candidate in candidates) {
                    try {
                        selected = when (candidate) {
                            AstraModelChoice.Variant.ADVANCED -> ensureModel(
                                AstraModelImport.ADVANCED_NAME,
                                AstraModelImport.ADVANCED_SHA256,
                                1_150_000_000L
                            )
                            AstraModelChoice.Variant.COMPACT -> ensureModel(
                                ASSET_NAME, MODEL_SHA256, MIN_SIZE_BYTES
                            )
                            AstraModelChoice.Variant.NONE -> null
                        }
                        if (selected != null) break
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (failure: Exception) {
                        lastPreparationError = failure
                        if (candidate == AstraModelChoice.Variant.ADVANCED &&
                            AstraModelChoice.Variant.COMPACT in candidates
                        ) {
                            onPartial("Advanced model unavailable. Checking compact offline fallback...")
                        }
                    }
                }
                var modelPath = selected ?: throw IllegalStateException(
                    "No verified local language model could be prepared. " +
                        "Re-import a model from Settings.",
                    lastPreparationError
                )
                val current = engine ?: AiChat.getInferenceEngine(
                    context.applicationContext
                ).also { engine = it }
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
                    suspend fun loadPreparedModel(path: File) {
                        // Unload stale native KV-cache when changing conversations
                        // or switching model weights. No network call is involved.
                        if (current.state.value is InferenceEngine.State.ModelReady) {
                            current.cleanUp()
                        }
                        current.loadModel(path.absolutePath)
                        current.setSystemPrompt(AstraConversationContext.systemPrompt())
                        loadedModelPath = path.absolutePath
                        restoringConversation = true
                        session.markLoaded(conversationGeneration)
                    }
                    try {
                        loadPreparedModel(modelPath)
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (loadError: Exception) {
                        // Native model load may fail without crashing Android.
                        // A valid compact GGUF can then recover conversation.
                        session.invalidate()
                        loadedModelPath = null
                        if (modelPath.name != AstraModelImport.ADVANCED_NAME ||
                            AstraModelChoice.Variant.COMPACT !in candidates
                        ) throw loadError
                        onPartial("Advanced model failed to load. Trying compact offline fallback...")
                        // JNI's Error state is resettable, but a process-level
                        // native crash cannot be recovered from Kotlin.
                        current.cleanUp()
                        modelPath = ensureModel(ASSET_NAME, MODEL_SHA256, MIN_SIZE_BYTES)
                        loadPreparedModel(modelPath)
                    }
                }

                val answer = StringBuilder()
                var lastUiUpdate = 0L
                val openReferences = runCatching {
                    val bundled = context.assets.open(AstraOpenLibrary.BUNDLED_ASSET)
                        .use(AstraOpenLibrary::load)
                    val synced = java.io.File(
                        context.noBackupFilesDir,
                        "knowledge/" + AstraOpenLibrary.SYNCED_NAME
                    ).takeIf { it.isFile && it.length() <= 160_000L }
                        ?.inputStream()?.use(AstraOpenLibrary::load).orEmpty()
                    val learnedHint = learning?.takeIf { it.status().steps >= 25 }
                        ?.predict(question)?.let {
                            it.topic == AstraContinualLearning.Topic.RUSSIAN &&
                                it.confidence > 0.82
                        } == true
                    if (bookReferences.isEmpty() && explicitBookQuery == null)
                        AstraOpenLibrary.relevant(
                            question, (synced + bundled).distinctBy { it.id },
                            neuralRussianHint = learnedHint
                        )
                    else emptyList()
                }.getOrElse { emptyList() }
                val augmentedQuestion = if (bookReferences.isNotEmpty())
                    books?.enrich(question, bookReferences) ?: question
                else AstraOpenLibrary.enrich(question, openReferences)
                val requestPrompt = AstraConversationContext.userPrompt(
                    augmentedQuestion,
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
                val publicAnswer = AstraOfflinePolicy.publicReply(answer.toString())
                if (publicAnswer.isBlank()) {
                    session.invalidate()
                    "The offline model did not produce a usable answer. Please try again."
                } else if (bookReferences.isNotEmpty()) {
                    publicAnswer + "\n\nRetrieved local book passages (verify originals):\n" +
                        (books?.citations(bookReferences) ?: "")
                } else if (openReferences.isNotEmpty()) {
                    publicAnswer + "\n\nOpen-course references:\n" +
                        AstraOpenLibrary.citations(openReferences)
                } else {
                    publicAnswer
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
