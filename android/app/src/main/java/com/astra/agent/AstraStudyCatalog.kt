package com.astra.agent

/**
 * Visible Study catalog. Selecting a subject never silently starts a lesson.
 * Topic actions use existing deterministic offline commands where available.
 * AI-only lessons are clearly distinguished from built-in offline tools.
 */
internal object AstraStudyCatalog {
    data class Topic(
        val title: String,
        val description: String,
        val command: String,
        val requiresModel: Boolean = false
    )
    data class Subject(
        val id: String,
        val title: String,
        val symbol: String,
        val description: String,
        val topics: List<Topic>
    )
    val subjects: List<Subject> = listOf(
        Subject("finance", "Finance", "◈", "Investing, markets and money", listOf(
            Topic("Compound interest", "Understand how returns accumulate.", "explain compound interest"),
            Topic("Bond duration", "Explore interest-rate sensitivity.", "explain bond duration"),
            Topic("Diversification", "Learn how portfolio risk is spread.", "explain diversification"),
            Topic("Dividend yield", "Learn an income-investing metric.", "explain dividend yield")
        )),
        Subject("mathematics", "Mathematics", "∑", "Numbers, algebra and finance math", listOf(
            Topic("Arithmetic", "Work through a multiplication example.", "calculate 12 * 5"),
            Topic("Percentages", "Calculate 20% of 500.", "20 percent of 500"),
            Topic("Compound growth", "Learn an application of exponents.", "explain compound interest"),
            Topic("Algebra", "Open a guided algebra explanation.", "Teach me algebra with a simple worked example", true)
        )),
        Subject("english", "English", "Aa", "Grammar, vocabulary and writing", listOf(
            Topic("Grammar essentials", "Parts of speech and sentence structure.", "Teach me English grammar, starting with parts of speech", true),
            Topic("Vocabulary", "Definitions, context and practice.", "Teach me five English vocabulary words with examples and a quiz", true),
            Topic("Writing", "Build clear paragraphs.", "Teach me to write a clear English paragraph and give me an exercise", true)
        )),
        Subject("day_trading", "Day Trading", "↗", "Markets, chart reading and risk", listOf(
            Topic("Market mechanics", "Orders, spreads and liquidity.", "Teach me day trading basics: order types, spreads and liquidity", true),
            Topic("Reading charts", "Candles, trend and volume.", "Teach me candlesticks, trends and volume using hypothetical examples", true),
            Topic("Risk management", "Position sizing and loss controls.", "Teach me trading risk management and position sizing, with hypothetical numbers", true)
        )),
        Subject("russian", "Russian", "Я", "Cyrillic, grammar and conversation", listOf(
            Topic("Start Russian", "Begin offline Russian lessons.", "learn russian"),
            Topic("Cyrillic", "Practice the Russian alphabet.", "russian lesson 3"),
            Topic("Vocabulary quiz", "Test your Russian knowledge.", "russian quiz")
        )),
        Subject("french", "French", "Fr", "Phrases, vocabulary and grammar", listOf(
            Topic("Start French", "Begin offline French lessons.", "learn french"),
            Topic("Lesson two", "Continue to the second lesson.", "french lesson 2"),
            Topic("Vocabulary quiz", "Practice the language.", "french quiz")
        )),
        Subject("setswana", "Setswana", "Ts", "Local language and everyday phrases", listOf(
            Topic("Start Setswana", "Begin the offline course.", "learn setswana"),
            Topic("Lesson two", "Explore more useful phrases.", "setswana lesson 2"),
            Topic("Vocabulary quiz", "Review what you learned.", "setswana quiz")
        )),
        Subject("spanish", "Spanish", "Es", "Everyday communication", listOf(
            Topic("Start Spanish", "Begin offline lessons.", "learn spanish"),
            Topic("Lesson two", "Advance your vocabulary.", "spanish lesson 2"),
            Topic("Vocabulary quiz", "Check your progress.", "spanish quiz")
        )),
        Subject("portuguese", "Portuguese", "Pt", "Useful phrases and conversation", listOf(
            Topic("Start Portuguese", "Begin offline lessons.", "learn portuguese"),
            Topic("Lesson two", "Progress to lesson two.", "portuguese lesson 2"),
            Topic("Vocabulary quiz", "Practice and review.", "portuguese quiz")
        )),
        Subject("german", "German", "De", "Vocabulary and everyday language", listOf(
            Topic("Start German", "Begin offline lessons.", "learn german"),
            Topic("Lesson two", "Progress to lesson two.", "german lesson 2"),
            Topic("Vocabulary quiz", "Practice your knowledge.", "german quiz")
        )),
        Subject("swahili", "Swahili", "Sw", "East African language practice", listOf(
            Topic("Start Swahili", "Begin offline lessons.", "learn swahili"),
            Topic("Lesson two", "Progress to lesson two.", "swahili lesson 2"),
            Topic("Vocabulary quiz", "Practice your knowledge.", "swahili quiz")
        )),
        Subject("japanese", "Japanese", "日", "Vocabulary and expressions", listOf(
            Topic("Start Japanese", "Begin offline lessons.", "learn japanese"),
            Topic("Lesson two", "Progress to lesson two.", "japanese lesson 2"),
            Topic("Vocabulary quiz", "Practice your knowledge.", "japanese quiz")
        )),
        Subject("mandarin", "Mandarin", "中", "Chinese phrases and vocabulary", listOf(
            Topic("Start Mandarin", "Begin offline lessons.", "learn mandarin"),
            Topic("Lesson two", "Progress to lesson two.", "mandarin lesson 2"),
            Topic("Vocabulary quiz", "Practice your knowledge.", "mandarin quiz")
        ))
    ).map { subject ->
        // The existing multilingual tutor already supports 21 offline lessons
        // per included course. Surface those *real* chapters instead of
        // disguising a three-button preview as the whole curriculum.
        if (subject.id == "russian") {
            // The existing RussianTutor includes six structured introductions
            // and enough bundled vocabulary for 21+ offline lessons. Surface
            // real topics in Study without inventing content or calling an LLM.
            // Keep the quiz as the last action for stable navigation contracts.
            subject.copy(
                topics = listOf(subject.topics.first()) +
                    (2..21).map { number ->
                        val title = when (number) {
                            2 -> "Lesson 2 — Everyday phrases"
                            3 -> "Lesson 3 — Cyrillic"
                            4 -> "Lesson 4 — Numbers"
                            5 -> "Lesson 5 — Russian cases"
                            6 -> "Lesson 6 — Verb aspect"
                            else -> "Lesson $number — Vocabulary"
                        }
                        Topic(
                            title,
                            if (number <= 6) "Open the built-in Russian lesson."
                            else "Practice a new group of offline Russian vocabulary.",
                            "russian lesson $number"
                        )
                    } +
                    listOf(
                        Topic("Course progress", "Review Russian lessons and quiz progress.",
                            "russian progress"),
                        subject.topics.last()
                    )
            )
        } else if (subject.id in setOf("setswana", "french", "spanish", "portuguese",
                "german", "swahili", "japanese", "mandarin")) {
            subject.copy(
                topics = listOf(subject.topics.first()) +
                    (2..21).map { number ->
                        Topic(
                            "Lesson " + number,
                            "Open offline vocabulary lesson " + number + " of 21.",
                            subject.id + " lesson " + number
                        )
                    } +
                    listOf(
                        subject.topics.last(),
                        Topic("Course progress", "Check stored quiz and lesson progress.",
                            subject.id + " progress")
                    )
            )
        } else subject
    }
    fun find(id: String?): Subject? = subjects.firstOrNull { it.id == id }
    fun topic(subjectId: String, title: String): Topic? =
        find(subjectId)?.topics?.firstOrNull { it.title == title }
}
