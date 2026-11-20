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

    /**
     * Shared recovery for synchronous engine setup and JNI method calls.
     * Do not change the supplied cause or use its potentially sensitive text
     * in messages shown to the owner.
     */
    fun recover(failure: LinkageError): IllegalStateException =
        IllegalStateException(RECOVERY_ADVICE, failure)

    fun <T> acquire(factory: () -> T): T = try {
        factory()
    } catch (failure: LinkageError) {
        throw recover(failure)
    }
}
