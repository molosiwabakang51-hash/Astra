package com.astra.agent

/**
 * Tracks one Android speech-recognition request at a time.
 *
 * SpeechRecognizer callbacks can arrive after cancellation, a new request,
 * a permission/service restart, or the private workspace being locked.
 * A callback may modify the UI or submit text only while its original
 * request id remains the active id.
 *
 * This class stores no words, raw audio, voice prints, or personal data.
 */
internal class AstraSpeechSession {
    private var requestId = 0L
    private var pending = false

    fun begin(): Long {
        // Overflow is not realistically reachable; zero remains reserved to
        // make uninitialized ids clearly invalid in tests and diagnostics.
        requestId = if (requestId == Long.MAX_VALUE) 1L else requestId + 1L
        pending = true
        return requestId
    }

    fun accepts(id: Long): Boolean = pending && id != 0L && id == requestId

    fun isPending(): Boolean = pending

    /** Only the current request can deliver its one terminal result/error. */
    fun complete(id: Long): Boolean {
        if (!accepts(id)) return false
        cancel()
        return true
    }

    /** Invalidates every outstanding callback, including ones already queued. */
    fun cancel() {
        pending = false
        requestId = if (requestId == Long.MAX_VALUE) 1L else requestId + 1L
    }
}

/** Guard private voice output and transcription by lifecycle and app lock. */
internal object AstraSpeechPrivacy {
    fun mayUseVoice(
        foreground: Boolean,
        appLockEnabled: Boolean,
        workspaceUnlocked: Boolean
    ): Boolean = foreground && (!appLockEnabled || workspaceUnlocked)
}
