package com.astra.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns an owner-selected 1.7B import for the lifetime of the app PROCESS,
 * rather than the lifetime of the Activity that launched Android Files.
 *
 * Activity recreation/lock-screen recomposition must not cancel a 1.28 GB
 * copy. Process death is still possible: the next launch reports a safe
 * interruption and the original SAF document is never modified.
 *
 * No URI, file name, prompt or credential is stored in preferences.
 * The model is verified by AstraModelImport before atomic installation.
 */
internal class AstraModelImportGate {
    private val busy = AtomicBoolean(false)
    fun acquire(): Boolean = busy.compareAndSet(false, true)
    fun release() = busy.set(false)
    fun isRunning(): Boolean = busy.get()
}

internal object AstraModelImportCoordinator {
    data class Snapshot(
        val running: Boolean = false,
        val stage: String = "",
        val message: String = ""
    )

    private val gate = AstraModelImportGate()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val current = MutableStateFlow(Snapshot())
    val snapshots: StateFlow<Snapshot> = current
    @Volatile private var importJob: Job? = null
    @Volatile private var ownerRequestedCancel = false

    fun isRunning(): Boolean = gate.isRunning()

    /** Stop only the current model copy, never an installed or backup GGUF. */
    fun cancel(): Boolean {
        val job = importJob ?: return false
        if (!gate.isRunning() || job.isCompleted || job.isCancelled) return false
        ownerRequestedCancel = true
        job.cancel(CancellationException("Owner cancelled model import"))
        return true
    }

    /** Error text is safe to persist: never include provider-specific paths or URIs. */
    internal fun failureMessage(failure: Exception): String {
        val detail = failure.message.orEmpty().lowercase()
        val reason = when {
            "import or download is already running" in detail ->
                "Another model transfer is active. Finish or cancel it in Settings."
            "not enough" in detail || "insufficient" in detail ||
                "no space" in detail -> "Not enough internal storage for the 1.7B model."
            "checksum" in detail || "sha-256" in detail ||
                "verified" in detail || "official" in detail ->
                "The selected weights are not the pinned Qwen3 1.7B Q4_K_M file."
            "exceeds safe size" in detail || "unexpected" in detail ||
                "too many entries" in detail ->
                "The selected file or archive exceeds safe import limits."
            "stopped responding" in detail || "read progress" in detail ->
                "The file provider stopped responding; move the GGUF to local Downloads."
            "cannot open" in detail || "permission" in detail ||
                "access" in detail -> "Android Files could not grant access to this document."
            "gguf" in detail || "archive" in detail ->
                "Select the official Qwen3-1.7B-Q4_K_M.gguf or a FULL Astra APK/ZIP."
            else -> "The selected document could not be imported."
        }
        return "MODEL IMPORT FAILED: $reason The downloaded file was not changed."
    }

    /** A successful SHA-256 import verifies stored bytes, not live JNI inference. */
    internal fun verifiedFileMessage(bytes: Long): String =
        "QWEN3 1.7B FILE VERIFIED · ${bytes / 1048576L} MiB SHA-256 verified. " +
            "Run Settings → Test on-device AI to confirm offline generation."

    /** Return false if another import is already active; never launch two copies. */
    fun start(context: Context, selected: Uri): Boolean {
        if (!gate.acquire()) return false
        ownerRequestedCancel = false
        val app = context.applicationContext
        publish(
            app,
            Snapshot(true, "copying", "Reading the selected Qwen3 1.7B file...")
        )
        // ACTION_OPEN_DOCUMENT gives read permission for the selected URI.
        // Keep a persistable grant only while copying; do not save the URI.
        val heldGrant = runCatching {
            app.contentResolver.takePersistableUriPermission(
                selected, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        }.getOrDefault(false)
        // Publish the job and register cleanup before any import work can run.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val bytes = AstraModelImport.importDocument(app, selected) { copied ->
                    publish(
                        app,
                        Snapshot(
                            true, "copying",
                            "Importing Qwen3 1.7B: ${copied / 1048576L} MiB copied. " +
                                "Keep Astra open until verification finishes."
                        )
                    )
                }
                publish(
                    app,
                    Snapshot(
                        true, "verified",
                        verifiedFileMessage(bytes)
                    )
                )
            } catch (cancel: CancellationException) {
                val userCancelled = ownerRequestedCancel
                publish(
                    app,
                    Snapshot(
                        true, if (userCancelled) "cancelled" else "interrupted",
                        if (userCancelled) {
                            "Model import cancelled. Installed models and the downloaded GGUF are intact."
                        } else {
                            "Model import interrupted. The downloaded GGUF remains intact."
                        }
                    )
                )
                throw cancel
            } catch (failure: Exception) {
                publish(app, Snapshot(true, "failed", failureMessage(failure)))
            }
        }
        importJob = job
        // invokeOnCompletion runs even when Android cancels a scheduled job
        // before its body starts. Cleaning up only inside the coroutine
        // would otherwise leave the single-flight gate locked forever.
        job.invokeOnCompletion { outcome ->
            try {
                if (heldGrant) {
                    runCatching {
                        app.contentResolver.releasePersistableUriPermission(
                            selected, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                if (outcome is CancellationException &&
                    current.value.stage == "copying"
                ) {
                    val cancelledByOwner = ownerRequestedCancel
                    publish(
                        app,
                        Snapshot(
                            true,
                            if (cancelledByOwner) "cancelled" else "interrupted",
                            if (cancelledByOwner)
                                "Model import cancelled. Existing weights are untouched."
                            else "Model import interrupted. The original GGUF is untouched."
                        )
                    )
                }
                current.value = current.value.copy(running = false)
            } finally {
                importJob = null
                gate.release()
            }
        }
        job.start()
        return true
    }

    private fun publish(context: Context, snapshot: Snapshot) {
        current.value = snapshot
        context.getSharedPreferences("astra", Context.MODE_PRIVATE).edit()
            .putString("astra_model_import_status", snapshot.message.take(350))
            .putString("astra_model_import_stage", snapshot.stage)
            .apply()
    }
}
