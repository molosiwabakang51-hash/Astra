package com.astra.agent

import java.util.Locale

/**
 * On-phone A/B probes for the same installed Qwen3 1.7B model.
 *
 * A complete run sends every synthetic problem in a fresh, isolated native
 * conversation in both modes. These narrow tests measure correctness and
 * latency; they are not an earned general-intelligence rating.
 */
internal object AstraReasoningBenchmark {
    // Both A/B modes receive the same maximum generated tokens. Otherwise,
    // "careful" mode's larger default answer budget confounds the comparison.
    const val COMPARISON_TOKEN_CAP = 768

    enum class Probe(val title: String, val question: String) {
        ENTAILMENT(
            "Logical entailment",
            "Every sapphire is a stone. Some stones are heavy. " +
                "Does it necessarily follow that every sapphire is heavy? " +
                "Reply with only YES or NO."
        ),
        PROFIT_LOSS(
            "Multi-step profit or loss",
            "A trader buys 40 units at P12 each, sells 15 units at P14 " +
                "and the remaining 25 at P10, with no fees. " +
                "What is the total profit or loss in pula? " +
                "Reply with a signed number only (negative for loss)."
        )
    }

    enum class Mode { FAST, CAREFUL }

    data class Sample(
        val probe: Probe,
        val mode: Mode,
        val passed: Boolean,
        val milliseconds: Long,
        val answerPreview: String
    )

    // The exact same user question is used in both modes. Only the
    // internal Qwen soft switch differs; otherwise a verbal "think
    // carefully" instruction could confound the A/B accuracy comparison.
    fun prompt(probe: Probe): String = probe.question

    fun score(probe: Probe, answer: String): Boolean {
        // Grade ONLY the visible final answer, never hidden reasoning or a
        // footnote containing the correct number in an incorrect explanation.
        val visible = AstraOfflinePolicy.publicReply(answer)
        // Local retrieval can append a citation footer after generation.
        // Grade the model's answer, not facts embedded in that footer.
        val footer = Regex(
            """(?im)^\s*(?:Open-course references:|Retrieved local book passages(?: \(verify originals\))?:?)\s*$"""
        )
        val text = visible.substring(0, footer.find(visible)?.range?.first
            ?: visible.length).trim().replace("**", "")
            .trim().lowercase(Locale.ROOT)
        return when (probe) {
            Probe.ENTAILMENT -> Regex("""^no[.!]?$""").matches(text)
            Probe.PROFIT_LOSS ->
                Regex("""^[-−]\s*(?:p\s*)?20(?:\.0{1,2})?[.!]?$""").matches(text)
        }
    }

    fun sample(probe: Probe, mode: Mode, answer: String, elapsedMs: Long): Sample =
        Sample(
            probe, mode, score(probe, answer), elapsedMs.coerceAtLeast(0),
            AstraOfflinePolicy.publicReply(answer).replace(Regex("""\s+"""), " ")
                .trim().take(120)
        )

    /**
     * No partial scores: interruptions, duplicate cases and wrong model
     * labels cannot be mistaken for an A/B improvement. Count each exact
     * problem/mode pair once. A caller cannot fabricate a complete run by
     * repeating an easy passing probe.
     */
    fun isComplete(samples: List<Sample>): Boolean =
        samples.size == Probe.entries.size * Mode.entries.size &&
            samples.map { it.probe to it.mode }.toSet().size == samples.size &&
            Probe.entries.all { probe ->
                Mode.entries.all { mode ->
                    samples.any { it.probe == probe && it.mode == mode }
                }
            }

    fun render(samples: List<Sample>, loadedModel: String?): String {
        val expectedModel = "Qwen3 1.7B · Q4_K_M"
        if (!isComplete(samples) || loadedModel != expectedModel) {
            return buildString {
                appendLine("ON-DEVICE 1.7B REASONING A/B")
                appendLine("Model loaded: " + (loadedModel ?: "unverified"))
                appendLine("INCOMPLETE OR UNVERIFIED RUN — NO SCORE.")
                append("All distinct questions must finish in both modes " +
                    "on the verified 1.7B model.")
            }
        }
        val fast = samples.filter { it.mode == Mode.FAST }
        val careful = samples.filter { it.mode == Mode.CAREFUL }
        return buildString {
            appendLine("ON-DEVICE 1.7B REASONING A/B")
            appendLine("Model loaded: " + expectedModel)
            appendLine("Fast: " + fast.count { it.passed } + "/" + fast.size)
            appendLine("Careful: " + careful.count { it.passed } + "/" + careful.size)
            appendLine("Fast average elapsed: " +
                fast.map { it.milliseconds }.average().toLong() + " ms")
            appendLine("Careful average elapsed: " +
                careful.map { it.milliseconds }.average().toLong() + " ms")
            for (result in samples) {
                appendLine(
                    (if (result.passed) "PASS" else "UNVERIFIED") + " " +
                        result.mode.name + " · " + result.probe.title +
                        " · " + result.milliseconds + " ms"
                )
                if (!result.passed) {
                    appendLine("Visible reply: " +
                        result.answerPreview.ifEmpty { "<no final answer>" })
                }
            }
            append(
                "These are two synthetic spot checks, not a 10/10 rating. " +
                    "Longer thinking can cost substantial RAM, heat and time. " +
                    "Compare outcomes on the same phone in airplane mode."
            )
        }
    }
}
