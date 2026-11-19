package com.astra.agent

import java.util.Locale

/**
 * Expanded multi-stage offline courses. Each workshop has an explanation,
 * a worked case, an independent exercise and a graded mastery question.
 * Every subject uses disjoint local progress keys and all commands are bounded.
 *
 * This advanced track supplements the existing 16–20 foundation lessons.
 * It does not claim that finishing a curriculum establishes professional
 * competence; users must practise and consult authoritative source materials.
 */
internal class AstraMasterclass(
    private val readInt: (String, Int) -> Int,
    private val writeInt: (String, Int) -> Unit
) {
    internal data class Unit(
        val title: String,
        val level: String,
        val explanation: String,
        val workedCase: String,
        val independentExercise: String,
        val question: String,
        val choices: List<String>,
        val answer: Char,
        val rationale: String
    ) {
        init {
            require(title.isNotBlank() && level.isNotBlank())
            require(explanation.length >= 180)
            require(workedCase.length >= 95)
            require(independentExercise.length >= 80)
            require(question.isNotBlank() && rationale.isNotBlank())
            require(choices.size == 3 && choices.distinct().size == 3 && answer in 'A'..'C')
        }
    }

    companion object {
        val tracks: Map<String, List<Unit>> = linkedMapOf(
            "finance" to AstraMasterFinance.units,
            "math" to AstraMasterMath.units,
            "english" to AstraMasterEnglish.units,
            "trading" to AstraMasterTrading.units
        )
        private val subjectNames = mapOf(
            "finance" to "Finance",
            "math" to "Mathematics",
            "english" to "English",
            "trading" to "Day Trading"
        )

        fun units(subject: String): List<Unit> = tracks[subject].orEmpty()
    }

    private fun mark(subject: String, suffix: String, index: Int) {
        require(index >= 1)
        val segment = (index - 1) / 30
        val bit = 1 shl ((index - 1) % 30)
        val part = suffix + "_" + segment
        write(subject, part, read(subject, part) or bit)
    }

    private fun countMarked(subject: String, suffix: String, total: Int): Int =
        (0 until ((total + 29) / 30)).sumOf { segment ->
            val remaining = total - segment * 30
            val width = minOf(remaining, 30)
            val mask = (1 shl width) - 1
            Integer.bitCount(read(subject, suffix + "_" + segment) and mask)
        }

    private fun key(subject: String, suffix: String) =
        "astra_masterclass_" + subject + "_" + suffix
    private fun read(subject: String, suffix: String, default: Int = 0): Int =
        readInt(key(subject, suffix), default)
    private fun write(subject: String, suffix: String, value: Int) =
        writeInt(key(subject, suffix), value)

    fun respond(raw: String): String? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ").trimEnd('.', '?', '!')
        if (text.length > 90) return null
        val match = Regex("""^(finance|math|english|trading) (?:masterclass|workshop)(?:\s+(.*))?$""")
            .matchEntire(text) ?: return null
        val subject = match.groupValues[1]
        val cmd = match.groupValues[2].trim()
        val list = units(subject)
        if (list.isEmpty()) return null
        return when {
            cmd.isBlank() || cmd == "course" || cmd == "syllabus" ->
                syllabus(subject, list)
            cmd == "progress" -> progress(subject, list)
            cmd == "next" -> show(subject,
                (read(subject, "last") + 1).coerceIn(1, list.size), list)
            cmd == "quiz" -> quiz(subject,
                read(subject, "last", 1).coerceIn(1, list.size), list)
            cmd.startsWith("quiz ") -> {
                val n = cmd.removePrefix("quiz ").toIntOrNull()
                if (n == null || n !in 1..list.size) range(subject, list)
                else quiz(subject, n, list)
            }
            cmd.startsWith("answer ") -> answer(subject,
                cmd.removePrefix("answer ").trim(), list)
            cmd.startsWith("lesson ") -> {
                val n = cmd.removePrefix("lesson ").toIntOrNull()
                if (n == null || n !in 1..list.size) range(subject, list)
                else show(subject, n, list)
            }
            cmd.toIntOrNull() != null -> {
                val n = cmd.toIntOrNull()!!
                if (n !in 1..list.size) range(subject, list)
                else show(subject, n, list)
            }
            else -> null
        }
    }

    private fun range(subject: String, list: List<Unit>) =
        "Select a " + subject + " workshop from 1 to " + list.size +
            ". Type '" + subject + " masterclass' for the curriculum."

    private fun syllabus(subject: String, list: List<Unit>): String = buildString {
        appendLine(subjectNames.getValue(subject).uppercase(Locale.ROOT) +
            " · EXTENDED OFFLINE WORKSHOPS · " + list.size + " CHAPTERS")
        appendLine("These are detailed practical chapters beyond the foundation lessons.")
        appendLine("Each chapter has an explanation, worked case, task and mastery check.")
        appendLine()
        list.forEachIndexed { i, item ->
            appendLine((i + 1).toString() + ". [" + item.level + "] " + item.title)
        }
        appendLine()
        append("Open '" + subject + " workshop 1'; complete '" +
            subject + " workshop quiz 1'; check '" + subject + " workshop progress'.")
    }.trim()

    private fun show(subject: String, number: Int, list: List<Unit>): String {
        val unit = list[number - 1]
        write(subject, "last", number)
        mark(subject, "visited", number)
        return buildString {
            appendLine(subjectNames.getValue(subject).uppercase(Locale.ROOT) +
                " · WORKSHOP " + number + "/" + list.size)
            appendLine(unit.title.uppercase(Locale.ROOT) + " · " + unit.level)
            appendLine()
            appendLine("DETAILED THEORY")
            appendLine(unit.explanation)
            appendLine()
            appendLine("WORKED CASE")
            appendLine(unit.workedCase)
            appendLine()
            appendLine("INDEPENDENT PRACTICE")
            appendLine(unit.independentExercise)
            appendLine()
            appendLine("SELF-CHECK")
            append("Try '" + subject + " workshop quiz " + number +
                "' for a scored concept question. " +
                (if (number < list.size) "Next: '" + subject +
                    " workshop " + (number + 1) + "'." else
                    "End of current expanded track; review progress."))
        }.trim()
    }

    private fun quiz(subject: String, number: Int, list: List<Unit>): String {
        write(subject, "pending", number)
        val unit = list[number - 1]
        return buildString {
            appendLine(subjectNames.getValue(subject).uppercase(Locale.ROOT) +
                " · WORKSHOP QUIZ " + number + "/" + list.size)
            appendLine(unit.question)
            unit.choices.forEachIndexed { i, choice ->
                appendLine(('A'.code + i).toChar().toString() + ". " + choice)
            }
            append("Respond '" + subject + " workshop answer A', B or C.")
        }.trim()
    }

    private fun answer(subject: String, choice: String, list: List<Unit>): String {
        val pending = read(subject, "pending")
        if (pending !in 1..list.size) {
            return "No " + subject + " workshop quiz is active."
        }
        val selected = choice.uppercase(Locale.ROOT)
        if (selected !in setOf("A", "B", "C")) {
            return "Choose A, B or C; the active question is still open."
        }
        val unit = list[pending - 1]
        val correct = selected[0] == unit.answer
        write(subject, "pending", 0)
        write(subject, "attempts", read(subject, "attempts").coerceAtMost(999_999) + 1)
        if (correct) {
            write(subject, "correct", read(subject, "correct").coerceAtMost(999_999) + 1)
            mark(subject, "mastered", pending)
        }
        return (if (correct) "Correct." else "Not quite. Answer: " + unit.answer + ".") +
            " " + unit.rationale + "\nNext: '" +
            subject + " workshop " + (pending + 1).coerceAtMost(list.size) +
            "' or '" + subject + " workshop progress'."
    }

    private fun progress(subject: String, list: List<Unit>): String {
        // Segment local bitsets so expanded tracks can grow beyond 30 chapters.
        val visited = countMarked(subject, "visited", list.size)
        val mastered = countMarked(subject, "mastered", list.size)
        val last = read(subject, "last").coerceIn(0, list.size)
        val attempts = read(subject, "attempts").coerceAtLeast(0)
        val correct = read(subject, "correct").coerceIn(0, attempts)
        return subjectNames.getValue(subject) + " · WORKSHOP PROGRESS\n" +
            "Chapters opened: " + visited + "/" + list.size + ". " +
            "Mastery quizzes passed: " + mastered + "/" + list.size + ".\n" +
            "Quiz attempts: " + attempts + "; correct: " + correct + ". " +
            "Last chapter: " + (if (last == 0) "none" else last.toString()) + ".\n" +
            "A passed short quiz is only one measure; finish the independent exercise."
    }
}
