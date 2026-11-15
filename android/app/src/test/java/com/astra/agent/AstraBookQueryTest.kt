package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AstraBookQueryTest {
    @Test fun explicitBookCommandKeepsNaturalQuestionIntact() {
        assertEquals("Explain Russian verb aspects",
            AstraBookQuery.extract("ask book Explain Russian verb aspects"))
        assertEquals("What is a genitive?",
            AstraBookQuery.extract("ASK MY BOOKS What is a genitive?"))
        assertEquals("Russian grammar",
            AstraBookQuery.extract("ask book  Russian grammar "))
    }

    @Test fun ordinaryAndSensitiveCommandsNeverAccessPrivateBooksByDefault() {
        assertNull(AstraBookQuery.extract("russian"))
        assertNull(AstraBookQuery.extract("research Russian grammar"))
        assertNull(AstraBookQuery.extract("show my notes"))
        assertNull(AstraBookQuery.extract("ask book"))
        assertNull(AstraBookQuery.extract("ask my books"))
    }
}
