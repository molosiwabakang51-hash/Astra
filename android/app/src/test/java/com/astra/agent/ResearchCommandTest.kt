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

    @Test fun broadLearningRequestsAreNotWikipediaSearches() {
        // Real handset transcript: this previously searched Wikipedia for
        // 'and learn about everything' and returned unrelated film titles.
        assertNull(ResearchCommand.extract("research and learn about everything"))
        assertNull(ResearchCommand.extract("research and study bond duration"))
        assertNull(ResearchCommand.extract("research and understand this question"))
        assertNull(ResearchCommand.extract("research everything"))
        assertNull(ResearchCommand.extract("research all knowledge"))
        assertNull(ResearchCommand.extract("research my notes"))
        assertNull(ResearchCommand.extract("research our conversation"))
        assertEquals("the solar system",
            ResearchCommand.extract("research the solar system"))
    }

    @Test fun PrivateCommandsDoNotTriggerOnlineResearch() {
        assertNull(ResearchCommand.extract("show notes"))
        assertNull(ResearchCommand.extract("search notes for bonds"))
        assertNull(ResearchCommand.extract("what is my shopping list"))
        assertNull(ResearchCommand.extract("calculate 5 * 9"))
        assertNull(ResearchCommand.extract("research"))
    }
}
