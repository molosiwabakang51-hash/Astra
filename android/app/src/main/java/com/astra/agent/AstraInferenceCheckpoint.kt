package com.astra.agent

import android.content.Context

/**
 * Records only generic native-inference phases. A process-level JNI crash
 * bypasses Kotlin catch/finally; a durable phase checkpoint gives the owner
 * a diagnostic clue on the next launch without storing chats or model output.
 *
 * An interrupted checkpoint is not proof of a crash: Android may also
 * terminate the process for low memory or after a user force-stop.
 */
internal object AstraInferenceCheckpoint {
    private const val PREFS = "astra"
    private const val ACTIVE = "astra_chat_native_inference_active"
    private const val PHASE = "astra_chat_native_inference_phase"
    private const val STARTED_AT = "astra_chat_native_inference_started_at_ms"

    const val PREPARING = "preparing"
    const val ENGINE_SETUP = "engine_setup"
    const val MODEL_LOADING = "model_loading"
    const val TOKEN_GENERATION = "token_generation"

    fun begin(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(ACTIVE, true)
            .putString(PHASE, PREPARING)
            .putLong(STARTED_AT, System.currentTimeMillis())
            .commit()
    }

    fun mark(context: Context, phase: String) {
        require(phase in setOf(PREPARING, ENGINE_SETUP, MODEL_LOADING, TOKEN_GENERATION))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PHASE, phase)
            .commit()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(ACTIVE)
            .remove(PHASE)
            .remove(STARTED_AT)
            .commit()
    }

    /** Available after app restart. No prompt, generated answer or file path. */
    fun interruptedSummary(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ACTIVE, false)) return null
        return "PREVIOUS CHAT INFERENCE WAS INTERRUPTED during " +
            phaseLabel(prefs.getString(PHASE, null)) + ". " +
            "This may have been a native crash, memory pressure or a force-stop; " +
            "the phase alone does not establish a cause. " +
            AstraProcessExitReason.latest(
                context, prefs.getLong(STARTED_AT, 0L)
            ) +
            " Keep your model and conversation history; collect a redacted " +
            "Android crash-buffer log if this occurs again."
    }

    /** Pure label selection; reject arbitrary persisted strings. */
    internal fun phaseLabel(phase: String?): String = when (phase) {
        PREPARING -> "model preparation"
        ENGINE_SETUP -> "native engine initialization"
        MODEL_LOADING -> "native model loading"
        TOKEN_GENERATION -> "token generation"
        else -> "an unknown stage"
    }
}
