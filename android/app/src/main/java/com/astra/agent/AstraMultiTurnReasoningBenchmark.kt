package com.astra.agent

import java.util.Locale

/**
 * Owner-triggered synthetic multi-turn tests on the actual offline model.
 * Neither private notes nor archived chats or book passages enter this suite.
 * A strict final answer does not constitute an overall intelligence score.
 */
internal object AstraMultiTurnReasoningBenchmark {
    enum class Kind { TEXT, NUMBER }

    data class Scenario(
        val title: String,
        val prompts: List<String>,
        val expected: String,
        val kind: Kind,
        val interimExpected: List<String> = listOf("ACK", "ACK")
    ) {
        init {
            require(prompts.size == 3)
            require(prompts.all { it.isNotBlank() && it.length <= 300 })
            require(interimExpected.size == 2 &&
                interimExpected.all { it.length in 1..20 && it.isNotBlank() }
            )
        }
    }

    val scenarios = listOf(
        Scenario(
            "Correction across turns",
            listOf(
                "Synthetic test: a fictional bond matures in SEVEN years. Reply ACK only.",
                "Correction: this fictional bond matures in THREE years, not seven. Reply ACK only.",
                "What is the corrected bond maturity in years? Reply with only the number."
            ), "3", Kind.NUMBER
        ),
        Scenario(
            "Most recent topic",
            listOf(
                "Synthetic test: fictional company ALPHA sells shoes. Reply ACK only.",
                "New topic: fictional company BETA sells books. Reply ACK only.",
                "Which company sells books? Reply ALPHA or BETA only."
            ), "BETA", Kind.TEXT
        ),
        Scenario(
            "Corrected financial premise",
            listOf(
                "Synthetic finance test: a one-year payment is 110 pula at an annual 8% discount rate. Reply ACK only.",
                "Change the discount rate to 10%, superseding 8%. Reply ACK only.",
                "Using the corrected rate, what is the present value in pula? Reply with only the number."
            ), "100", Kind.NUMBER
        ),
        Scenario(
            "Cross-language context",
            listOf(
                "Fictional language exercise: our chosen town is Maun. Reply OK only.",
                "Switch to French. Quelle ville avons-nous choisie? Reponds seulement avec le nom.",
                "Rappelle la ville choisie au debut. Reponds avec seulement son nom."
            ), "Maun", Kind.TEXT, listOf("OK", "Maun")
        )
    )

    data class Outcome(
        val scenario: Scenario,
        val passed: Boolean,
        val millis: Long,
        val interimPassed: Int = 0
    )

    /**
     * A final answer can be right by chance while earlier responses ignored
     * the owner's instructions. Count these separately and do not inflate
     * the already narrow final-answer correctness score.
     */
    fun interimMatches(scenario: Scenario, turnIndex: Int, raw: String): Boolean {
        if (turnIndex !in 0..1 || AstraInterruptedReply.isIncomplete(raw)) return false
        val clean = AstraOfflinePolicy.publicReply(raw).trim()
            .trimEnd('.', '!', ' ')
        if (clean.isEmpty() || clean.length > 30 ||
            clean.contains('\n') || clean.contains('\r')
        ) return false
        return clean.equals(scenario.interimExpected[turnIndex], ignoreCase = true)
    }

    fun score(scenario: Scenario, raw: String): Boolean {
        if (AstraInterruptedReply.isIncomplete(raw)) return false
        val reply = AstraOfflinePolicy.publicReply(raw).trim()
        if (reply.isBlank() || reply.length > 40 ||
            reply.contains('\n') || reply.contains('\r')
        ) return false
        val value = reply.trimEnd('.', '!', ' ').trim()
        return when (scenario.kind) {
            Kind.TEXT -> value.equals(scenario.expected, ignoreCase = true)
            Kind.NUMBER -> {
                val actual = value.toBigDecimalOrNull() ?: return false
                val expected = scenario.expected.toBigDecimalOrNull() ?: return false
                actual.compareTo(expected) == 0
            }
        }
    }

    fun render(results: List<Outcome>, model: String?): String {
        require(results.size == scenarios.size &&
            results.map { it.scenario }.toSet() == scenarios.toSet() &&
            results.all { it.interimPassed in 0..2 }
        ) { "All synthetic multi-turn cases must complete." }
        if (!AstraReasoningBenchmark.verifiedModel(model)) return AstraReasoningBenchmark.UNSCORED
        return buildString {
            appendLine("QWEN3 ON-PHONE MULTI-TURN CHECKS — EXPERIMENTAL")
            appendLine("Loaded model: " + (model ?: "unverified"))
            appendLine("${results.count { it.passed }}/${scenarios.size} strict final-answer checks passed.")
            appendLine("${results.sumOf { it.interimPassed }}/${scenarios.size * 2} earlier-turn instruction checks passed.")
            results.forEach {
                appendLine((if (it.passed) "PASS" else "FAIL") +
                    " — " + it.scenario.title + " (${it.millis.coerceAtLeast(0L)} ms)" +
                    " · early replies ${it.interimPassed}/2")
            }
            append(
                "Three successive native replies per case, using only fixed " +
                    "synthetic prompts and an isolated local conversation. " +
                    "No private source data, prompts or answers are exported. " +
                    "Strict final-answer checks do not measure explanation " +
                    "quality or general intelligence and earn no 100-point " +
                    "handset quality score."
            )
        }
    }
}
