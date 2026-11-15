package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraBookRankingTest {
    @Test fun titleOnlyOrGenericLanguageHitNeverOutranksActualSubject() {
        val question = AstraBookRanking.queryTerms("Explain Russian genitive grammar")
        val title = "Russian_Grammar_Textbook.md"
        val wrong = AstraBookRanking.score(
            question,
            "Russian alphabet and pronunciation are important for beginners.",
            title
        )
        val right = AstraBookRanking.score(
            question,
            "The Russian genitive case expresses possession and follows some prepositions.",
            title
        )
        assertEquals(0, wrong)
        assertTrue(right > wrong)
    }

    @Test fun EnglishCasePluralsAndDistinctTermsCanMatch() {
        val terms = AstraBookRanking.queryTerms("Explain the six Russian cases")
        assertTrue(terms.contains("case"))
        assertTrue(AstraBookRanking.score(
            terms,
            "The cases in Russian include nominative and genitive.",
            "Grammar.md"
        ) > 0)
    }

    @Test fun irrelevantBookTitleCannotBeCitedAsEvidence() {
        val q = AstraBookRanking.queryTerms("bond convexity")
        assertEquals(0, AstraBookRanking.score(
            q,
            "This is a chapter about Russian vocabulary and pronunciation.",
            "Bond_Convexity_Finance_Notes.md"
        ))
    }

    @Test fun excerptCentresOnAnswerNotBeginningOfLongParagraph() {
        val beginning = ("Russian textbook background and introductory history. ").repeat(13)
        val ending = "The genitive case expresses possession in Russian."
        val result = AstraBookRanking.excerpt(
            beginning + ending,
            "What does the genitive case mean?",
            210
        )
        assertTrue(result.contains("genitive case expresses possession"))
        assertFalse(result.startsWith("Russian textbook background"))
        assertTrue(result.length <= 215)
    }

    @Test fun veryLongUntrustedPromptsStayBoundedAndDeterministic() {
        val text = "This paragraph lists the instrumental case in Russian. ".repeat(40)
        val query = "Explain the instrumental case in a lesson"
        val first = AstraBookRanking.excerpt(text, query, 320)
        assertEquals(first, AstraBookRanking.excerpt(text, query, 320))
        assertTrue(first.length <= 322)
        assertTrue(AstraBookRanking.score(AstraBookRanking.queryTerms(query),
            text, "Russian grammar") > 0)
    }
}
