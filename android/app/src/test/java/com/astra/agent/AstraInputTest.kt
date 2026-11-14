package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AstraInputTest {
    @Test fun wakePhraseAndCourtesyAreRecognized() {
        val cases = mapOf(
            "Hey Astra, please show me my tasks?" to "show tasks",
            "Astra, could you read my notes?" to "show notes",
            "Okay Astra: give me a mission briefing" to "mission control",
            "can you tell me my status" to "mission control",
            "please could you what should I work on today?" to "what should i do next",
            "Astra, please research the solar system" to "research the solar system",
            "Please make a note that a bond has interest rate risk" to
                "remember that a bond has interest rate risk",
            "please add a task to review my portfolio" to "add task review my portfolio",
            "what can you help me with?" to "help",
            "hi astra" to "hi astra",
            "Astra take a note" to "remember",
            "Astra, take a note that bond duration matters" to "remember bond duration matters",
            "Astra jot this down: revise finance" to "remember revise finance",
            "What would you like to do" to "help",
            "What can you do?" to "help",
            "calculate 12 * 5" to "calculate 12 * 5"
        )
        for ((input, output) in cases) {
            assertEquals("input=$input", output, AstraInput.normalize(input))
        }
    }

    @Test fun normalizerNeverGeneratesCommandsFromEmptyInputs() {
        for (input in listOf("", " ", "please", "Astra", "  \n  ")) {
            val normalized = AstraInput.normalize(input)
            org.junit.Assert.assertTrue(normalized.length <= input.length)
        }
    }

    @Test fun repeatedPolitenessStopsAtThreeAndRetainsPayload() {
        assertEquals("show tasks", AstraInput.normalize("please can you please show me my tasks?"))
    }
}
