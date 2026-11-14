package com.astra.agent

/**
 * Normalizes polite spoken commands without turning arbitrary conversation into
 * privileged actions. The original user message is still kept in chat history.
 */
internal object AstraInput {
    private val whitespace = Regex("""\s+""")
    private val wakePrefix = Regex(
        """(?i)^(?:(?:hey|hi|okay|ok)\s+)?astra[,:\s]+(?=\S)"""
    )
    private val courtesyPrefix = Regex(
        """(?i)^(?:please\s+|could\s+you\s+|can\s+you\s+|would\s+you\s+|will\s+you\s+|i\s+(?:want|need)\s+you\s+to\s+)"""
    )

    fun normalize(raw: String): String {
        var text = raw.trim().replace(whitespace, " ")
        text = text.replaceFirst(wakePrefix, "").trim()
        repeat(3) {
            val trimmed = text.replaceFirst(courtesyPrefix, "").trim()
            if (trimmed == text) return@repeat
            text = trimmed
        }

        val lower = text.lowercase(java.util.Locale.ROOT).trimEnd('?', '!', '.')
        text = when (lower) {
            "what are my tasks", "list my tasks", "show me my tasks",
            "what's on my task list", "what is on my task list" -> "show tasks"
            "list my notes", "show me my notes", "read my notes" -> "show notes"
            "what is my progress", "how's my progress", "how is my progress" ->
                "show my progress"
            "give me a mission briefing", "tell me my status" -> "mission control"
            "what should i work on today", "help me prioritize my tasks" ->
                "what should i do next"
            "what can astra do", "what can you help me with" -> "help"
            else -> text
        }

        text = text.replaceFirst(
            Regex("""(?i)^make\s+(?:a\s+)?note\s+(?:that\s+)?"""),
            "remember that "
        )
        text = text.replaceFirst(
            Regex("""(?i)^add\s+a\s+task\s+to\s+"""),
            "add task "
        )
        return text.trim()
    }
}
