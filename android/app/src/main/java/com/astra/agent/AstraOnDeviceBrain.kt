package com.astra.agent

import android.content.Context
import android.os.StatFs
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Qwen3 inference runs entirely on the Android device.
 *
 * FULL builds bundle Qwen3 1.7B; LITE installs import the same verified model
 * once into app-private storage. Neither build uses the older 0.6B model for
 * conversation, and no cloud inference endpoint is required.
 */
internal class AstraOnDeviceBrain(
    private val context: Context,
    private val learning: AstraContinualLearning? = null,
    private val books: AstraBookLibrary? = null
) {
    data class Turn(val role: String, val content: String)

    companion object {
        // Keep historic public aliases in sync with the actual required 1.7B
        // asset. They must never identify obsolete 0.6B weights as the model.
        const val ASSET_NAME = AstraModelImport.ADVANCED_NAME
        const val MODEL_SHA256 = AstraModelImport.ADVANCED_SHA256
        const val MODEL_LABEL = "Qwen3 1.7B · Q4_K_M · offline"
        private const val MIN_SIZE_BYTES = 1_150_000_000L
        private const val DISK_HEADROOM_BYTES = 64L * 1024 * 1024
    }

    private val mutex = Mutex()
    private var engine: InferenceEngine? = null
    private val session = AstraInferenceSession()
    private val verifiedModels = AstraVerifiedModelCache()
    // Prevent repeated native loads of the identical GGUF after a real
    // loader failure; atomic re-import with a new file identity permits retry.
    private val modelHealth = AstraModelHealth()
    @Volatile
    private var loadedModelPath: String? = null
    @Volatile
    private var lastNativeTiming: AstraInferenceTiming.Sample? = null

    /** Timing only; no prompts, user notes or model-file paths. */
    fun lastInferenceTiming(): String =
        AstraInferenceTiming.describe(lastNativeTiming)
    // A change of consent must not leave old book snippets in native KV-cache.
    private var previousBookContextAllowed: Boolean? = null
    private var previousReplyLanguageId: String? = null

    /**
     * Only reports the last model successfully loaded into the native engine.
     * The on-device self-test independently determines if it produced an answer.
     */
    fun loadedModelLabel(): String? = AstraModelChoice.loadedName(loadedModelPath)

    fun hasLocalModel(): Boolean = hasAdvancedModel()

    fun isModelBundled(): Boolean =
        isBundled(AstraModelImport.ADVANCED_NAME)

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
        diagnosticIsolated: Boolean = false,
        diagnosticReasoningOverride: Boolean? = null,
        onPartial: suspend (String) -> Unit = {}
    ): String = mutex.withLock {
            try {
                // Preference commit() can block on flash and other queued
                // writes, particularly while a 1.7B model strains memory.
                // Do not execute these synchronous checkpoints on Android's
                // main thread before the first native token.
                withContext(Dispatchers.IO) {
                    AstraInferenceCheckpoint.begin(context)
                }
                withContext(Dispatchers.IO) {
                val requestStartedNs = System.nanoTime()
                val finalMessage = AstraConversationContext.currentUserMessage(
                    turns.lastOrNull { it.role == "user" }?.content
                )
                require(finalMessage.isNotEmpty()) { "Message cannot be empty." }
                // Benchmark switches may ONLY alter Qwen's mode for one of
                // the fixed, owner-approved synthetic questions. They cannot
                // enable deep reasoning on chats, private files or older
                // assistant replies. Ordinary diagnostic runs remain fast.
                require(AstraPairedReasoning.allowsModeOverride(
                    finalMessage, diagnosticIsolated, diagnosticReasoningOverride
                )) { "Diagnostic reasoning mode is restricted to fixed offline checks." }
                val adaptiveEnabled = context.getSharedPreferences(
                    "astra", Context.MODE_PRIVATE
                ).getBoolean("astra_adaptive_reasoning", false)
                val carefulReasoning = diagnosticReasoningOverride ?:
                    AstraAdaptiveReasoning.select(
                        finalMessage,
                        automaticEnabled = adaptiveEnabled,
                        diagnosticIsolated = diagnosticIsolated
                    )
                val selectedReplyLanguageId = AstraDiagnosticContext.replyLanguage(
                    context.getSharedPreferences(
                        "astra", Context.MODE_PRIVATE
                    ).getString("astra_ai_reply_language", "auto") ?: "auto",
                    diagnosticIsolated
                )
                if (previousReplyLanguageId != null &&
                    previousReplyLanguageId != selectedReplyLanguageId
                ) {
                    // Do not keep earlier output-language directives inside
                    // the native KV cache when the owner switches languages.
                    session.invalidate()
                }
                previousReplyLanguageId = selectedReplyLanguageId
                // Fixed benchmark prompts must not invoke user-library retrieval,
                // even when their wording happens to resemble a book query.
                val explicitBookQuery = if (diagnosticIsolated) null else
                    AstraBookQuery.extract(finalMessage)
                val question = explicitBookQuery ?: finalMessage
                val permittedBooks = AstraDiagnosticContext.allowPrivateBooks(
                    explicitBookQuery != null,
                    context.getSharedPreferences("astra", Context.MODE_PRIVATE)
                        .getBoolean("astra_use_books_in_chat", false),
                    diagnosticIsolated
                )
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
                // A smaller GGUF may remain on disk after an older Astra
                // install, but it is never a valid conversation fallback.
                if (!hasAdvancedModel()) {
                    error(
                        "Qwen3 1.7B is required for AI conversation. " +
                            "Open Settings to download or import the verified 1.7B GGUF."
                    )
                }
                val modelPath = try {
                    ensureModel(
                        AstraModelImport.ADVANCED_NAME,
                        AstraModelImport.ADVANCED_SHA256,
                        1_150_000_000L
                    )
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    throw IllegalStateException(
                        "Qwen3 1.7B could not be verified. Re-import the official model " +
                            "in Settings; Astra will not use a smaller replacement.",
                        failure
                    )
                }
                AstraInferenceCheckpoint.mark(
                    context, AstraInferenceCheckpoint.ENGINE_SETUP
                )
                val current = engine ?: AstraNativeBootstrap.acquire {
                    // The JNI library can fail to link synchronously on a
                    // mismatched APK or incompatible arm64 Android device.
                    // Convert only LinkageError to a recoverable Exception,
                    // rather than allowing an uncaught process-level close.
                    AiChat.getInferenceEngine(context.applicationContext)
                }.also { engine = it }
                // Upstream initializes JNI asynchronously. Wait for the engine
                // to be ready instead of racing model loading against init.
                val initialized = AstraNativeStartupWait.awaitReady {
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
                        // This is only a native-load backoff. Do not quarantine
                        // a healthy file for temporary Android low-RAM pressure,
                        // prompt cancellation, or another unrelated exception.
                        check(modelHealth.shouldAttempt(path)) {
                            "The unchanged Qwen3 1.7B file failed its previous native load. " +
                                "Re-import verified weights in Settings or restart Astra " +
                                "to retry. Chats and model files are retained."
                        }
                        AstraInferenceCheckpoint.mark(
                            context, AstraInferenceCheckpoint.MODEL_LOADING
                        )
                        // Unload stale native KV-cache when changing conversations
                        // or switching model weights. No network call is involved.
                        if (current.state.value is InferenceEngine.State.ModelReady) {
                            current.cleanUp()
                        }
                        try {
                            current.loadModel(path.absolutePath)
                        } catch (cancel: kotlinx.coroutines.CancellationException) {
                            // Stop/retry must not turn a verified GGUF into a
                            // permanently failed model in the current process.
                            throw cancel
                        } catch (failure: Exception) {
                            modelHealth.recordFailure(path)
                            throw IllegalStateException(
                                "Qwen3 1.7B native model loading failed. " +
                                    "The same file will not be loaded repeatedly " +
                                    "until re-import or app restart.",
                                failure
                            )
                        }
                        current.setSystemPrompt(AstraConversationContext.systemPrompt())
                        loadedModelPath = path.absolutePath
                        restoringConversation = true
                        session.markLoaded(conversationGeneration)
                    }
                    // A 1.7B JNI load failure is reported explicitly.
                    // Downgrading to 0.6B would silently reduce reasoning
                    // quality against the user's selected model policy.
                    loadPreparedModel(modelPath)
                }

                val answer = AstraStreamUnicode.StreamingBuffer(16_000)
                var lastPreviewAttempt = 0L
                var lastPublishedText = ""
                var firstNativeChunkNs: Long? = null
                var nativeChunkCount = 0
                val openReferences = if (diagnosticIsolated) emptyList() else runCatching {
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
                        thinkingRequested = carefulReasoning
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
                        hasReferences = citedBooks.isNotEmpty() || citedOpenCourse.isNotEmpty(),
                        thinkingRequested = carefulReasoning
                    ),
                    diagnosticMaxTokens
                )
                AstraInferenceCheckpoint.mark(
                    context, AstraInferenceCheckpoint.TOKEN_GENERATION
                )
                current.sendUserPrompt(requestPrompt, answerBudget).collect { part ->
                    // Check cancellation even if upstream emits buffered chunks without suspending.
                    currentCoroutineContext().ensureActive()
                    if (part.isNotEmpty()) {
                        if (firstNativeChunkNs == null) firstNativeChunkNs = System.nanoTime()
                        nativeChunkCount++
                    }
                    // JNI can split a supplementary character between two
                    // token chunks. Hold an incomplete high surrogate until
                    // the next chunk, rather than showing or persisting it.
                    answer.append(part)
                    val now = System.nanoTime()
                    if (part.isNotEmpty() &&
                        AstraStreamingUiPolicy.intervalElapsed(now, lastPreviewAttempt)) {
                        // Bound preview parsing even when hidden thinking produces no visible text.
                        lastPreviewAttempt = now
                        // Updating a full Markdown chat bubble and scrolling
                        // several times each second can overwhelm a low-RAM
                        // Android main thread while native Qwen3 is generating.
                        // Bound these expensive UI updates without changing
                        // the token stream, native output or persisted answer.
                        val partial = AstraSourceCitationGuard.protect(
                            AstraOfflinePolicy.publicReply(answer.snapshot()),
                            verifiedPrivatePassages = citedBooks.size,
                            verifiedOpenPassages = citedOpenCourse.size
                        )
                        if (AstraStreamingUiPolicy.hasNewVisibleText(
                                partial, lastPublishedText
                            )) {
                            onPartial(partial)
                            lastPublishedText = partial
                        }
                    }
                }
                val publicAnswer = AstraOfflinePolicy.publicReply(answer.finish())
                // The model can invent [B#]/[K#] markers and even a
                // citation-footer heading. Allow only IDs whose complete
                // evidence blocks actually reached the native model prompt.
                // Strip forged headings before appending app-owned sources;
                // an AI claim must never authorize book follow-up access.
                val attributedAnswer = AstraSourceCitationGuard.protect(
                    publicAnswer,
                    verifiedPrivatePassages = citedBooks.size,
                    verifiedOpenPassages = citedOpenCourse.size
                )
                if (attributedAnswer.isNotBlank()) {
                    // A cancelled, failed, think-only or citation-filtered-empty
                    // reply cannot establish successful visible generation.
                    // Store only numeric timing, never prompt or response text.
                    lastNativeTiming = AstraInferenceTiming.sample(
                        requestStartedNs,
                        firstNativeChunkNs,
                        System.nanoTime(),
                        nativeChunkCount
                    )
                }
                if (attributedAnswer.isBlank()) {
                    // Source/citation filtering can remove every visible token.
                    // Never return a blank bubble or preserve that native session.
                    session.invalidate()
                    "The offline model did not produce a usable answer. Please try again."
                } else if (citedBooks.isNotEmpty()) {
                    attributedAnswer + "\n\nRetrieved local book passages (verify originals):\n" +
                        (books?.citations(citedBooks) ?: "")
                } else if (citedOpenCourse.isNotEmpty()) {
                    attributedAnswer + "\n\nOpen-course references:\n" +
                        AstraOpenLibrary.citations(citedOpenCourse)
                } else {
                    attributedAnswer
                }
                }
            } catch (linkage: LinkageError) {
                // JNI symbols may be resolved only when a later native method
                // first runs (loadModel, system prompt or token generation),
                // not at AiChat.getInferenceEngine(). A Java LinkageError
                // must not escape our normal Kotlin recovery boundary.
                // Keep OOM/VM errors outside this catch by design.
                session.invalidate()
                loadedModelPath = null
                throw AstraNativeBootstrap.recover(linkage)
            } catch (error: Exception) {
                // Cancellation, JNI failures and timeouts can leave partial
                // tokens in the model's native KV-cache. A later request must
                // not inherit that incomplete assistant response.
                session.invalidate()
                loadedModelPath = null
                throw error
            } finally {
                // This is a synchronous disk checkpoint. It must never
                // block the UI thread, and cancellation must not leave a
                // false persistent 'active inference' flag behind.
                // Fatal native process death cannot execute this finally.
                withContext(NonCancellable + Dispatchers.IO) {
                    AstraInferenceCheckpoint.clear(context)
                }
            }
        }

    /** Verify the requested 1.7B GGUF locally and retain it across signed upgrades. */
    private suspend fun ensureModel(
        name: String,
        expectedSha256: String,
        minimumBytes: Long
    ): File {
        // Enforce the selected model requirement at the final native load
        // boundary too, even if a future caller supplies legacy metadata.
        require(name == AstraModelImport.ADVANCED_NAME &&
            expectedSha256.equals(AstraModelImport.ADVANCED_SHA256, ignoreCase = true) &&
            minimumBytes >= MIN_SIZE_BYTES
        ) { "Only verified Qwen3 1.7B Q4_K_M may be loaded for conversation." }
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
        val temporary = File(directory, "$name.partial")
        // A killed FULL asset extraction may leave a 1+ GB partial file.
        // Reclaim it BEFORE checking space so recovery does not fail merely
        // because the interrupted attempt is occupying that same space.
        AstraModelImport.discardInterruptedCopy(temporary)
        val requiredBytes = 1_350_000_000L
        require(StatFs(directory.absolutePath).availableBytes >=
            requiredBytes + DISK_HEADROOM_BYTES
        ) { "Not enough free phone storage to prepare the local language model." }

        try {
            // FULL APK asset reads can be short or transiently return zero.
            // Reuse the bounded, cancellable GGUF stream copier used for Files
            // imports instead of risking an infinite 0-byte read loop here.
            // Hash while copying to avoid rereading ~1.3 GB from phone storage.
            val assetDigest = MessageDigest.getInstance("SHA-256")
            val copiedBytes = context.assets.open(name).use { source ->
                FileOutputStream(temporary).use { sink ->
                    val copied = AstraModelImport.copyBounded(
                        source, sink, assetDigest
                    ) {
                        workContext.ensureActive()
                        require(StatFs(directory.absolutePath).availableBytes >=
                            DISK_HEADROOM_BYTES
                        ) { "Insufficient phone storage while preparing bundled model." }
                    }
                    workContext.ensureActive()
                    sink.fd.sync()
                    copied
                }
            }
            val assetSha256 = assetDigest.digest().joinToString("") {
                "%02x".format(it)
            }
            require(copiedBytes >= minimumBytes &&
                temporary.length() == copiedBytes &&
                assetSha256.equals(expectedSha256, ignoreCase = true)
            ) { "Bundled language model failed SHA-256 integrity verification." }
            AstraModelStorage.installVerified(temporary, target)
            verifiedModels.rememberVerified(target, expectedSha256)
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    // Share the downloader's cancellation-aware, bounded zero-read verifier.
    // A broken stream must not spin indefinitely during 1.7B model startup.
    private suspend fun sha256(file: File): String =
        file.inputStream().buffered().use { input ->
            AstraModelDownload.checksumStream(input)
        }
}
