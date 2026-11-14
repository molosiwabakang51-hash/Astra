package com.astra.agent

import java.util.Locale

/**
 * A local, deterministic challenge proving that the Android inference path
 * produced the specifically requested words rather than a canned error.
 *
 * This cannot prove physical airplane-mode operation from CI. The user should
 * disable Wi-Fi and mobile data before running the in-app self-test.
 */
internal object AstraOfflineSelfTest {
    private val responsePattern = Regex(
        """^\s*["']?\s*ASTRA[\s,.:—-]+OFFLINE\s*[.!?"']*\s*$""",
        RegexOption.IGNORE_CASE
    )

    const val CHALLENGE = "Say ASTRA OFFLINE and nothing else."

    fun evaluate(response: String, elapsedMs: Long): Result {
        val clean = AstraOfflinePolicy.publicReply(response).trim()
        val duration = elapsedMs.coerceAtLeast(0)
        return when {
            clean.isBlank() ||
                clean.startsWith("The offline model did not produce") ||
                clean.startsWith("Offline model unavailable:") ->
                Result(false, "OFFLINE TEST FAILED: The model produced no usable answer.")
            !responsePattern.matches(clean) ->
                Result(
                    false,
                    "OFFLINE TEST UNVERIFIED: Model generated text in ${duration} ms " +
                        "but did not follow the ASTRA OFFLINE challenge."
                )
            else -> Result(true, "OFFLINE TEST VERIFIED in ${duration} ms: ASTRA OFFLINE")
        }
    }

    data class Result(val passed: Boolean, val message: String)
}
