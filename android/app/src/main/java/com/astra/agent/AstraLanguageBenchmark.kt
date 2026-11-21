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
    const val COMPOUND_INTEREST_PROMPT =
        "A deposit of P1,000 grows by 10% interest compounded once per year " +
            "for exactly 3 years, with no deposits or withdrawals. " +
            "What is the final balance in pula? Reply with only the amount."
    const val DEDUCTION_PROMPT =
        "Every lark is a bird. No bird is a robot. Could any lark be a robot " +
            "under these rules? Reply with only YES or NO."
    const val SETSWANA_PROMPT =
        "Translate the Setswana greeting 'Dumela' into English. " +
            "Reply with one English word only."
    const val FRENCH_PROMPT =
        "Translate the French greeting 'bonjour' into English. " +
            "Reply with only a short English equivalent."
    const val GERMAN_PROMPT =
        "Translate the German word 'Danke' into English. " +
            "Reply with only the English translation."
    const val SPANISH_PROMPT =
        "Translate the Spanish word 'gracias' into English. " +
            "Reply with only the English translation."

    enum class Probe(val title: String) {
        ARITHMETIC("19 + 23"),
        RUSSIAN("Russian translation"),
        CONTEXT("Follow-up memory"),
        GRAMMAR("Russian grammar"),
        COMPOUND_INTEREST("Compound interest"),
        DEDUCTION("Logical deduction"),
        SETSWANA("Setswana greeting"),
        FRENCH("French translation"),
        GERMAN("German translation"),
        SPANISH("Spanish translation")
    }

    data class Outcome(
        val probe: Probe,
        val passed: Boolean,
        val milliseconds: Long,
        val sample: String
    )

    // The on-device benchmark must never treat appended reference excerpts as
    // generated answers OR include them in diagnostic samples. These excerpts
    // may originate in private books imported by the owner.
    private val sourceFooter = Regex(
        """(?im)^\s*(?:Open-course references:|Retrieved local book passages)\s*$"""
    )

    private fun answerOnly(output: String): String {
        val visible = AstraOfflinePolicy.publicReply(output)
        return visible.substring(0, sourceFooter.find(visible)?.range?.first
            ?: visible.length).trim()
    }

    fun score(probe: Probe, answer: String): Boolean {
        // Score only the model answer, not retrieved local source passages.
        val text = answerOnly(answer).lowercase(Locale.ROOT)
        if (text.isBlank() ||
            text.startsWith("the offline model did not produce") ||
            text.startsWith("offline model unavailable")
        ) return false
        return when (probe) {
            Probe.ARITHMETIC ->
                Regex("""^(?:(?:the )?answer (?:is|:)\s*)?(?:\*\*)?42(?:\*\*)?[.!]?$""")
                    .matches(text)
            // Require concise affirmative answers. Merely mentioning the
            // correct answer while negating it must not earn credit.
            Probe.RUSSIAN ->
                Regex("""^(?:thanks|thank(?:\s+|-)you)[.!]?$""").matches(text)
            Probe.CONTEXT ->
                Regex("""^["'“‘]?nimbora["'”’]?[.!]?$""").matches(text)
            Probe.GRAMMAR ->
                // Listing two case names is not evidence of understanding
                // if the response explicitly denies that those cases exist.
                !Regex("""\b(?:no|not|neither|none|without|isn't|aren't|doesn't|don't|incorrect|false)\b""")
                    .containsMatchIn(text) &&
                listOf(
                    "nominative", "genitive", "dative",
                    "accusative", "instrumental", "prepositional"
                ).count { word ->
                    Regex("""\b""" + word + """\b""").containsMatchIn(text)
                } >= 2
            Probe.COMPOUND_INTEREST ->
                Regex("""^(?:p|bwp)?\s*1,?331(?:\.0{1,2})?[.!]?$""").matches(text)
            Probe.DEDUCTION ->
                Regex("""^no[.!]?$""").matches(text)
            Probe.SETSWANA ->
                Regex("""^(?:hello|hi)[.!]?$""").matches(text)
            Probe.FRENCH ->
                Regex("""^(?:hello|hi|good morning)[.!]?$""").matches(text)
            Probe.GERMAN, Probe.SPANISH ->
                Regex("""^(?:thanks|thank(?:\s+|-)you)[.!]?$""").matches(text)
        }
    }

    fun outcome(probe: Probe, answer: String, elapsedMs: Long): Outcome =
        Outcome(probe, score(probe, answer), elapsedMs.coerceAtLeast(0),
            answerOnly(answer).replace(Regex("""\s+"""), " ")
                .trim().take(140))

    fun render(outcomes: List<Outcome>, actualModel: String?): String {
        require(outcomes.size <= Probe.entries.size) {
            "Too many benchmark outcomes"
        }
        // Prevent duplicate result callbacks from inflating the score or
        // hiding untested capabilities behind repeated easy probes.
        require(outcomes.map { it.probe }.distinct().size == outcomes.size) {
            "Duplicate benchmark probe results"
        }
        val passed = outcomes.count { it.passed }
        return buildString {
            appendLine("ON-DEVICE LANGUAGE SPOT CHECKS")
            appendLine("Model loaded: " + (actualModel ?: "unverified"))
            appendLine(passed.toString() + "/" + outcomes.size + " checks passed.")
            appendLine("Coverage: " + outcomes.size + "/" + Probe.entries.size +
                " distinct probes attempted" +
                (if (outcomes.size == Probe.entries.size) " (complete)." else
                    " (partial; not a full ten-probe assessment)."))
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
