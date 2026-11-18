package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/** The six real menu destinations and their drawn icons must stay aligned. */
class AstraNavigationGlyphTest {
    @Test fun iconOrderMatchesNavigationMenuOrder() {
        assertEquals(
            listOf(
                AstraNavSection.CHAT,
                AstraNavSection.TASKS,
                AstraNavSection.NOTES,
                AstraNavSection.TOOLS,
                AstraNavSection.SETTINGS,
                AstraNavSection.STUDY
            ),
            AstraNavSection.entries
        )
    }
}
