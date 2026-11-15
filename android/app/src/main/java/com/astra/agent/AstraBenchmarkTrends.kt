package com.astra.agent

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A bounded, private record of explicitly run native-Qwen spot checks.
 * Saves only counts, model tier and elapsed milliseconds, never prompts,
 * generated answers, filenames, device identifiers or private user messages.
 */
internal object AstraBenchmarkTrends {
    private const val MAX_RECORDS = 8
    private const val MAX_STORED_CHARS = 2048
    private const val MAX_TEST_MS = 300_000L

    data class Entry(
        val epochMillis: Long,
        val model: String,
        val passed: Int,
        val total: Int,
        val elapsedMillis: Long
    )

    private val line = Regex(
        """^(\d{13})\|(compact|advanced|unverified)\|(\d{1,2})\|(\d{1,2})\|(\d{1,7})$"""
    )

    fun entries(raw: String): List<Entry> =
        raw.takeLast(MAX_STORED_CHARS).lineSequence().mapNotNull {
            val match = line.matchEntire(it.trim()) ?: return@mapNotNull null
            val time = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val model = match.groupValues[2]
            val passed = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val total = match.groupValues[4].toIntOrNull() ?: return@mapNotNull null
            val elapsed = match.groupValues[5].toLongOrNull() ?: return@mapNotNull null
            if (time !in 946684800000L..4102444800000L ||
                total !in 1..30 || passed !in 0..total ||
                elapsed !in 0..(30L * MAX_TEST_MS)) return@mapNotNull null
            Entry(time, model, passed, total, elapsed)
        }.take(MAX_RECORDS).toList()

    /** Can only commit a completed set of distinct probes, not partial runs. */
    fun append(
        old: String,
        outcomes: List<AstraLanguageBenchmark.Outcome>,
        loadedModel: String?,
        epochMillis: Long
    ): String {
        require(epochMillis in 946684800000L..4102444800000L)
        require(outcomes.size == AstraLanguageBenchmark.Probe.entries.size)
        require(outcomes.map { it.probe }.distinct().size == outcomes.size)
        val model = when (loadedModel) {
            "Qwen3 1.7B · Q4_K_M" -> "advanced"
            "Qwen3 0.6B · Q4_K_M" -> "compact"
            else -> "unverified"
        }
        val completed = Entry(
            epochMillis, model, outcomes.count { it.passed }, outcomes.size,
            outcomes.sumOf { it.milliseconds.coerceIn(0L, MAX_TEST_MS) }
        )
        return (entries(old) + completed).takeLast(MAX_RECORDS)
            .joinToString("\n") {
                listOf(it.epochMillis, it.model, it.passed, it.total,
                    it.elapsedMillis).joinToString("|")
            }
    }

    fun render(raw: String): String {
        val history = entries(raw)
        if (history.isEmpty()) {
            return "No completed on-phone language checks are recorded."
        }
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return buildString {
            appendLine("ASTRA // PRIVATE PHONE CHECK HISTORY")
            appendLine("Last ${history.size} completed spot checks (newest first).")
            history.asReversed().forEach {
                val model = when (it.model) {
                    "advanced" -> "Qwen3 1.7B"
                    "compact" -> "Qwen3 0.6B"
                    else -> "Model unverified"
                }
                appendLine(date.format(Date(it.epochMillis)) +
                    " · " + model + " · " + it.passed + "/" + it.total +
                    " checks · " + (it.elapsedMillis / 1000L) + " sec")
            }
            append("Stored privately; not a general intelligence or 8.5/10 score. " +
                "Never exported automatically. Raw answers are not saved.")
        }
    }
}
