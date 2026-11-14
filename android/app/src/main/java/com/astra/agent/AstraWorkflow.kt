package com.astra.agent

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Offline, user-authored multi-operation workflows.
 *
 * No model output can invoke this parser. All steps are validated and
 * simulated before an explicit confirmation. The Android layer commits the
 * complete effect to one SharedPreferences transaction.
 */
internal object AstraWorkflow {
    private const val MAX_STEPS = 12
    private const val MAX_INPUT = 2400
    private const val MAX_VALUE = 220

    data class Snapshot(
        val tasks: List<String>,
        val completed: List<String>,
        val notes: List<String>,
        val shopping: List<String>,
        val habits: List<String>
    )

    sealed interface Operation {
        data class AddTask(val name: String) : Operation
        data class AddNote(val text: String) : Operation
        data class AddShopping(val item: String) : Operation
        data class RemoveShopping(val item: String) : Operation
        data class AddHabit(val name: String) : Operation
        data class Priority(val index: Int, val level: String) : Operation
        data class CompleteTask(val index: Int) : Operation
    }

    data class Simulation(val after: Snapshot, val summary: List<String>)
    sealed interface Parse {
        data object NotAWorkflow : Parse
        data class Invalid(val reason: String) : Parse
        data class Valid(val operations: List<Operation>) : Parse
    }

    private val prefix = Regex("""(?i)^(?:workflow|batch|do these)\s*:\s*""")
    private val task = Regex("""(?i)^(?:add (?:a )?task|create (?:a )?task)\s+(.+)$""")
    private val note = Regex("""(?i)^(?:add (?:a )?note|remember that|note)\s+(.+)$""")
    private val shoppingAdd = Regex("""(?i)^add\s+(.+?)\s+to\s+(?:my\s+)?shopping\s+list$""")
    private val shoppingRemove = Regex("""(?i)^remove\s+(.+?)\s+from\s+(?:my\s+)?shopping\s+list$""")
    private val habit = Regex("""(?i)^(?:add|track)\s+habit\s+(.+)$""")
    private val priority = Regex("""(?i)^set\s+task\s+(\d+)\s+priority\s+(high|medium|low)$""")
    private val complete = Regex("""(?i)^(?:complete\s+task|done)\s+(\d+)$""")
    private val taskTag = Regex("""^\[P[123]]\s*""")

    fun parse(raw: String): Parse {
        val text = raw.trim()
        if (!prefix.containsMatchIn(text)) return Parse.NotAWorkflow
        if (text.length > MAX_INPUT) {
            return Parse.Invalid("Workflow exceeds 2,400 characters.")
        }
        val body = text.replaceFirst(prefix, "").trim()
        if (body.isEmpty()) return Parse.Invalid("Add steps after 'workflow:'. Type 'workflow help'.")
        val commands = body.split(Regex("""[;\r\n]""")).map(String::trim)
        if (commands.size > MAX_STEPS) return Parse.Invalid("Maximum 12 workflow steps.")
        if (commands.any(String::isBlank)) return Parse.Invalid("Empty step between separators.")
        val steps = mutableListOf<Operation>()
        for ((index, command) in commands.withIndex()) {
            val op = parseStep(command) ?: return Parse.Invalid(
                "Unsupported workflow step " + (index + 1) + ": " + command.take(100) +
                    ". No changes were made. Type 'workflow help'."
            )
            steps.add(op)
        }
        return Parse.Valid(steps)
    }

    private fun parseStep(text: String): Operation? {
        fun checked(value: String): String? = value.trim().takeIf {
            it.isNotEmpty() && it.length <= MAX_VALUE && it.none { c -> c.isISOControl() }
        }
        task.matchEntire(text)?.let { return checked(it.groupValues[1])?.let(Operation::AddTask) }
        note.matchEntire(text)?.let { return checked(it.groupValues[1])?.let(Operation::AddNote) }
        shoppingAdd.matchEntire(text)?.let {
            return checked(it.groupValues[1])?.let(Operation::AddShopping)
        }
        shoppingRemove.matchEntire(text)?.let {
            return checked(it.groupValues[1])?.let(Operation::RemoveShopping)
        }
        habit.matchEntire(text)?.let { return checked(it.groupValues[1])?.let(Operation::AddHabit) }
        priority.matchEntire(text)?.let {
            val number = it.groupValues[1].toIntOrNull()?.minus(1) ?: return null
            return Operation.Priority(number, it.groupValues[2].lowercase(Locale.ROOT))
        }
        complete.matchEntire(text)?.let {
            val number = it.groupValues[1].toIntOrNull()?.minus(1) ?: return null
            return Operation.CompleteTask(number)
        }
        return null
    }

    /** No originals are mutated, including on late-step failure. */
    fun simulate(before: Snapshot, operations: List<Operation>): Result<Simulation> =
        runCatching {
            require(operations.isNotEmpty() && operations.size <= MAX_STEPS) {
                "The workflow must have 1 to 12 valid operations."
            }
            val tasks = before.tasks.toMutableList()
            val completed = before.completed.toMutableList()
            val notes = before.notes.toMutableList()
            val shopping = before.shopping.toMutableList()
            val habits = before.habits.toMutableList()
            val summary = mutableListOf<String>()
            fun exists(list: List<String>, value: String) =
                list.any { it.equals(value, ignoreCase = true) }
            operations.forEachIndexed { position, op ->
                val action = try {
                    when (op) {
                        is Operation.AddTask -> {
                            require(!exists(tasks, op.name)) { "Task already exists." }
                            tasks.add(op.name)
                            "Add task: " + op.name
                        }
                        is Operation.AddNote -> {
                            require(!exists(notes, op.text)) { "Note already exists." }
                            notes.add(op.text)
                            "Save note: " + op.text
                        }
                        is Operation.AddShopping -> {
                            require(!exists(shopping, op.item)) { "Shopping item already exists." }
                            shopping.add(op.item)
                            "Add shopping item: " + op.item
                        }
                        is Operation.RemoveShopping -> {
                            val index = shopping.indexOfFirst { it.equals(op.item, true) }
                            require(index >= 0) { "Shopping item not found: " + op.item }
                            "Remove shopping item: " + shopping.removeAt(index)
                        }
                        is Operation.AddHabit -> {
                            require(!exists(habits, op.name)) { "Habit already exists." }
                            habits.add(op.name)
                            "Track habit: " + op.name
                        }
                        is Operation.Priority -> {
                            require(op.index in tasks.indices) { "Task number is out of range." }
                            val name = tasks[op.index].replace(taskTag, "").trim()
                            val tag = when (op.level) {
                                "high" -> "[P1]"
                                "medium" -> "[P2]"
                                else -> "[P3]"
                            }
                            tasks[op.index] = tag + " " + name
                            "Set task " + (op.index + 1) + " priority " + op.level + ": " + name
                        }
                        is Operation.CompleteTask -> {
                            require(op.index in tasks.indices) { "Task number is out of range." }
                            val name = tasks.removeAt(op.index)
                            completed.add(name)
                            "Complete task " + (op.index + 1) + ": " + name
                        }
                    }
                } catch (failure: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Step " + (position + 1) + ": " + failure.message, failure
                    )
                }
                summary.add((position + 1).toString() + ". " + action)
            }
            Simulation(Snapshot(tasks, completed, notes, shopping, habits), summary)
        }

    fun encodeItems(items: List<String>): String =
        items.joinToString("\n") { URLEncoder.encode(it, "UTF-8") }

    fun decodeItems(raw: String): List<String> =
        raw.split('\n').filter(String::isNotEmpty)
            .mapNotNull { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    const val HELP = "Try 'workflow: add task Study finance; add milk to shopping list; " +
        "add habit exercise'. Other steps: add note, remove item from shopping list, " +
        "set task N priority high/medium/low, complete task N. Up to 12 steps " +
        "separated by semicolons or new lines. Astra previews them first. " +
        "Type 'confirm workflow' to apply, 'show workflow' or 'cancel workflow'."
}

/** Pending plans are session-only and are cleared on conversation changes. */
internal class AstraWorkflowSession {
    data class Response(val text: String, val apply: AstraWorkflow.Snapshot? = null)
    private data class Pending(
        val before: AstraWorkflow.Snapshot,
        val simulated: AstraWorkflow.Simulation
    )
    private var pending: Pending? = null

    fun reset() { pending = null }

    fun handle(raw: String, current: AstraWorkflow.Snapshot): Response? {
        val command = raw.trim().lowercase(Locale.ROOT)
        when (command) {
            "workflow help", "batch help" -> return Response(AstraWorkflow.HELP)
            "cancel workflow", "abort workflow" -> {
                val existed = pending != null
                pending = null
                return Response(if (existed) "Workflow cancelled. No changes were made."
                    else "No workflow pending.")
            }
            "show workflow", "workflow status" ->
                return pending?.let { Response(preview(it.simulated)) }
                    ?: Response("No workflow is pending. Type 'workflow help'.")
            "confirm workflow" -> {
                val staged = pending ?: return Response("No workflow awaiting confirmation.")
                pending = null
                if (current != staged.before) {
                    return Response(
                        "Saved information changed since the preview. " +
                            "Workflow discarded without changes. Review a new plan."
                    )
                }
                return Response(
                    "Applied " + staged.simulated.summary.size + " workflow steps locally.",
                    staged.simulated.after
                )
            }
        }
        return when (val parsed = AstraWorkflow.parse(raw)) {
            AstraWorkflow.Parse.NotAWorkflow -> null
            is AstraWorkflow.Parse.Invalid -> {
                pending = null
                Response(parsed.reason)
            }
            is AstraWorkflow.Parse.Valid -> {
                AstraWorkflow.simulate(current, parsed.operations).fold(
                    onSuccess = {
                        pending = Pending(current, it)
                        Response(preview(it))
                    },
                    onFailure = {
                        pending = null
                        Response("Workflow rejected; nothing changed. " +
                            (it.message ?: "Invalid operation."))
                    }
                )
            }
        }
    }

    private fun preview(sim: AstraWorkflow.Simulation) =
        "WORKFLOW PREVIEW · NO CHANGES YET\n\n" + sim.summary.joinToString("\n") +
            "\n\nType 'confirm workflow' to apply all steps, or 'cancel workflow'."
}
