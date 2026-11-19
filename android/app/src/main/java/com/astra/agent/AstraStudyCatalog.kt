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
    val subjects: List<Subject> = (listOf(
        Subject("finance", "Finance", "◈", "Investing, markets and money", listOf(
            Topic("Compound interest", "Understand how returns accumulate.", "explain compound interest"),
            Topic("Bond duration", "Explore interest-rate sensitivity.", "explain bond duration"),
            Topic("Diversification", "Learn how portfolio risk is spread.", "explain diversification"),
            Topic("Dividend yield", "Learn an income-investing metric.", "explain dividend yield")
        )),
        Subject("mathematics", "Mathematics", "∑", "Numbers, algebra and finance math", listOf(
            Topic("Maths course", "View the 16-lesson offline curriculum.", "math course")
        ) + AstraMathCourse.lessons.mapIndexed { index, lesson ->
            Topic(
                "Lesson ${index + 1} — ${lesson.title}",
                "Worked example and independent self-check quiz.",
                "math lesson ${index + 1}"
            )
        } + listOf(
            Topic("Maths progress", "Review your mastered quizzes and next lesson.", "math progress"),
            Topic("Maths quiz", "Test your most recently opened lesson.", "math quiz")
        )),
        Subject("english", "English", "Aa", "Grammar, vocabulary and writing", listOf(
            Topic("English course", "Browse the complete 16-lesson offline course.", "english course")
        ) + AstraEnglishCourse.lessons.mapIndexed { index, lesson ->
            Topic(
                "Lesson ${index + 1} — ${lesson.title}",
                "Read a clear explanation, worked example and practice quiz.",
                "english lesson ${index + 1}"
            )
        } + listOf(
            Topic("English progress", "Review lesson and quiz mastery stored on this phone.",
                "english progress"),
            Topic("English quiz", "Test the most recently opened lesson.",
                "english quiz")
        )),
        Subject("day_trading", "Day Trading", "↗", "Market structure, charting and risk", listOf(
            Topic("Trading course", "Explore 16 model-free foundational lessons.", "trading course")
        ) + AstraTradingCourse.lessons.mapIndexed { index, lesson ->
            Topic(
                "Lesson ${index + 1} — ${lesson.title}",
                "Study a hypothetical example and a self-check question.",
                "trading lesson ${index + 1}"
            )
        } + listOf(
            Topic("Trading progress", "Review saved lessons and quiz mastery.", "trading progress"),
            Topic("Trading quiz", "Test the most recently opened lesson.", "trading quiz")
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
        if (subject.id == "finance") {
            // These are real self-contained lessons, not model prompts. Keep
            // the original quick-reference finance topics as shortcuts.
            subject.copy(
                topics = listOf(
                    Topic("Course roadmap", "Browse 20 complete offline foundation lessons.",
                        "finance course")
                ) + AstraFinanceCourse.lessons.mapIndexed { index, lesson ->
                    Topic(
                        "Lesson ${index + 1} — ${lesson.title}",
                        "Read a worked example, assumptions and an optional quiz.",
                        "finance lesson ${index + 1}"
                    )
                } + listOf(
                    Topic("Quiz current lesson", "Test the lesson you last opened.",
                        "finance quiz"),
                    Topic("Course progress", "Track opened lessons and quiz mastery.",
                        "finance progress")
                ) + subject.topics
            )
        } else if (subject.id == "russian") {
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
    } + AstraSubjectAtlas.entries.map { entry ->
        Subject(
            id = entry.id,
            title = entry.title,
            symbol = "◇",
            description = entry.category + " · offline syllabus roadmap, optional local AI",
            topics = listOf(
                Topic(
                    "Offline syllabus",
                    "A structured four-module roadmap, not a complete taught course.",
                    "atlas syllabus " + entry.id
                )
            ) + entry.modules.mapIndexed { index, module ->
                Topic(
                    "Explore " + (index + 1) + " — " + module,
                    "Optional on-device AI discussion. Verify facts against reliable materials.",
                    "Teach me " + module + " within " + entry.title +
                        ". Explain important ideas, equations or evidence, " +
                        "then give worked examples and a self-check. " +
                        "Distinguish facts from uncertainties.",
                    requiresModel = true
                )
            }
        )
    }).map { subject ->
        val course = when (subject.id) {
            "finance" -> "finance"
            "mathematics" -> "math"
            "english" -> "english"
            "day_trading" -> "trading"
            "french" -> "french"
            "physics" -> "physics"
            "russian" -> "russian"
            "computer_science" -> "computing"
            else -> null
        }
        if (course == null) subject else {
            // Keep the existing Russian quiz as the final card: the original
            // offline tutor and navigation tests depend on that contract.
            val existing = if (subject.id == "russian") {
                subject.topics.dropLast(1)
            } else subject.topics
            subject.copy(topics = existing + listOf(
                Topic(
                    "Advanced workshop roadmap",
                    "Deep practical modules beyond the original foundation chapters.",
                    course + " workshop"
                )
            ) + AstraMasterclass.units(course).mapIndexed { index, unit ->
                Topic(
                    "Workshop " + (index + 1) + " — " + unit.title,
                    "Detailed theory, a worked case, an independent exercise and mastery quiz.",
                    course + " workshop " + (index + 1)
                )
            } + listOf(
                Topic("Workshop quiz", "Assess the last opened advanced workshop.",
                    course + " workshop quiz"),
                Topic("Workshop progress", "Review advanced chapter mastery stored privately.",
                    course + " workshop progress")
            ) + if (subject.id == "russian") {
                listOf(subject.topics.last())
            } else emptyList())
        }
    }
    fun find(id: String?): Subject? = subjects.firstOrNull { it.id == id }
    fun topic(subjectId: String, title: String): Topic? =
        find(subjectId)?.topics?.firstOrNull { it.title == title }
}
