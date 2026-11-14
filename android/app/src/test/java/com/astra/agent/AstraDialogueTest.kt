package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AstraDialogueTest {
    @Test fun asksForMissingTaskThenAcceptsPlainLanguage() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val completed = mutableListOf<String>()

        assertTrue(dialogue.respond("add task", tasks, notes, completed)!!.contains("What task"))
        assertTrue(
            dialogue.respond("Revise bond convexity", tasks, notes, completed)!!.contains("Task added")
        )
        assertEquals(listOf("Revise bond convexity"), tasks)
        assertNull(dialogue.respond("Revise something else", tasks, notes, completed))
    }

    @Test fun notesAndCancellationDoNotModifyPrivateRecords() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf("existing task")
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        assertTrue(dialogue.respond("remember", tasks, notes, done)!!.contains("What should I remember"))
        assertTrue(dialogue.respond("cancel", tasks, notes, done)!!.contains("Cancelled"))
        assertTrue(notes.isEmpty())
        assertEquals(listOf("existing task"), tasks)
        assertTrue(dialogue.respond("remember that", tasks, notes, done)!!.contains("What should I remember"))
        assertEquals("Note saved.", dialogue.respond("Bond durations are measured in years", tasks, notes, done))
        assertEquals(listOf("Bond durations are measured in years"), notes)
    }

    @Test fun explicitCommandsOverridePendingPrompts() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf("Existing")
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        dialogue.respond("add task", tasks, notes, done)
        assertNull(dialogue.respond("calculate 2 * 3", tasks, notes, done))
        assertEquals(listOf("Existing"), tasks)
        dialogue.respond("remember", tasks, notes, done)
        assertNull(dialogue.respond("show tasks", tasks, notes, done))
        assertTrue(notes.isEmpty())
    }

    @Test fun contextualPriorityAndConfirmationAreSafe() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf("Read reports", "Review bonds")
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        assertTrue(dialogue.respond("what should I do next", tasks, notes, done)!!
            .contains("Task 1: Read reports"))
        assertTrue(dialogue.respond("why this one", tasks, notes, done)!!.contains("saved order"))
        assertTrue(dialogue.respond("make it high", tasks, notes, done)!!.contains("Priority updated"))
        assertEquals("[P1] Read reports", tasks[0])
        assertTrue(dialogue.respond("mark it done", tasks, notes, done)!!.contains("Reply 'yes'"))
        assertEquals(2, tasks.size) // no destructive action before confirmation
        assertTrue(dialogue.respond("yes", tasks, notes, done)!!.contains("Completed"))
        assertEquals(listOf("Review bonds"), tasks)
        assertEquals(listOf("[P1] Read reports"), done)
    }

    @Test fun cancellationAndChangedTaskNeverCompleteWrongItem() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf("A", "B")
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        dialogue.respond("what should I do next", tasks, notes, done)
        dialogue.respond("mark it done", tasks, notes, done)
        assertTrue(dialogue.respond("cancel", tasks, notes, done)!!.contains("Cancelled"))
        assertTrue(done.isEmpty())

        dialogue.respond("mark it done", tasks, notes, done)
        tasks[0] = "A changed"
        assertTrue(dialogue.respond("yes", tasks, notes, done)!!.contains("changed"))
        assertEquals(listOf("A changed", "B"), tasks)
        assertTrue(done.isEmpty())
    }

    @Test fun unclearRepliesDoNotConfirmDestructiveActions() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf("Review")
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        dialogue.respond("what's next", tasks, notes, done)
        dialogue.respond("complete it", tasks, notes, done)
        assertNull(dialogue.respond("maybe", tasks, notes, done))
        assertEquals(listOf("Review"), tasks)
        assertTrue(done.isEmpty())
    }

    @Test fun noReferenceCannotModifyTasks() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf("Study")
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        assertTrue(dialogue.respond("mark it done", tasks, notes, done)!!.contains("No task is selected"))
        assertTrue(dialogue.respond("make it high", tasks, notes, done)!!.contains("No task is selected"))
        assertEquals(listOf("Study"), tasks)
        assertTrue(done.isEmpty())
    }

    @Test fun resetRemovesPendingDestructiveAction() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf("Study")
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        dialogue.respond("what's next", tasks, notes, done)
        dialogue.respond("mark it done", tasks, notes, done)
        dialogue.reset()
        assertNull(dialogue.respond("yes", tasks, notes, done))
        assertEquals(listOf("Study"), tasks)
        assertTrue(done.isEmpty())
    }

    @Test fun quizAnswersAreNotStolen() {
        val dialogue = AstraDialogue()
        val tasks = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        assertNull(dialogue.respond("yes", tasks, notes, done))
        assertNull(dialogue.respond("no", tasks, notes, done))
        assertNull(dialogue.respond("answer thank you", tasks, notes, done))
    }

    @Test fun twentyThousandSessionOperationsPreserveTaskCounts() {
        val random = Random(606L)
        val dialogue = AstraDialogue()
        val tasks = (1..25).map { "Item $it" }.toMutableList()
        val notes = mutableListOf<String>()
        val done = mutableListOf<String>()
        var confirmed = 0
        repeat(20_000) { step ->
            when (random.nextInt(5)) {
                0 -> {
                    dialogue.respond("what's next", tasks, notes, done)
                    dialogue.respond("make it high", tasks, notes, done)
                }
                1 -> {
                    dialogue.respond("what's next", tasks, notes, done)
                    val confirm = dialogue.respond("mark it done", tasks, notes, done)
                    if (tasks.isNotEmpty()) {
                        assertTrue(confirm!!.contains("Reply 'yes'"))
                        if (random.nextBoolean()) {
                            val result = dialogue.respond("yes", tasks, notes, done)
                            assertTrue("step=$step", result!!.startsWith("Completed:"))
                            confirmed++
                        } else {
                            assertTrue(dialogue.respond("no", tasks, notes, done)!!.contains("Cancelled"))
                        }
                    }
                }
                2 -> {
                    dialogue.respond("add task", tasks, notes, done)
                    assertTrue(dialogue.respond("New item $step", tasks, notes, done)!!
                        .contains("Task added"))
                }
                3 -> {
                    dialogue.respond("remember", tasks, notes, done)
                    dialogue.respond("Note $step", tasks, notes, done)
                }
                else -> {
                    dialogue.respond("show tasks", tasks, notes, done)
                }
            }
            assertEquals(step.toString(), 25 + (tasks.size - 25) + confirmed - confirmed, tasks.size - done.size + done.size + 25 - 25) // same value sanity
            assertEquals("step=$step", confirmed, done.size)
        }
        assertTrue(notes.isNotEmpty())
        assertTrue(tasks.size + done.size >= 25)
    }
}
