package com.astra.agent

/**
 * Owner-initiated, isolated Qwen3 1.7B fast-vs-think experiments.
 *
 * Every pair uses the EXACT SAME synthetic question, with only the Qwen mode
 * changed. The ordering alternates to reduce a systematic cold-load bias.
 * No private chats, documents, notes, tasks or credentials enter this suite.
 *
 * Four narrow scored answers are NOT an overall intelligence rating.
 */
internal object AstraPairedReasoning {
    enum class Mode { FAST, THINK }

    val probes = listOf(
        AstraReasoningBenchmark.Probe.WEIGHTED_RETURN,
        AstraReasoningBenchmark.Probe.PROJECT_NPV,
        AstraReasoningBenchmark.Probe.LOGICAL_DEDUCTION,
        AstraReasoningBenchmark.Probe.FOREIGN_EXCHANGE_RISK
    )

    data class Comparison(
        val probe: AstraReasoningBenchmark.Probe,
        val fast: AstraReasoningBenchmark.Outcome,
        val think: AstraReasoningBenchmark.Outcome
    )

    fun order(index: Int): List<Mode> =
        if (index % 2 == 0) listOf(Mode.FAST, Mode.THINK)
        else listOf(Mode.THINK, Mode.FAST)

    fun compare(
        probe: AstraReasoningBenchmark.Probe,
        fast: AstraReasoningBenchmark.Outcome,
        think: AstraReasoningBenchmark.Outcome
    ): Comparison {
        require(fast.probe == probe && think.probe == probe) {
            "Both modes must evaluate an identical benchmark question."
        }
        return Comparison(probe, fast, think)
    }

    fun render(comparisons: List<Comparison>, model: String?): String {
        require(comparisons.size == probes.size &&
            comparisons.map { it.probe }.toSet() == probes.toSet()
        ) { "All paired synthetic checks must finish before reporting." }
        val fastPass = comparisons.count { it.fast.passed }
        val thinkPass = comparisons.count { it.think.passed }
        return buildString {
            appendLine("PAIRED OFFLINE QWEN3 REASONING — EXPERIMENTAL")
            appendLine("Loaded model: " + (model ?: "unverified"))
            appendLine("Same synthetic prompts and 512-token output cap per mode.")
            appendLine("Fast: $fastPass/${probes.size} correct; " +
                "Think: $thinkPass/${probes.size} correct.")
            comparisons.forEach { trial ->
                appendLine(
                    trial.probe.title + ": fast " +
                        (if (trial.fast.passed) "PASS" else "FAIL") +
                        " (${trial.fast.milliseconds} ms), think " +
                        (if (trial.think.passed) "PASS" else "FAIL") +
                        " (${trial.think.milliseconds} ms)"
                )
            }
            appendLine(
                "Measured durations include local model setup and may depend " +
                    "on warm/cold loading. Order alternates; repeat after restart."
            )
            append(
                "These four tests do not establish general reasoning ability, " +
                    "a 100-point AI score, or a demonstrated improvement in " +
                    "conversation quality. No private data were used."
            )
        }
    }
}
