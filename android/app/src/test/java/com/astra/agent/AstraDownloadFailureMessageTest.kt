package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AstraDownloadFailureMessageTest {
    @Test fun privateFilePathsAndTokenizedUrlsAreNeverDisclosed() {
        val path = "/storage/emulated/0/Downloads/private-model.gguf"
        val url = "https://model-host.example/weight.gguf?token=secret-token"
        val failure = IOException("Cannot read $path redirected to $url")
        val visible = AstraDownloadFailureMessage.forOwner(failure)
        assertTrue(visible.contains("could not finish"))
        assertFalse(visible.contains(path))
        assertFalse(visible.contains(url))
        assertFalse(visible.contains("secret-token"))
        assertFalse(visible.contains("private-model"))
    }

    @Test fun rejectedResumeOffersAnActionableExplanationWithoutPrivateBytes() {
        val original = IllegalStateException(
            "Saved model offset was rejected (HTTP 416) for bytes=12345- at private URI"
        )
        val visible = AstraDownloadFailureMessage.forOwner(original)
        assertTrue(visible.contains("saved range was rejected", ignoreCase = true))
        assertTrue(visible.contains("temporary partial was cleared"))
        assertFalse(visible.contains("12345"))
        assertFalse(visible.contains("URI"))
    }

    @Test fun hashAndFileSizeErrorsDoNotClaimVerifiedWeights() {
        val wrongHash = AstraDownloadFailureMessage.forOwner(
            IllegalArgumentException("Downloaded GGUF failed SHA-256 at secret.gguf")
        )
        assertTrue(wrongHash.contains("failed the official SHA-256"))
        assertTrue(wrongHash.contains("not installed"))
        assertFalse(wrongHash.contains("secret.gguf"))
        val wrongRange = AstraDownloadFailureMessage.forOwner(
            IOException("Model server returned an inconsistent transfer size")
        )
        assertTrue(wrongRange.contains("invalid size or byte range"))
    }

    @Test fun diskErrorNeverSuggestsUninstallingOrClearingUserData() {
        val visible = AstraDownloadFailureMessage.forOwner(
            IOException("Insufficient storage at /data/user/0/com.astra.agent/")
        )
        assertTrue(visible.contains("internal phone storage"))
        assertTrue(visible.contains("without uninstalling"))
        assertTrue(visible.contains("without uninstalling Astra"))
        assertFalse(visible.contains("/data/user"))
    }

    @Test fun networkTimeoutAndRateLimitingHaveDifferentAdvice() {
        val timeout = AstraDownloadFailureMessage.forOwner(
            IOException("Connection timed out at https://secret.example/?t=private")
        )
        assertTrue(timeout.contains("resumed transfer"))
        assertFalse(timeout.contains("secret.example"))
        val throttled = AstraDownloadFailureMessage.forOwner(
            IOException("Model download failed (HTTP 429). https://private")
        )
        assertTrue(throttled.contains("rate limiting"))
        assertFalse(throttled.contains("https://private"))
    }

    @Test fun unknownErrorsStillProduceAStableSafeMessage() {
        val visible = AstraDownloadFailureMessage.forOwner(
            IllegalStateException("Process failed at /home/someone/private_file")
        )
        assertTrue(visible.contains("could not finish"))
        assertTrue(visible.contains("conversations and weights are intact"))
        assertFalse(visible.contains("/home/someone"))
    }
}
