package com.astra.agent

import java.util.Locale

/**
 * Fixed, independent questions for checking the real ARM64 Qwen inference
 * backend. Unlike unit tests, these checks require actual on-phone generation.
 *
 * They are diagnostic probes, not a general LLM accuracy test, a claim of
 * unrestricted understanding, or proof that model weights have been trained.
 * Prompts contain no private data and call no online AI service.
 */
internal object AstraLanguageBenchmark {
    const val MATH_PROMPT =
        "What is 19 plus 23? Reply with only the number in one word."
    const val RUSSIAN_PROMPT =
        "Translate the Russian word спасибо into English. Reply briefly with only the translation."
    const val MEMORY_SETUP =
        "For this short test remember the invented codeword NIMBORA. Reply OK in one word."
    const val MEMORY_QUESTION =
        "What was the invented codeword? Reply with only that word."
    const val GRAMMAR_PROMPT =
        "Name two grammatical cases used in Russian. Reply with their English names only, briefly."

    enum class Probe(val title: String) {
        ARITHMETIC("19 + 23"),
        RUSSIAN("Russian translation"),
        CONTEXT("Follow-up memory"),
        GRAMMAR("Russian grammar")
    }

    data class Outcome(
        val probe: Probe,
        val passed: Boolean,
        val milliseconds: Long,
        val sample: String
    )

    fun score(probe: Probe, answer: String): Boolean {
        val text = AstraOfflinePolicy.publicReply(answer)
            .trim().lowercase(Locale.ROOT)
        if (text.isBlank() ||
            text.startsWith("the offline model did not produce") ||
            text.startsWith("offline model unavailable")
        ) return false
        return when (probe) {
            Probe.ARITHMETIC ->
                Regex("""(?<!\d)42(?!\d)""").containsMatchIn(text)
            Probe.RUSSIAN ->
                Regex("""\b(?:thanks|thank(?:\s+|-)?you)\b""").containsMatchIn(text)
            Probe.CONTEXT ->
                Regex("""\bnimbora\b""").containsMatchIn(text)
            Probe.GRAMMAR -> listOf(
                "nominative", "genitive", "dative",
                "accusative", "instrumental", "prepositional"
            ).count { word -> Regex("""\b""" + word + """\b""").containsMatchIn(text) } >= 2
        }
    }

    fun outcome(probe: Probe, answer: String, elapsedMs: Long): Outcome =
        Outcome(probe, score(probe, answer), elapsedMs.coerceAtLeast(0),
            AstraOfflinePolicy.publicReply(answer).replace(Regex("""\s+"""), " ")
                .trim().take(140))

    fun render(outcomes: List<Outcome>, actualModel: String?): String {
        require(outcomes.size <= Probe.entries.size)
        val passed = outcomes.count { it.passed }
        return buildString {
            appendLine("ON-DEVICE LANGUAGE SPOT CHECKS")
            appendLine("Model loaded: " + (actualModel ?: "unverified"))
            appendLine(passed.toString() + "/" + outcomes.size + " checks passed.")
            outcomes.forEach { x ->
                appendLine((if (x.passed) "PASS" else "UNVERIFIED") +
                    " — " + x.probe.title + " (" + x.milliseconds + " ms)")
                if (!x.passed) {
                    appendLine("Model output: " + x.sample.ifEmpty { "<no answer>" })
                }
            }
            append("These are narrow model-output checks, not proof of general " +
                "language comprehension, self-training or absence of hallucinations.")
        }
    }
}
