package com.astra.agent

import java.util.Locale

/**
 * Local mission briefings use only stored tasks, completed tasks and note counts.
 * Priority tags remain in existing task strings: [P1], [P2], [P3].
 */
internal object MissionControl {
    private val priorityCommand = Regex(
        """(?i)^(?:set|mark|change)\s+(?:(?:the\s+)?priority\s+(?:of\s+)?)?task\s+(\d+)\s+(?:(?:to|as)\s+)?(?:priority\s+)?(high|medium|low)$"""
    )
    private val quickPriority = Regex("""(?i)^(?:prioritize|prioritise)\s+task\s+(\d+)$""")
    private val priorityPrefix = Regex("""^\[P[123]]\s*""")
    private val nextCommands = setOf(
        "what should i do next", "what's next", "what next", "what do i do next",
        "what should i work on", "what should i focus on", "my next task",
        "what is my priority", "what's my priority", "top priority", "next task",
        "what is my next task", "what is the most important task"
    )
    private val briefingCommands = setOf(
        "mission control", "brief me", "give me a briefing", "status report",
        "mission briefing", "my mission briefing", "give me a status report",
        "workload overview", "summarize my workload", "summarise my workload",
        "show my progress", "progress report", "how am i doing",
        "how are we doing", "how much have i done", "system briefing",
        "show my dashboard"
    )

    fun respond(
        raw: String,
        tasks: MutableList<String>,
        completed: List<String>,
        notes: List<String>
    ): String? {
        val clean = raw.trim().trimEnd('?', '!', '.').trim()
        val lower = clean.lowercase(Locale.ROOT)

        priorityCommand.matchEntire(clean)?.let { match ->
            return setPriority(tasks, match.groupValues[1], match.groupValues[2])
        }
        quickPriority.matchEntire(clean)?.let { match ->
            return setPriority(tasks, match.groupValues[1], "high")
        }

        if (lower in nextCommands) return nextTask(tasks)
        if (lower in briefingCommands) return briefing(tasks, completed, notes)
        return null
    }

    /** Explicit priority wins; otherwise earlier saved tasks win. */
    internal fun recommendedTask(tasks: List<String>): IndexedValue<String>? =
        tasks.withIndex().minWithOrNull(
            compareBy<IndexedValue<String>> { priorityRank(it.value) }
                .thenBy { it.index }
        )

    private fun priorityRank(task: String): Int = when {
        task.startsWith("[P1] ") -> 0
        task.startsWith("[P2] ") -> 1
        task.startsWith("[P3] ") -> 3
        else -> 2
    }

    private fun setPriority(tasks: MutableList<String>, number: String, level: String): String {
        val index = number.toIntOrNull()?.minus(1)
        if (index == null || index !in tasks.indices) {
            return "I couldn't find that task. Say 'show tasks' to see its number."
        }
        val tag = when (level.lowercase(Locale.ROOT)) {
            "high" -> "[P1]"
            "medium" -> "[P2]"
            else -> "[P3]"
        }
        val original = tasks[index].replace(priorityPrefix, "").trim()
        tasks[index] = "$tag $original"
        return "Priority updated for task " + (index + 1) + ": " + original +
            " (" + level.lowercase(Locale.ROOT) + ")."
    }

    private fun nextTask(tasks: List<String>): String {
        val chosen = recommendedTask(tasks)
            ?: return "There are no outstanding tasks. Try 'add task ...' to create one."
        val priority = when {
            chosen.value.startsWith("[P1] ") -> "High priority"
            chosen.value.startsWith("[P2] ") -> "Medium priority"
            chosen.value.startsWith("[P3] ") -> "Low priority"
            else -> "First unprioritized task"
        }
        val index = chosen.index + 1
        return "SUGGESTED NEXT ACTION\nTask " + index + ": " + chosen.value +
            "\n" + priority + ". This suggestion uses saved task order and explicit priority labels, " +
            "not automatic deadline or urgency detection.\n" +
            "To change it, say 'set task " + index + " priority high'."
    }

    private fun briefing(
        tasks: List<String>,
        completed: List<String>,
        notes: List<String>
    ): String {
        val total = tasks.size + completed.size
        val percent = if (total == 0) 0 else (completed.size.toLong() * 100L / total).toInt()
        val next = recommendedTask(tasks)
        val nextText = if (next == null) "Nothing pending."
            else "Task " + (next.index + 1) + ": " + next.value
        val preview = if (tasks.isEmpty()) {
            "No outstanding tasks."
        } else {
            tasks.take(3).mapIndexed { i, task -> (i + 1).toString() + ". " + task }
                .joinToString("\n") +
                if (tasks.size > 3) "\n...and " + (tasks.size - 3) + " more." else ""
        }
        return "ASTRA // MISSION CONTROL\n\n" +
            "TASKS  " + tasks.size + " open  |  " + completed.size + " completed\n" +
            "NOTES  " + notes.size + " stored\n" +
            "PROGRESS  " + percent + "% of recorded tasks completed\n\n" +
            "NEXT ACTION\n" + nextText + "\n\n" +
            "WORK QUEUE\n" + preview + "\n\n" +
            "I used your local saved information. No calendar, external data, " +
            "deadlines or autonomous actions were consulted."
    }
}
