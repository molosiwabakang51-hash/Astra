package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResearchCommandTest {
    @Test fun explicitPublicResearchCommandsAreRecognized() {
        assertEquals("Jupiter", ResearchCommand.extract("research Jupiter"))
        assertEquals("Botswana", ResearchCommand.extract("search wikipedia for Botswana"))
        assertEquals("bond duration", ResearchCommand.extract("wikipedia bond duration"))
        assertEquals("Russian grammar", ResearchCommand.extract("look up on wikipedia Russian grammar"))
    }

    @Test fun PrivateCommandsDoNotTriggerOnlineResearch() {
        assertNull(ResearchCommand.extract("show notes"))
        assertNull(ResearchCommand.extract("search notes for bonds"))
        assertNull(ResearchCommand.extract("what is my shopping list"))
        assertNull(ResearchCommand.extract("calculate 5 * 9"))
        assertNull(ResearchCommand.extract("research"))
    }
}
