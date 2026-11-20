package com.astra.agent

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraNativeBootstrapTest {
    @Test fun successfullyInitializedEngineIsReturnedWithoutWrapping() {
        val engine = Any()
        assertSame(engine, AstraNativeBootstrap.acquire { engine })
    }

    @Test fun missingAndroidNativeLibraryBecomesRecoverableException() {
        val original = UnsatisfiedLinkError(
            "dlopen failed at /data/user/0/private/libastra.so"
        )
        val failure = runCatching {
            AstraNativeBootstrap.acquire<Any> { throw original }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertSame(original, failure?.cause)
        assertTrue(failure?.message.orEmpty().contains("correct arm64 build"))
        assertTrue(failure?.message.orEmpty().contains("downloaded GGUF"))
        assertFalse(failure?.message.orEmpty().contains("/data/user/"))
    }

    @Test fun failedNativeClassResolutionAlsoBecomesRecoverable() {
        val original = NoClassDefFoundError(
            "Missing com.arm.aichat from private.apk"
        )
        val failure = runCatching {
            AstraNativeBootstrap.acquire<Any> { throw original }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertSame(original, failure?.cause)
        assertFalse(failure?.message.orEmpty().contains("private.apk"))
    }

    @Test fun userCancellationIsNotReclassifiedAsNativeLibraryFailure() {
        val cancelled = CancellationException("cancel model startup")
        val observed = runCatching {
            AstraNativeBootstrap.acquire<Any> { throw cancelled }
        }.exceptionOrNull()
        assertSame(cancelled, observed)
    }

    @Test fun unrelatedExceptionsAreNotRewrittenAsLinkageFailures() {
        val exception = IllegalArgumentException("invalid owner request")
        val result = runCatching {
            AstraNativeBootstrap.acquire<Any> { throw exception }
        }.exceptionOrNull()
        assertSame(exception, result)
    }

    @Test fun unrecoverableVmErrorsAreNotSwallowedOrMisreported() {
        val error = OutOfMemoryError("simulated only; do not allocate memory")
        val caught = runCatching {
            AstraNativeBootstrap.acquire<Any> { throw error }
        }.exceptionOrNull()
        assertSame(error, caught)
    }

    @Test fun privateLoaderFailureDetailsNeverEnterOwnerMessage() {
        val visible = AstraNativeBootstrap.RECOVERY_ADVICE
        assertEquals(visible, AstraNativeBootstrap.RECOVERY_ADVICE)
        for (secret in listOf("dlopen failed", "/data/", "content://",
            "token=", "secret-key", "chat transcript")) {
            assertFalse(secret, visible.contains(secret, ignoreCase = true))
        }
        assertTrue(visible.contains("chats and notes remain stored locally"))
    }
}
