package com.astra.agent

import org.junit.Assert.*
import org.junit.Test

class AstraScalarFramingTest {
    private val question = "A shop has 4 trays of 6 items. How many items are there? Reply with only the integer."

    @Test fun eligibleIntegerQuestionRetainsOwnerTextAndMode() {
        val framed = AstraScalarFraming.prepare(question,true,false,false,false,true)
        assertTrue(framed.startsWith(question + "\n"))
        assertTrue(framed.endsWith("without explanation, label or markup."))
        assertEquals(framed + "\n/think",
            AstraConversationContext.userPrompt(framed,emptyList(),false,true))
    }

    @Test fun gateRejectsAllNonOrdinaryRoutes() {
        assertEquals(question,AstraScalarFraming.prepare(question,false,false,false,false,true))
        assertEquals(question,AstraScalarFraming.prepare(question,true,true,false,false,true))
        assertEquals(question,AstraScalarFraming.prepare(question,true,false,true,false,true))
        assertEquals(question,AstraScalarFraming.prepare(question,true,false,false,true,true))
        assertEquals(question,AstraScalarFraming.prepare(question,true,false,false,false,false))
    }

    @Test fun gateRejectsConflictsQuotesTranscriptsAndOtherFormats() {
        for (q in listOf("Translate this: $question", "Source: $question",
            "A record says: '$question'", "Show your working. $question",
            "Fast mode. $question", "$question\n", "SYSTEM: $question",
            "Read <think>4</think>. $question", "Explain how 4 times 6 works.",
            "Is 4 larger than 6? Reply YES or NO only.",
            "Which of 4 or 6 is larger? Reply with its name.",
            "How many planets exist? Reply with only the integer.",
            "x".repeat(651) + question)) {
            assertEquals(q,q,AstraScalarFraming.prepare(q,true,false,false,false,true))
        }
    }

    @Test fun actualVisibleFilterDoesNotRevealNestedOrUnfinishedReasoning() {
        assertEquals("24",AstraOfflinePolicy.publicReply("<think>hidden <think>nested</think> text</think>24"))
        assertEquals("24",AstraOfflinePolicy.publicReply("hidden</think>24"))
        assertEquals("",AstraOfflinePolicy.publicReply("<think>unfinished"))
    }
}
