package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraOpenLibraryRelevanceTest {
    private val intro = AstraOpenLibrary.Entry(
        id = "ru-intro",
        title = "Russian greetings and introductions",
        url = "https://en.wikibooks.org/wiki/Russian/Lesson_1",
        license = "CC BY-SA 4.0",
        text = "Learn how to say hello in Russian: privet. Practice greetings and basic phrases.",
        keywords = "russian greetings russian lesson introduction hello language"
    )

    @Test fun ownerRussianBondQuestionCannotGetIrrelevantGreetingsCitation() {
        val prompts = listOf(
            "Объясни мне, как работают облигации.",
            "Как инфляция влияет на государственные облигации?",
            "Tell me about Russian government bonds",
            "Explain inflation in Russian",
            "Explain how bonds work in Russian",
            "What is the Russian central bank interest rate?"
        )
        prompts.forEach { question ->
            assertFalse(question, AstraOpenLibrary.isLanguageStudyQuestion(question))
            assertEquals(question, emptyList<AstraOpenLibrary.Entry>(),
                AstraOpenLibrary.relevant(question, listOf(intro)))
            assertEquals(question, emptyList<AstraOpenLibrary.Entry>(),
                AstraOpenLibrary.relevant(
                    question, listOf(intro), neuralRussianHint = true
                ))
        }
    }

    @Test fun actualLanguageLessonsStillUseRelevantLicensedExcerpts() {
        val questions = listOf(
            "Teach me Russian greetings",
            "Explain Russian genitive cases",
            "Learn Russian alphabet and phrases",
            "How to say hello in Russian?",
            "Practise Russian vocabulary",
            "Учить русский язык: приветствия"
        )
        questions.forEach { question ->
            assertTrue(question, AstraOpenLibrary.isLanguageStudyQuestion(question))
        }
        val hits = AstraOpenLibrary.relevant(
            "Teach me Russian greetings",
            listOf(intro)
        )
        assertEquals(listOf(intro), hits)
    }

    @Test fun sourceRelevanceStillRequiresLexicalEvidence() {
        assertEquals(emptyList<AstraOpenLibrary.Entry>(),
            AstraOpenLibrary.relevant("Teach me Russian punctuation", listOf(intro)))
        assertEquals(emptyList<AstraOpenLibrary.Entry>(),
            AstraOpenLibrary.relevant("Объясни мне, как работают облигации.",
                emptyList(), neuralRussianHint = true))
    }
}
