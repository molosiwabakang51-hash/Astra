package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AstraWorkflowTest {
    private fun before() = AstraWorkflow.Snapshot(
        tasks = listOf("Review bond duration", "Finish assignment"),
        completed = emptyList(),
        notes = listOf("Diversification matters"),
        shopping = listOf("milk"),
        habits = listOf("walk")
    )

    @Test fun multiDomainPlanIsPreviewedButNeverExecutedUntilConfirmed() {
        val session = AstraWorkflowSession()
        val original = before()
        val prompt = "workflow: add task Study Russian; add bread to shopping list; " +
            "add note Verify convexity; add habit exercise; set task 2 priority high"
        val preview = session.handle(prompt, original)!!
        assertNull(preview.apply)
        assertTrue(preview.text.contains("WORKFLOW PREVIEW"))
        assertEquals(listOf("Review bond duration", "Finish assignment"), original.tasks)
        val committed = session.handle("confirm workflow", original)!!
        val result = committed.apply!!
        assertEquals("[P1] Finish assignment", result.tasks[1])
        assertEquals("Study Russian", result.tasks[2])
        assertTrue(result.shopping.contains("bread"))
        assertTrue(result.notes.contains("Verify convexity"))
        assertTrue(result.habits.contains("exercise"))
        assertNull(session.handle("confirm workflow", result)!!.apply)
    }

    @Test fun combinedCompletionAndPriorityAreAppliedInPreviewOrder() {
        val session = AstraWorkflowSession()
        val original = before()
        val response = session.handle(
            "batch: complete task 1; set task 1 priority low", original
        )!!
        assertTrue(response.text.contains("Review bond duration"))
        assertTrue(response.text.contains("Finish assignment"))
        val result = session.handle("confirm workflow", original)!!.apply!!
        assertEquals(listOf("[P3] Finish assignment"), result.tasks)
        assertEquals(listOf("Review bond duration"), result.completed)
    }

    @Test fun invalidLateStepAbortsAllSimulatedMutations() {
        val old = before()
        val session = AstraWorkflowSession()
        val plan = session.handle(
            "workflow: add task New task; add mango to shopping list; complete task 999", old
        )!!
        assertNull(plan.apply)
        assertTrue(plan.text.contains("Workflow rejected"))
        assertEquals(before(), old)
        assertNull(session.handle("confirm workflow", old)!!.apply)
    }

    @Test fun unsupportedStepCannotBecomeToolInvocation() {
        val session = AstraWorkflowSession()
        val old = before()
        for (unsafe in listOf(
            "workflow: add task a; delete all conversations",
            "workflow: add task b; send my notes to a server",
            "workflow: add task c; research private diary",
            "workflow: add task d; transfer P500 to someone"
        )) {
            assertNull(session.handle(unsafe, old)!!.apply)
            assertNull(session.handle("confirm workflow", old)!!.apply)
            assertEquals(before(), old)
        }
    }

    @Test fun malformedPlansAndResourceAbuseFailClosed() {
        val session = AstraWorkflowSession()
        val old = before()
        for (bad in listOf(
            "workflow:",
            "workflow: add task a;",
            "workflow: ; add task a",
            "workflow: add task " + "x".repeat(250),
            "workflow: add task " + "x".repeat(2500),
            "workflow: add task 1;" + "add task x;".repeat(13),
            "workflow: complete task 999999999999999999999999999",
            "workflow: add task a\n\nadd task b",
            "workflow: add task a; add task a",
            "workflow: add milk to shopping list",
            "workflow: add habit walk"
        )) {
            val response = session.handle(bad, old)
            assertNotNull(response)
            assertNull(response!!.apply)
            assertEquals(before(), old)
        }
    }

    @Test fun cancelledAndStalePlansCannotMakeChanges() {
        val session = AstraWorkflowSession()
        val old = before()
        session.handle("workflow: add task a", old)
        assertTrue(session.handle("cancel workflow", old)!!.text.contains("cancelled"))
        assertNull(session.handle("confirm workflow", old)!!.apply)
        session.handle("workflow: add task b", old)
        val modified = old.copy(tasks = old.tasks + "Concurrent change")
        val stale = session.handle("confirm workflow", modified)!!
        assertNull(stale.apply)
        assertTrue(stale.text.contains("changed"))
        session.handle("workflow: add task c", old)
        session.reset() // switching conversation, clearing or recreation
        assertNull(session.handle("confirm workflow", old)!!.apply)
    }

    @Test fun shoppingEncodingRoundTripsPunctuationAndUnicode() {
        val input = listOf("crème fraîche", "tea & biscuits", "apples, oranges", "100% cacao")
        assertEquals(input, AstraWorkflow.decodeItems(AstraWorkflow.encodeItems(input)))
        assertEquals(emptyList<String>(), AstraWorkflow.decodeItems(""))
    }

    @Test fun ordinaryQuestionsAreNotInterpretedAsCommands() {
        val snapshot = before()
        val session = AstraWorkflowSession()
        for (message in listOf(
            "research and learn about everything",
            "help", "russian", "what is convexity?",
            "workflow optimizer theory", "show tasks", "cancel", "yes",
            "send me an email", "plan my day"
        )) {
            assertNull("input=" + message, session.handle(message, snapshot))
        }
    }

    @Test fun stressTenThousandMalformedUntrustedInputsWithoutMutatingState() {
        val rand = Random(44115)
        val alphabet = "abc123 ;;?\n|=><&[]()@#Привет"
        val snapshot = before()
        val session = AstraWorkflowSession()
        repeat(10_000) {
            val value = buildString {
                append(if (rand.nextBoolean()) "workflow: " else "batch: ")
                repeat(rand.nextInt(180)) { append(alphabet[rand.nextInt(alphabet.length)]) }
            }
            val result = session.handle(value, snapshot)
            assertNotNull(result)
            assertNull("input=" + value, result!!.apply)
            assertEquals(before(), snapshot)
        }
        assertNull(session.handle("confirm workflow", snapshot)!!.apply)
    }

    @Test fun manyValidPlansProduceDeterministicIndependentPreviews() {
        val snapshot = before()
        val session = AstraWorkflowSession()
        repeat(1_000) { i ->
            val plan = session.handle(
                "workflow: add task test_" + i + "; add note finding_" + i, snapshot
            )!!
            assertNull(plan.apply)
            assertTrue(plan.text.contains("test_" + i))
            assertTrue(plan.text.contains("finding_" + i))
            assertEquals(before(), snapshot)
        }
        val after = session.handle("confirm workflow", snapshot)!!.apply!!
        assertEquals(3, after.tasks.size)
        assertEquals(2, after.notes.size)
        assertEquals("test_999", after.tasks.last())
        assertFalse(after.tasks.contains("test_998"))
    }
}
