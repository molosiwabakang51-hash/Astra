package com.astra.agent

/** Session-local, content-free diagnostics. No private text or persistent data.
 * A successful visible reply is not a correctness or intelligence assessment.
 * A terminal outcome is immutable: catch/finally must not overwrite a timeout.
 */
internal data class AstraInferenceAttempt private constructor(
    val phase: Phase,
    val outcome: Outcome,
    val elapsedMs: Long?
) {
    enum class Phase {
        PREPARING, ENGINE_SETUP, MODEL_LOADING, PROMPT_PREPARATION,
        TOKEN_GENERATION, RESPONSE_FILTERING
    }
    enum class Outcome {
        RUNNING, VISIBLE_REPLY, EMPTY_REPLY, TIMEOUT, CANCELLED,
        LOW_MEMORY, LINKAGE_FAILURE, FAILED, NO_GENERATION, TOKEN_LIMIT
    }

    fun advance(value: Phase): AstraInferenceAttempt =
        if (outcome == Outcome.RUNNING && value.ordinal >= phase.ordinal)
            copy(phase = value) else this

    fun finish(value: Outcome, startedNs: Long, finishedNs: Long): AstraInferenceAttempt {
        require(value != Outcome.RUNNING)
        if (outcome != Outcome.RUNNING) return this
        val elapsed = (finishedNs - startedNs).takeIf { it >= 0L }?.div(1_000_000L)
        return copy(outcome = value, elapsedMs = elapsed)
    }

    companion object {
        fun start() = AstraInferenceAttempt(Phase.PREPARING, Outcome.RUNNING, null)

        fun describe(attempt: AstraInferenceAttempt?): String {
            if (attempt == null) return "No native inference attempt recorded."
            val phase = when (attempt.phase) {
                Phase.PREPARING -> "model verification and preparation"
                Phase.ENGINE_SETUP -> "engine initialization"
                Phase.MODEL_LOADING -> "model loading or memory admission"
                Phase.PROMPT_PREPARATION -> "prompt preparation"
                Phase.TOKEN_GENERATION -> "token generation"
                Phase.RESPONSE_FILTERING -> "response filtering"
            }
            val outcome = when (attempt.outcome) {
                Outcome.RUNNING -> "running"
                Outcome.VISIBLE_REPLY -> "visible reply produced; accuracy unverified"
                Outcome.EMPTY_REPLY -> "no visible reply"
                Outcome.TIMEOUT -> "generation deadline exceeded"
                Outcome.CANCELLED -> "cancelled"
                Outcome.LOW_MEMORY -> "Android memory admission refused"
                Outcome.LINKAGE_FAILURE -> "JNI linkage failure"
                Outcome.FAILED -> "failed; cause not established"
                Outcome.NO_GENERATION -> "ended without native generation"
                Outcome.TOKEN_LIMIT -> "output token limit reached; incomplete response"
            }
            val duration = attempt.elapsedMs?.let { "; elapsed ${it} ms" } ?: ""
            return "Latest native attempt: $phase; $outcome$duration. No chat content recorded."
        }
    }
}
