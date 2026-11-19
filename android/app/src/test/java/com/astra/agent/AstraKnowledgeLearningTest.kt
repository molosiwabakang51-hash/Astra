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
        assertEquals(20, AstraContinualLearning.Topic.entries.size)
        assertTrue(AstraContinualLearning.Topic.entries.contains(
            AstraContinualLearning.Topic.COMPUTING
        ))
    }

    @Test fun broadSubjectsCanTrainWithoutPersistingRawConversation() {
        val domains = mapOf(
            "Explain French grammar and pronouns" to AstraContinualLearning.Topic.FRENCH,
            "How does Russian verb aspect work" to AstraContinualLearning.Topic.RUSSIAN,
            "Explain stock index futures and margin call" to AstraContinualLearning.Topic.TRADING,
            "How do I solve calculus integrals?" to AstraContinualLearning.Topic.MATHEMATICS,
            "Explain Newton's laws in physics" to AstraContinualLearning.Topic.PHYSICS,
            "Explain organic chemistry" to AstraContinualLearning.Topic.CHEMISTRY,
            "Explain photosynthesis in biology" to AstraContinualLearning.Topic.BIOLOGY,
            "Explain an algorithm in computer science" to AstraContinualLearning.Topic.COMPUTING,
            "Teach me World War history" to AstraContinualLearning.Topic.HISTORY,
            "Explain contract law" to AstraContinualLearning.Topic.LAW,
            "Teach me creative writing poetry" to AstraContinualLearning.Topic.CREATIVE_WRITING,
            "What is Setswana grammar?" to AstraContinualLearning.Topic.SETSWANA
        )
        for ((message, expected) in domains) {
            val state = File(Files.createTempDirectory("astra-domain").toFile(), "adapter.bin")
            val learner = AstraContinualLearning(state)
            assertEquals(message, expected, learner.trainingLabel(message))
            // Exercise on-disk updates without repeating fsync hundreds of
            // times for each domain in a unit test.
            repeat(3) { learner.learn(message) }
            assertEquals(3, learner.status().steps)
            assertTrue(state.isFile)
            assertFalse(state.readText(Charsets.ISO_8859_1).contains(message))
        }
    }

    @Test fun v1TopicWeightsMigrateWithoutErasingLearningSteps() {
        val location = File(Files.createTempDirectory("astra-neural-v1").toFile(),
            "adapter.bin")
        java.io.DataOutputStream(location.outputStream()).use { output ->
            output.writeUTF("ASTRA_NEURAL_TOPIC_V1")
            output.writeInt(37)
            output.writeDouble(0.14)
            // 128 * 24 first-layer weights, then 24 biases.
            repeat(128 * 24 + 24) { output.writeDouble(0.005) }
            // V1 contained three 24-unit output rows and three biases.
            repeat(3 * 24 + 3) { output.writeDouble(0.03) }
        }
        val migrated = AstraContinualLearning(location)
        assertEquals(37, migrated.status().steps)
        assertEquals(0.14, migrated.status().lastLoss, 1e-9)
        migrated.learn("Explain neural networks and computer science")
        assertEquals(38, migrated.status().steps)
        val restored = AstraContinualLearning(location)
        assertEquals(38, restored.status().steps)
        assertEquals(AstraContinualLearning.Topic.entries.size, 20)
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


    @Test fun openCoursePromptContainsOnlyWholeCitableEvidence() {
        val large = (1..3).map { index ->
            AstraOpenLibrary.Entry(
                "course-$index", "Russian grammar course $index: vocabulary " +
                    "A".repeat(90), "https://en.wikibooks.org/wiki/Russian/" +
                    "long-source-".repeat(35),
                "CC BY-SA 4.0", "Case detail $index: " + "noun ending ".repeat(90),
                "russian grammar"
            )
        }
        val question = "Explain Russian cases."
        val prompt = AstraOpenLibrary.enrich(question, large)
        val cited = AstraOpenLibrary.includedEvidence(large, prompt)
        assertTrue(cited.isNotEmpty())
        assertTrue(cited.size < large.size)
        assertTrue(prompt.contains("QUESTION:\n" + question))
        assertTrue(prompt.length <= 1_250 + question.length)
        // An excerpt excluded by the 1,250-character limit cannot earn
        // a citation or leave a half-closed quote in the user's answer.
        assertEquals(large.take(cited.size), cited)
        assertEquals(cited.size, Regex("""(?m)^\[K\d+\] """).findAll(prompt).count())
        assertFalse(AstraOpenLibrary.citations(cited).contains("course 3"))
    }

    @Test fun savedResponseLanguageNeverCitesAnExcerptCutFromNativePrompt() {
        val sources = (1..3).map { index ->
            AstraOpenLibrary.Entry(
                "lesson-$index", "Russian grammar lesson $index",
                "https://en.wikibooks.org/wiki/Russian/Lesson_$index",
                "CC BY-SA 4.0",
                "Russian lesson number $index. " + "Case and ending. ".repeat(14),
                "russian grammar"
            )
        }
        val question = "Russian: " + "x".repeat(1_630)
        val enriched = AstraOpenLibrary.enrich(question, sources)
        val native = AstraReplyLanguage.prepare(
            AstraConversationContext.userPrompt(enriched, emptyList(), false),
            AstraReplyLanguage.Choice.FRENCH
        )
        val included = AstraOpenLibrary.includedEvidence(sources, native)
        assertTrue(native.length <= 3_000)
        assertTrue(native.endsWith(question + "\n/no_think"))
        assertTrue(included.size <= sources.size)
        assertEquals(sources.take(included.size), included)
        // A source line must be intact in the final model prompt, not merely
        // present in the earlier unbounded RAG candidate list.
        for (entry in included) {
            assertTrue(native.contains("Russian lesson number " +
                entry.id.substringAfterLast('-') + "."))
        }
    }

    @Test fun changedOrTrimmedOpenCourseQuotationCannotClaimGrounding() {
        val entry = AstraOpenLibrary.Entry(
            "russian-grammar", "Russian grammar", "https://en.wikibooks.org/wiki/Russian/Grammar",
            "CC BY-SA 4.0", "Russian cases vary by grammatical role.", "russian cases"
        )
        val source = listOf(entry)
        val question = "What are Russian cases?"
        val prompt = AstraOpenLibrary.enrich(question, source)
        assertEquals(source, AstraOpenLibrary.includedEvidence(source, prompt))
        val damaged = prompt.replace("Russian cases vary by grammatical role.",
            "Russian cases vary by gramma...")
        assertTrue(AstraOpenLibrary.includedEvidence(source, damaged).isEmpty())
        assertTrue(AstraOpenLibrary.includedEvidence(source, question).isEmpty())
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
