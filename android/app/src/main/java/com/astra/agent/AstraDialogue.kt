package com.astra.agent

import java.util.Locale

/**
 * A small, honest conversation state machine for existing offline skills.
 *
 * This is not generative AI. It handles underspecified commands, references
 * to the most recently suggested task and confirmation before completion.
 * Unfinished prompts are session-only and are not written to disk.
 */
internal class AstraDialogue {
    private sealed interface Pending {
        data object TaskText : Pending
        data object NoteText : Pending
        data class CompleteTask(val index: Int, val text: String) : Pending
    }

    private data class SuggestedTask(val index: Int, val text: String)

    private var pending: Pending? = null
    private var suggested: SuggestedTask? = null

    val awaitingResponse: Boolean
        get() = pending != null

    fun dismissIncompletePrompt() {
        pending = null
    }

    private val taskRequests = setOf(
        "add task", "add a task", "create task", "new task",
        "remind me to", "add a new task"
    )
    private val noteRequests = setOf(
        "remember", "remember that", "add note", "save note",
        "make a note", "make a note that", "new note"
    )
    private val yes = setOf("yes", "yes please", "confirm", "do it", "proceed")
    private val no = setOf("no", "no thanks", "cancel", "never mind", "nevermind", "stop")
    private val taskDone = setOf(
        "mark it done", "mark that done", "mark it complete",
        "mark that complete", "complete it", "complete that task",
        "finish it", "finish that task", "i finished it", "i've done it"
    )
    private val taskWhy = setOf(
        "why that task", "why this task", "why this one", "why that one",
        "why is that next", "why should i do that"
    )
    private val contextPriority = Regex(
        """(?i)^(?:(?:make|mark|set)\s+(?:it|that|this\s+task)\s+(?:(?:priority)\s+)?|(?:set|change)\s+(?:its|that\s+task's)\s+priority\s+(?:to\s+)?)(high|medium|low)$"""
    )
    private val commands = Regex(
        """(?i)^(?:add\s+(?:a\s+)?task|create\s+task|task\s+|done\s+\d+|complete\s+task\s+\d+|show\s+|list\s+|set\s+task\s+|prioriti[sz]e\s+task\s+|remember\s+|note\s+|search\s+|find\s+|research\s+|wikipedia\s+|calculate\s+|compute\s+|convert\s+|russian\s+|teach\s+|translate\s+|help$|mission\s+control$|plan\s+my\s+day$|open\s+|clear\s+chat$|share\s+chat$|copy\s+chat$).*"""
    )

    fun respond(
        raw: String,
        tasks: MutableList<String>,
        notes: MutableList<String>,
        completed: MutableList<String>
    ): String? {
        val input = raw.trim().trimEnd('?', '!', '.').trim()
        val lower = input.lowercase(Locale.ROOT)

        val waiting = pending
        if (waiting != null) {
            if (lower in no) {
                pending = null
                return "Cancelled. No changes made."
            }
            when (waiting) {
                is Pending.CompleteTask -> {
                    if (lower in yes) {
                        pending = null
                        val current = tasks.getOrNull(waiting.index)
                        if (current != waiting.text) {
                            suggested = null
                            return "The task list changed before confirmation. " +
                                "I did not complete anything. Ask 'what's next' again."
                        }
                        tasks.removeAt(waiting.index)
                        completed.add(waiting.text)
                        suggested = null
                        return "Completed: " + waiting.text
                    }
                    // Never treat an unrelated message as permission to change tasks.
                    pending = null
                }
                Pending.TaskText, Pending.NoteText -> {
                    if (lower.isBlank() || lower in yes || input.length > 300) {
                        return if (waiting == Pending.TaskText) {
                            "Give me a task name (up to 300 characters), or say 'cancel'."
                        } else {
                            "Give me the note to save (up to 300 characters), or say 'cancel'."
                        }
                    }
                    if (!commands.matches(input) && !looksLikeQuestion(lower)) {
                        pending = null
                        return if (waiting == Pending.TaskText) {
                            tasks.add(input)
                            "Task added: " + input
                        } else {
                            notes.add(input)
                            "Note saved."
                        }
                    }
                    // A new explicit command takes precedence over a missing field.
                    pending = null
                }
            }
        }

        if (lower in taskRequests) {
            pending = Pending.TaskText
            return "What task should I add? Reply with the task name, or say 'cancel'."
        }
        if (lower in noteRequests) {
            pending = Pending.NoteText
            return "What should I remember? Reply with the note, or say 'cancel'."
        }

        val missionResponse = MissionControl.respond(input, tasks, completed, notes)
        if (missionResponse != null) {
            if (missionResponse.startsWith("SUGGESTED NEXT ACTION")) {
                suggested = MissionControl.recommendedTask(tasks)?.let {
                    SuggestedTask(it.index, it.value)
                }
            }
            // Explicit task-priority edits might have changed the referenced text.
            if (missionResponse.startsWith("Priority updated")) {
                suggested?.let { old ->
                    val revised = tasks.getOrNull(old.index)
                    suggested = if (revised == null) null else old.copy(text = revised)
                }
            }
            return missionResponse
        }

        if (lower in taskWhy || lower in taskDone || contextPriority.matches(input)) {
            val reference = suggested
                ?: return "No task is selected. Ask 'what should I do next?' first."
            if (tasks.getOrNull(reference.index) != reference.text) {
                suggested = null
                return "That task has changed. Ask 'what should I do next?' for a current suggestion."
            }
            if (lower in taskWhy) {
                return "I suggested task " + (reference.index + 1) + " because Astra " +
                    "uses your saved order and explicit [P1], [P2], [P3] priority tags. " +
                    "It does not estimate urgency or detect deadlines."
            }
            if (lower in taskDone) {
                pending = Pending.CompleteTask(reference.index, reference.text)
                return "Complete task " + (reference.index + 1) + ": " +
                    reference.text + "? Reply 'yes' to confirm or 'cancel'."
            }
            val priority = contextPriority.matchEntire(input)?.groupValues?.get(1)
            if (priority != null) {
                val result = MissionControl.respond(
                    "set task " + (reference.index + 1) + " priority " + priority,
                    tasks, completed, notes
                )
                suggested = tasks.getOrNull(reference.index)?.let {
                    SuggestedTask(reference.index, it)
                }
                return result
            }
        }

        if (lower in yes || lower in no) {
            // Do not consume simple Yes/No answers: Russian practice may use them.
            return null
        }
        return null
    }

    fun reset() {
        pending = null
        suggested = null
    }

    private fun looksLikeQuestion(input: String): Boolean =
        listOf("what ", "why ", "when ", "where ", "how ", "who ", "can you ", "could you ")
            .any { input.startsWith(it) }
}
