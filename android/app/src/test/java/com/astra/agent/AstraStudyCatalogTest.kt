package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraStudyCatalogTest {
    @Test fun studyLayoutProtectsNarrowScreensAndLargeFontScaling() {
        assertEquals(1, AstraStudyLayout.columnsFor(320f, 1f))
        assertEquals(1, AstraStudyLayout.columnsFor(360f, 1f))
        assertEquals(1, AstraStudyLayout.columnsFor(389f, 1f))
        assertEquals(2, AstraStudyLayout.columnsFor(390f, 1f))
        assertEquals(2, AstraStudyLayout.columnsFor(440f, 1.29f))
        assertEquals(1, AstraStudyLayout.columnsFor(440f, 1.3f))
        assertEquals(1, AstraStudyLayout.columnsFor(600f, 2f))
        assertEquals(1, AstraStudyLayout.columnsFor(Float.NaN, 1f))
        assertEquals(1, AstraStudyLayout.columnsFor(Float.POSITIVE_INFINITY, 1f))
        assertEquals(1, AstraStudyLayout.columnsFor(600f, Float.NaN))
        assertEquals(1, AstraStudyLayout.columnsFor(600f, 0f))
    }

    @Test fun studyOpensCatalogWithoutAutostartingFinance() {
        assertEquals("Study", AstraWelcomePolicy.commands.first())
        assertFalse(AstraWelcomePolicy.commands.first().contains("finance", ignoreCase = true))
        assertEquals(13, AstraStudyCatalog.subjects.size)
    }

    @Test fun russianStudyHubExposesRealOfflineLessonsAndProgress() {
        val russian = AstraStudyCatalog.find("russian")!!
        assertEquals("learn russian", russian.topics.first().command)
        assertEquals("russian quiz", russian.topics.last().command)
        assertTrue(russian.topics.size >= 23)
        for (number in 2..21) {
            assertEquals(1, russian.topics.count { it.command == "russian lesson $number" })
            assertTrue(russian.topics.first { it.command == "russian lesson $number" }
                .requiresModel.not())
        }
        assertEquals(1, russian.topics.count { it.command == "russian progress" })

        val values = mutableMapOf<String, Int>()
        val tutor = RussianTutor(
            readInt = { key, default -> values[key] ?: default },
            writeInt = { key, value -> values[key] = value }
        )
        for (number in listOf(2, 3, 6, 20, 21)) {
            val response = tutor.respond("russian lesson $number") ?: ""
            assertTrue("Existing offline tutor must serve lesson $number: $response",
                response.contains("Russian lesson $number"))
        }
        assertTrue(tutor.respond("russian progress").orEmpty().contains("Last lesson:"))
    }

    @Test fun userRequestedCoreSubjectsExist() {
        val available = AstraStudyCatalog.subjects.map { it.title }.toSet()
        assertTrue(available.containsAll(
            setOf("Finance", "Mathematics", "English", "Day Trading", "Russian", "French")
        ))
        assertTrue(available.containsAll(
            setOf("Setswana", "Spanish", "Portuguese", "German", "Swahili",
                "Japanese", "Mandarin")
        ))
    }

    @Test fun subjectsAndTopicsHaveUniqueNonblankNamesAndActions() {
        assertEquals(AstraStudyCatalog.subjects.size,
            AstraStudyCatalog.subjects.map { it.id }.toSet().size)
        for (subject in AstraStudyCatalog.subjects) {
            assertTrue(subject.topics.size >= 3)
            assertTrue(subject.id.isNotBlank())
            assertEquals(subject.topics.size,
                subject.topics.map { it.title }.toSet().size)
            for (topic in subject.topics) {
                assertTrue(topic.title.isNotBlank())
                assertTrue(topic.description.isNotBlank())
                assertTrue(topic.command.isNotBlank())
                assertEquals(topic, AstraStudyCatalog.topic(subject.id, topic.title))
            }
        }
        assertEquals(null, AstraStudyCatalog.find("not-a-course"))
        assertEquals(null, AstraStudyCatalog.topic("finance", "Not a topic"))
    }

    @Test fun nonBuiltInEnglishTradingAndAlgebraAreLabelledLocalAi() {
        for (id in listOf("english", "day_trading")) {
            val subject = AstraStudyCatalog.find(id)
            assertNotNull(subject)
            assertTrue(subject!!.topics.all { it.requiresModel })
        }
        assertTrue(AstraStudyCatalog.topic("mathematics", "Algebra")!!.requiresModel)
        assertFalse(AstraStudyCatalog.topic("russian", "Start Russian")!!.requiresModel)
        assertFalse(AstraStudyCatalog.topic("french", "Start French")!!.requiresModel)
    }

    @Test fun languageButtonsUseExistingOfflineTutorSyntax() {
        for (id in listOf("setswana", "spanish", "portuguese", "german",
            "swahili", "japanese", "mandarin", "french")) {
            assertEquals("learn " + id, AstraStudyCatalog.find(id)!!.topics.first().command)
        }
        assertEquals("russian quiz",
            AstraStudyCatalog.find("russian")!!.topics.last().command)
    }

    @Test fun newWelcomeOmitsDuplicateGreetingOnlyBeforeUserChats() {
        assertTrue(AstraWelcomePolicy.hideInitialGreeting(
            true, "Astra", "Hello. How can I help you?"
        ))
        assertFalse(AstraWelcomePolicy.hideInitialGreeting(
            false, "Astra", "Hello. How can I help you?"
        ))
        assertFalse(AstraWelcomePolicy.hideInitialGreeting(
            true, "You", "Hello. How can I help you?"
        ))
    }
}
