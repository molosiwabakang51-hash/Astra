package com.astra.agent

/**
 * Converts missing or incompatible Android/JNI engine libraries into a
 * recoverable, content-free startup failure instead of letting a Java
 * LinkageError escape the ordinary Kotlin Exception boundary and close Astra.
 *
 * The native backend is still fully offline and only Qwen3 1.7B is permitted.
 * Hardware-native SIGSEGV/OOM kills cannot be caught from Kotlin, and this
 * deliberately does not swallow OutOfMemoryError or VM failures.
 */
internal object AstraNativeBootstrap {
    const val RECOVERY_ADVICE =
        "The offline Qwen3 1.7B inference engine could not start on this device. " +
            "Check that the installed APK is the correct arm64 build; " +
            "your downloaded GGUF, chats and notes remain stored locally."

    fun <T> acquire(factory: () -> T): T = try {
        factory()
    } catch (failure: LinkageError) {
        // Failure causes may include filesystem paths or internal loader
        // details. They remain attached for developer diagnosis but are never
        // interpolated into the owner-visible message or stored in the chat.
        throw IllegalStateException(RECOVERY_ADVICE, failure)
    }
}
