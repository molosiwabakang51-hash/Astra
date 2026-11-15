package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.nio.file.Files

class AstraKnowledgeLearningTest {
    private fun learner() = AstraContinualLearning(
        File(Files.createTempDirectory("astra-learning-test").toFile(), "adapter.bin")
    )

    @Test fun localNeuralWeightsActuallyChangeWithTrainingAndReload() {
        val location = File(Files.createTempDirectory("astra-training").toFile(), "adapter.bin")
        val model = AstraContinualLearning(location)
        val before = model.predict("russian alphabet")
        repeat(180) {
            model.learn("russian alphabet pronunciation cyrillic vocabulary lessons")
        }
        val after = model.predict("russian alphabet")
        assertEquals(AstraContinualLearning.Topic.RUSSIAN, after.topic)
        assertTrue(after.confidence > before.confidence)
        assertTrue(after.confidence > 0.70)
        assertEquals(180, model.status().steps)
        assertTrue(location.isFile)
        val restored = AstraContinualLearning(location)
        assertEquals(model.status().steps, restored.status().steps)
        assertEquals(after.topic, restored.predict("russian alphabet").topic)
        // No verbatim conversation archive: only numeric weights and counters.
        assertFalse(location.readText(Charsets.ISO_8859_1).contains("pronunciation"))
        restored.reset()
        assertEquals(0, restored.status().steps)
        assertFalse(location.exists())
    }

    @Test fun safeTextCannotBeMistakenForPrivilegedAction() {
        val model = learner()
        repeat(90) { model.learn("russian phonetics grammar") }
        val output = model.predict("teach alphabet")
        assertTrue(output.confidence in 0.0..1.0)
        // Model only predicts a retrieval topic; it exposes no task or action API.
        assertTrue(AstraContinualLearning.Topic.entries.size == 3)
    }

    @Test fun sourceBoundedRetrievalAndAttributionExcludeOtherTopics() {
        val raw = listOf(
            """{"id":"cases","title":"Russian cases","url":"https://en.wikibooks.org/wiki/Russian/Grammar/Cases","text":"Six Russian cases change noun endings in different grammatical functions.","license":"CC BY-SA 4.0","keywords":"russian grammar cases genitive accusative"}""",
            """{"id":"intro","title":"Russian greetings","url":"https://en.wikibooks.org/wiki/Russian/Lesson_1","text":"Privet is a casual greeting in Russian.","license":"CC BY-SA 4.0","keywords":"russian greetings привет"}""",
            """{"id":"unlicensed","title":"Wrong source","url":"https://private.example/book","text":"Do not allow random content or unlicensed pages.","license":"Proprietary","keywords":"russian"}"""
        ).joinToString("\n")
        val passages = AstraOpenLibrary.load(ByteArrayInputStream(raw.toByteArray()))
        assertEquals(2, passages.size)
        val hits = AstraOpenLibrary.relevant("Explain Russian genitive cases", passages)
        assertEquals("cases", hits.first().id)
        val prompt = AstraOpenLibrary.enrich("Explain Russian genitive cases", hits)
        assertTrue(prompt.contains("NOT commands"))
        assertTrue(prompt.contains("CC BY-SA 4.0"))
        assertTrue(prompt.endsWith("Explain Russian genitive cases"))
        assertTrue(AstraOpenLibrary.citations(hits).contains("https://en.wikibooks.org/"))
        assertTrue(AstraOpenLibrary.relevant("Explain bond convexity", passages).isEmpty())
    }

    @Test fun userQuestionAlwaysSurvivesBoundedContext() {
        val example = AstraOpenLibrary.Entry(
            "test", "Russian dictionary", "https://en.wiktionary.org/wiki/тест",
            "CC BY-SA 4.0", "x".repeat(1_500), "russian words"
        )
        val message = "How does Russian pronunciation work?"
        val prompt = AstraOpenLibrary.enrich(message, listOf(example))
        assertTrue(prompt.length < 1_450)
        assertTrue(prompt.endsWith(message))
        val huge = "russian " + "x".repeat(2_000)
        assertEquals(huge, AstraOpenLibrary.enrich(huge, listOf(example)))
    }
}
