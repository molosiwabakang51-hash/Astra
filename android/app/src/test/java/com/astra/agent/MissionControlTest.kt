package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MissionControlTest {
    private fun ask(
        command: String,
        tasks: MutableList<String>,
        completed: List<String> = emptyList(),
        notes: List<String> = emptyList()
    ): String? = MissionControl.respond(command, tasks, completed, notes)

    @Test fun noTasksGivesHonestBriefing() {
        val tasks = mutableListOf<String>()
        assertTrue(ask("mission control", tasks)!!.contains("Nothing pending"))
        assertTrue(ask("What's next?", tasks)!!.contains("no outstanding tasks"))
        assertTrue(ask("mission control", tasks)!!.contains("PROGRESS  0%"))
        assertEquals(null, ask("calculate 2 + 3", tasks))
    }

    @Test fun progressIsComputedFromRealRecordedTaskCounts() {
        val tasks = mutableListOf("Review bonds", "Read report")
        val result = ask("Brief me", tasks, listOf("Submit draft", "Check data"), listOf("Bond notes"))!!
        assertTrue(result.contains("2 open  |  2 completed"))
        assertTrue(result.contains("NOTES  1 stored"))
        assertTrue(result.contains("PROGRESS  50%"))
        assertTrue(result.contains("Task 1: Review bonds"))
        assertEquals(listOf("Review bonds", "Read report"), tasks)
    }

    @Test fun userAssignedPrioritiesTakePrecedenceWithoutReordering() {
        val tasks = mutableListOf("Read report", "Walk", "Review portfolio", "Study")
        assertTrue(ask("set task 3 priority high", tasks)!!.contains("Priority updated"))
        assertTrue(ask("mark task 4 priority low", tasks)!!.contains("Priority updated"))
        assertTrue(ask("set task 2 priority medium", tasks)!!.contains("Priority updated"))
        assertEquals("[P1] Review portfolio", tasks[2])
        assertEquals("[P2] Walk", tasks[1])
        assertEquals("[P3] Study", tasks[3])
        assertTrue(ask("what should I do next?", tasks)!!.contains("Task 3: [P1] Review portfolio"))
        assertTrue(ask("set task 3 to low", tasks)!!.contains("Priority updated"))
        assertEquals("[P3] Review portfolio", tasks[2])
        assertTrue(ask("what's next", tasks)!!.contains("Task 2: [P2] Walk"))
        assertTrue(ask("prioritize task 1", tasks)!!.contains("Priority updated"))
        assertEquals("[P1] Read report", tasks[0])
        assertEquals(4, tasks.size)
    }

    @Test fun invalidPrioritiesNeverMutateTasks() {
        val tasks = mutableListOf("Read report")
        val before = tasks.toList()
        for (bad in listOf("set task 0 high", "set task 9 priority high",
            "set task 999999999999999999999999 to medium")) {
            assertTrue(ask(bad, tasks)!!.contains("couldn't find"))
            assertEquals(before, tasks)
        }
    }

    @Test fun fortyThousandPriorityUpdatesNeverLoseTasks() {
        val random = Random(4401L)
        val tasks = (1..64).map { "Mission item $it" }.toMutableList()
        repeat(40_000) { step ->
            val index = random.nextInt(tasks.size)
            val level = listOf("high", "medium", "low")[random.nextInt(3)]
            assertTrue(
                "step=$step",
                ask("set task " + (index + 1) + " priority " + level, tasks)!!
                    .contains("Priority updated")
            )
            assertEquals(64, tasks.size)
            assertTrue(tasks[index].startsWith("[P"))
        }
        assertTrue(ask("mission control", tasks)!!.contains("64 open"))
    }
}
