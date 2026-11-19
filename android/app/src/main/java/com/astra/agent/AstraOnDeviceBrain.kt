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
    private val verifiedModels = AstraVerifiedModelCache()
    @Volatile
    private var loadedModelPath: String? = null
    // A change of consent must not leave old book snippets in native KV-cache.
    private var previousBookContextAllowed: Boolean? = null
    private var previousReplyLanguageId: String? = null
    // A failed native 1.7B load should not re-run on every subsequent turn.
    // A newly imported file can be retried without restarting Astra.
    private val failedAdvancedModels = AstraModelHealth()

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
        diagnosticMaxTokens: Int? = null,
        onPartial: suspend (String) -> Unit = {}
    ): String = mutex.withLock {
            // Durable but content-free checkpoint survives a fatal JNI signal.
            AstraInferenceCheckpoint.begin(context)
            try {
                withContext(Dispatchers.IO) {
                val finalMessage = AstraConversationContext.currentUserMessage(
                    turns.lastOrNull { it.role == "user" }?.content
                )
                require(finalMessage.isNotEmpty()) { "Message cannot be empty." }
                val selectedReplyLanguageId = context.getSharedPreferences(
                    "astra", Context.MODE_PRIVATE
                ).getString("astra_ai_reply_language", "auto") ?: "auto"
                if (previousReplyLanguageId != null &&
                    previousReplyLanguageId != selectedReplyLanguageId
                ) {
                    // Do not keep earlier output-language directives inside
                    // the native KV cache when the owner switches languages.
                    session.invalidate()
                }
                previousReplyLanguageId = selectedReplyLanguageId
                val explicitBookQuery = AstraBookQuery.extract(finalMessage)
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
                // Never append citations if question length prevents the
                // matching source excerpts from entering Qwen's prompt.
                if (explicitBookQuery != null &&
                    !AstraReferenceFit.permitsBook(question)
                ) {
                    return@withContext "Your book question is too long to attach " +
                        "relevant passages safely. Shorten it to 1,800 characters " +
                        "or fewer and ask again."
                }
                val bookReferences = if (permittedBooks &&
                    AstraReferenceFit.permitsBook(question)
                ) books?.search(question, maxHits = 2).orEmpty()
                else emptyList()
                if (explicitBookQuery != null && bookReferences.isEmpty()) {
                    return@withContext "No relevant passage was found in your imported books. " +
                        "Import a supported TXT, EPUB, RTF or document file in Settings, then ask again."
                }
                if (!verifiedModels.hasVerifiedFile()) {
                    onPartial("Verifying the local offline model...")
                }
                val advancedFile = AstraModelImport.advancedFile(context)
                val compactAvailable = AstraModelImport.modelFile(context).isFile ||
                    isBundled(ASSET_NAME)
                val candidates = AstraModelChoice.candidates(
                    advancedAvailable = (AstraModelImport.advancedInstalled(context) ||
                        isBundled(AstraModelImport.ADVANCED_NAME)) &&
                        (!compactAvailable || failedAdvancedModels.shouldAttempt(advancedFile)),
                    compactAvailable = compactAvailable
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
                        if (candidate == AstraModelChoice.Variant.ADVANCED) {
                            failedAdvancedModels.recordFailure(advancedFile)
                        }
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
                AstraInferenceCheckpoint.mark(
                    context, AstraInferenceCheckpoint.ENGINE_SETUP
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
                        // Android's own low-memory signal takes precedence over
                        // starting a large new JNI model allocation. This does
                        // not interfere with an already-running model session.
                        AstraNativeLoadGuard.requireCapacity(context)
                        AstraInferenceCheckpoint.mark(
                            context, AstraInferenceCheckpoint.MODEL_LOADING
                        )
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
                        // Low system RAM is a temporary condition, not
                        // evidence that the advanced GGUF is corrupt. Stop
                        // before JNI and allow a later retry after RAM clears.
                        if (loadError is AstraNativeLowMemoryException) {
                            throw loadError
                        }
                        // Native model load may fail without crashing Android.
                        // A valid compact GGUF can then recover conversation.
                        session.invalidate()
                        loadedModelPath = null
                        if (modelPath.name != AstraModelImport.ADVANCED_NAME ||
                            AstraModelChoice.Variant.COMPACT !in candidates
                        ) throw loadError
                        failedAdvancedModels.recordFailure(modelPath)
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
                    if (bookReferences.isEmpty() && explicitBookQuery == null &&
                        AstraReferenceFit.permitsOpenCourse(question)
                    ) AstraOpenLibrary.relevant(
                            question, (synced + bundled).distinctBy { it.id },
                            neuralRussianHint = learnedHint
                        )
                    else emptyList()
                }.getOrElse { emptyList() }
                val augmentedQuestion = if (bookReferences.isNotEmpty())
                    books?.enrich(question, bookReferences) ?: question
                else AstraOpenLibrary.enrich(question, openReferences)
                val requestPrompt = AstraReplyLanguage.prepare(
                    AstraConversationContext.userPrompt(
                        augmentedQuestion,
                        turns.dropLast(1).map {
                            AstraConversationContext.Turn(it.role, it.content)
                        },
                        restoringConversation,
                        thinkingRequested = AstraReasoningMode.enabled(finalMessage)
                    ),
                    AstraReplyLanguage.fromId(selectedReplyLanguageId)
                )
                // Attribution must reflect the exact prompt after language
                // preferences, recovery and truncation have been applied.
                val citedBooks = if (bookReferences.isNotEmpty()) {
                    books?.includedEvidence(question, bookReferences, requestPrompt).orEmpty()
                } else emptyList()
                val citedOpenCourse = if (bookReferences.isEmpty()) {
                    AstraOpenLibrary.includedEvidence(openReferences, requestPrompt)
                } else emptyList()
                if (explicitBookQuery != null && citedBooks.isEmpty()) {
                    return@withContext "Your book excerpts could not fit safely in " +
                        "the offline model prompt. Ask a shorter, more specific question."
                }
                val answerBudget = AstraGenerationPolicy.effectiveBudget(
                    AstraGenerationPolicy.maxAnswerTokens(
                        finalMessage,
                        advancedModel = modelPath.name == AstraModelImport.ADVANCED_NAME,
                        hasReferences = citedBooks.isNotEmpty() || citedOpenCourse.isNotEmpty()
                    ),
                    diagnosticMaxTokens
                )
                AstraInferenceCheckpoint.mark(
                    context, AstraInferenceCheckpoint.TOKEN_GENERATION
                )
                var outputTruncated = false
                current.sendUserPrompt(requestPrompt, answerBudget).collect { part ->
                    // Keep the existing UTF-16 cap without bisecting an emoji
                    // or changing earlier streamed text after truncation.
                    if (!outputTruncated) {
                        outputTruncated = AstraStreamUnicode.appendCapped(
                            answer, part, 16_000
                        )
                    }
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
                } else if (citedBooks.isNotEmpty()) {
                    publicAnswer + "\n\nRetrieved local book passages (verify originals):\n" +
                        (books?.citations(citedBooks) ?: "")
                } else if (citedOpenCourse.isNotEmpty()) {
                    publicAnswer + "\n\nOpen-course references:\n" +
                        AstraOpenLibrary.citations(citedOpenCourse)
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
            } finally {
                // Does not run on fatal native process death. Cancelled and
                // recoverable requests clear the checkpoint normally.
                AstraInferenceCheckpoint.clear(context)
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
        verifiedModels.matching(name, expectedSha256)?.let { return it }
        // A manually imported GGUF may atomically replace the verified file
        // while Astra is open. The previous native model and KV cache must
        // not be reused against the new file, even at the same pathname.
        if (verifiedModels.changed(name)) {
            session.invalidate()
            loadedModelPath = null
        }
        val directory = File(context.noBackupFilesDir, "offline-models")
        require(directory.isDirectory || directory.mkdirs()) {
            "Cannot create private model storage."
        }
        val target = AstraModelStorage.activeModel(directory, name)
        if (target.isFile && target.length() >= minimumBytes &&
            sha256(target).equals(expectedSha256, ignoreCase = true)
        ) {
            verifiedModels.rememberVerified(target, expectedSha256)
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
            verifiedModels.rememberVerified(target, expectedSha256)
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
