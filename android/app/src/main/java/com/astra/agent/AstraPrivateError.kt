package com.astra.agent

import java.io.IOException
import java.util.Locale

/**
 * Owner-visible diagnostics for private operations that use Android document
 * providers, local file storage or the native inference engine.
 *
 * Raw exception messages may contain SAF URIs, private document names,
 * usernames, native paths, network tokens, or snippets of imported text.
 * Only fixed, reviewed strings leave this boundary. All classification runs
 * locally, with no logging, telemetry, uploads or new permissions.
 */
internal object AstraPrivateError {
    enum class Operation {
        MODEL_CHAT,
        BACKUP_EXPORT,
        BACKUP_READ,
        BACKUP_RESTORE,
        BOOK_IMPORT,
        BOOK_CLEAR,
        VOCAB_IMPORT,
        VOCAB_EXPORT,
        VOCAB_RESTORE,
        VOCAB_CLEAR,
        MODEL_SELF_TEST,
        LANGUAGE_CHECKS,
        LIBRARY_SYNC,
        WORKFLOW_SAVE,
        FOLDER_LINK,
        FOLDER_IMPORT,
        FOLDER_REVOKE
    }

    private enum class Cause {
        STORAGE, ACCESS, UNSUPPORTED, MEMORY, STARTUP, MODEL,
        NETWORK, DOCUMENT_STALLED, GENERATION, CONTEXT_LIMIT, UNKNOWN
    }

    private fun category(error: Exception): Cause {
        // Only recognize the exact internal stall signature from our bounded
        // reader, including when an archive parser wraps its IOException.
        // Never interpret arbitrary provider error text as public advice.
        if (generateSequence(error as Throwable?) { it.cause }.take(3).any {
                it is IOException &&
                    it.message == AstraProgressInputStream.STALLED_PROVIDER
            }
        ) return Cause.DOCUMENT_STALLED
        // Only this audited, fixed engine-deadline message is a startup
        // timeout. A general "Qwen3" string is not evidence of corrupt weights.
        if (error is IllegalStateException &&
            error.message == AstraNativeStartupWait.TIMEOUT_ADVICE
        ) return Cause.STARTUP
        if (error is IllegalStateException &&
            error.message == "Offline generation exceeded its time budget. Retry after cleanup."
        ) return Cause.GENERATION
        if (error is IllegalStateException &&
            error.message == "Native prompt processing failed (code 5)"
        ) return Cause.CONTEXT_LIMIT
        // Read up to three causes, with a short bound; never return or store
        // the diagnostic text. A provider can include a path or signed URL.
        val description = generateSequence(error as Throwable?) { it.cause }
            .take(3)
            .joinToString(" ") { it.message.orEmpty().take(350) }
            .lowercase(Locale.ROOT)
        return when {
            error is SecurityException ||
                listOf("permission denied", "permission required",
                    "access denied", "not permitted", "securityexception")
                    .any(description::contains) -> Cause.ACCESS
            listOf("no space", "insufficient storage", "not enough storage",
                "storage full", "enospc", "disk full")
                .any(description::contains) -> Cause.STORAGE
            listOf("low memory", "out of memory", "native memory",
                "memory pressure", "outofmemory")
                .any(description::contains) -> Cause.MEMORY
            listOf("sha-256", "checksum", "gguf", "model loading",
                "model unavailable", "qwen3 1.7b")
                .any(description::contains) -> Cause.MODEL
            listOf("unsupported", "corrupt", "malformed", "invalid file",
                "invalid archive", "too large", "file format",
                "parse error", "truncated")
                .any(description::contains) -> Cause.UNSUPPORTED
            listOf("timed out", "timeout", "http ", "connection reset",
                "network unavailable", "connection failed")
                .any(description::contains) -> Cause.NETWORK
            else -> Cause.UNKNOWN
        }
    }

    fun forOwner(operation: Operation, failure: Exception): String {
        val heading = when (operation) {
            Operation.MODEL_CHAT -> "Offline model unavailable."
            Operation.BACKUP_EXPORT -> "Backup export did not finish."
            Operation.BACKUP_READ -> "Could not read the selected backup."
            Operation.BACKUP_RESTORE -> "Data restore did not finish."
            Operation.BOOK_IMPORT -> "Book import did not finish."
            Operation.BOOK_CLEAR -> "Could not finish clearing imported books."
            Operation.VOCAB_IMPORT -> "Vocabulary import did not finish."
            Operation.VOCAB_EXPORT -> "Vocabulary export did not finish."
            Operation.VOCAB_RESTORE -> "Vocabulary restore did not finish."
            Operation.VOCAB_CLEAR -> "Could not finish clearing custom vocabulary."
            Operation.MODEL_SELF_TEST -> "Offline AI self-test did not finish."
            Operation.LANGUAGE_CHECKS -> "Conversation checks did not finish."
            Operation.LIBRARY_SYNC -> "Public course update did not finish."
            Operation.WORKFLOW_SAVE -> "Workflow could not be saved."
            Operation.FOLDER_LINK -> "Could not retain folder access."
            Operation.FOLDER_IMPORT -> "Folder import did not finish."
            Operation.FOLDER_REVOKE -> "Could not revoke linked-folder access."
        }
        val cause = category(failure)
        val reason = when (cause) {
            Cause.STORAGE -> "Internal phone storage may be full."
            Cause.ACCESS -> "Android did not grant the required file access."
            Cause.UNSUPPORTED -> "The selected material may be unsupported or damaged."
            Cause.MEMORY -> "Android may not have enough available RAM."
            Cause.STARTUP -> "The offline AI engine did not finish starting in time."
            Cause.GENERATION -> "Offline generation exceeded its time budget."
            Cause.CONTEXT_LIMIT -> "The complete question and response budget do not fit the local context."
            Cause.MODEL -> "The verified Qwen3 1.7B model may be unavailable or invalid."
            Cause.NETWORK -> "The connection may have timed out or been rejected."
            Cause.DOCUMENT_STALLED ->
                "The selected document provider stopped returning data."
            Cause.UNKNOWN -> "The cause could not be identified safely."
        }
        val advice = if (cause == Cause.DOCUMENT_STALLED) {
            "Copy the file to Downloads and select it again from Settings."
        } else if (operation == Operation.MODEL_CHAT &&
            cause == Cause.STARTUP
        ) {
            "Close other apps and retry, or restart Astra. " +
                "Your model, chats, notes and tasks stay on your phone."
        } else if (operation == Operation.MODEL_CHAT && cause == Cause.CONTEXT_LIMIT) {
            "Use a shorter question or direct-answer mode. Your model and saved data are unchanged."
        } else when (operation) {
            Operation.MODEL_CHAT ->
                "Retry from Chat or verify the 1.7B model in Settings. " +
                    "Tasks and notes remain available."
            Operation.BACKUP_EXPORT, Operation.VOCAB_EXPORT ->
                "Choose a writable destination and retry. Keep your existing backup."
            Operation.BACKUP_READ ->
                "Select a supported Astra backup in Files and retry."
            Operation.BACKUP_RESTORE, Operation.VOCAB_RESTORE ->
                "Review your existing data before retrying. Do not clear app data."
            Operation.BOOK_IMPORT ->
                "Choose a readable supported book and retry from Settings."
            Operation.VOCAB_IMPORT ->
                "Check that the TSV uses the supported vocabulary format."
            Operation.BOOK_CLEAR, Operation.VOCAB_CLEAR ->
                "Inspect the current library before retrying the clear operation."
            Operation.MODEL_SELF_TEST, Operation.LANGUAGE_CHECKS ->
                "Check the installed 1.7B model, available RAM and retry the check."
            Operation.LIBRARY_SYNC ->
                "Check internet access for public learning resources and retry."
            Operation.WORKFLOW_SAVE ->
                "Review the current tasks and notes before trying the workflow again."
            Operation.FOLDER_LINK ->
                "Choose the folder again and approve Android's read-only access."
            Operation.FOLDER_IMPORT ->
                "Check the linked folder's access and retry the import in Settings."
            Operation.FOLDER_REVOKE ->
                "Review folder authorization in Settings and try again."
        }
        return "$heading $reason $advice"
    }
}
