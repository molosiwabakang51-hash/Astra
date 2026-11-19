package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraSubjectAtlasTest {
    @Test fun broadDisciplinesHaveSearchableOfflineOutlinesWithoutClaimingFullCourses() {
        val entries = AstraSubjectAtlas.entries
        assertEquals(285, entries.size)
        assertEquals(15, entries.map { it.category }.toSet().size)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertEquals(13 + entries.size, AstraStudyCatalog.subjects.size)
        for (entry in entries) {
            assertEquals(4, entry.modules.size)
            assertEquals(4, entry.modules.toSet().size)
            val subject = AstraStudyCatalog.find(entry.id)!!
            assertEquals(entry.title, subject.title)
            assertTrue(subject.description.contains(entry.category))
            // Physics now includes 19 real offline advanced workshops,
            // their roadmap and progress actions in addition to atlas entries.
            assertEquals(when (entry.id) {
                "physics" -> 27
                "computer_science" -> 26
                else -> 5
            }, subject.topics.size)
            assertEquals("atlas syllabus " + entry.id, subject.topics.first().command)
            assertFalse(subject.topics.first().requiresModel)
            assertEquals(4, subject.topics.drop(1).count { it.requiresModel })
            assertEquals("OFFLINE + AI", AstraStudyAccess.label(subject))
            val offline = AstraSubjectAtlas.respond("atlas syllabus " + entry.id)!!
            assertTrue(offline.contains("NOT A COMPLETE TAUGHT COURSE"))
            assertTrue(offline.contains(entry.title.uppercase()))
            for (module in entry.modules) assertTrue(offline.contains(module))
        }
    }

    @Test fun widerStudyLibraryCoversMajorAcademicAndPracticalFields() {
        for (name in listOf(
            "Physics", "General Chemistry", "Biology", "Neuroscience",
            "World History", "Political Science", "Law", "Human Rights",
            "Computer Science", "Artificial Intelligence", "Machine Learning",
            "Cybersecurity", "Electrical Engineering", "Graphic Design",
            "Health Informatics", "Public Health", "Agronomy", "Geology",
            "Insurance", "Architecture", "Music Theory", "Hausa Language",
            "Personal Finance", "Renewable Energy"
        )) {
            assertTrue("Missing: " + name,
                AstraSubjectAtlas.entries.any { it.title == name })
        }
    }

    @Test fun atlasRequestsAreBoundedAndDoNotPretendToKnowUnlistedLessons() {
        assertNull(AstraSubjectAtlas.respond("atlas syllabus nonexistent"))
        assertNull(AstraSubjectAtlas.respond("delete finance progress"))
        assertNull(AstraSubjectAtlas.respond("teach me quantum mechanics"))
        assertNull(AstraSubjectAtlas.respond("atlas syllabus " + "x".repeat(200)))
        assertNull(AstraSubjectAtlas.respond("atlas syllabus"))
        val physics = AstraSubjectAtlas.respond("ATLAS SYLLABUS physics")!!
        assertTrue(physics.contains("Physics".uppercase()))
        assertTrue(physics.contains("no"))
    }

    @Test fun subjectSearchFindsNamesAndModulesWithoutModel() {
        val chemistry = AstraSubjectAtlas.respond("atlas search chemical bonding")!!
        assertTrue(chemistry.contains("General Chemistry"))
        assertTrue(chemistry.contains("atlas syllabus general_chemistry"))
        val biology = AstraSubjectAtlas.respond("study search biology")!!
        assertTrue(biology.contains("ATLAS RESULTS"))
        val noMatch = AstraSubjectAtlas.respond("atlas search zzzzz")!!
        assertTrue(noMatch.contains("No subject matched"))
        assertNull(AstraSubjectAtlas.respond("atlas search a"))
        assertNull(AstraSubjectAtlas.respond("atlas search " + "x".repeat(200)))
    }

    @Test fun browseSubjectCategoriesAndGetExactRoadmapReferences() {
        val all = AstraSubjectAtlas.respond("atlas categories")!!
        assertTrue(all.contains("285 FIELDS"))
        assertTrue(all.contains("Physical Sciences"))
        assertTrue(all.contains("15 academic and practical categories"))
        val physics = AstraSubjectAtlas.respond("atlas category Physical Sciences")!!
        assertTrue(physics.contains("Physics: atlas syllabus physics"))
        assertTrue(physics.contains("Astronomy"))
        assertTrue(AstraSubjectAtlas.respond("atlas category notarealcategory")!!
            .contains("Unknown category"))
    }

    @Test fun advancedWorkshopCardsExposeRealChaptersAndProgressNotModelPrompts() {
        val expected = mapOf(
            "finance" to Pair("finance", 14),
            "mathematics" to Pair("math", 14),
            "english" to Pair("english", 15),
            "day_trading" to Pair("trading", 14),
            "french" to Pair("french", 16),
            "physics" to Pair("physics", 19),
            "russian" to Pair("russian", 18),
            "computer_science" to Pair("computing", 18)
        )
        for ((subjectId, courseInfo) in expected) {
            val subject = AstraStudyCatalog.find(subjectId)!!
            assertEquals(courseInfo.second, subject.topics.count {
                it.command.startsWith(courseInfo.first + " workshop ") &&
                    it.command.removePrefix(courseInfo.first + " workshop ")
                        .toIntOrNull() != null
            })
            val advanced = subject.topics.filter { it.title.startsWith("Workshop ") }
            assertTrue(advanced.isNotEmpty())
            assertTrue(advanced.none { it.requiresModel })
            assertTrue(subject.topics.any { it.command == courseInfo.first + " workshop" })
            assertTrue(subject.topics.any { it.command == courseInfo.first + " workshop progress" })
        }
    }
}
