package com.astra.agent

import java.util.Locale

/**
 * A local, deterministic English grammar, reading and writing curriculum. Lessons and quiz answers are
 * original authored examples, not claims of neural reasoning or live tutoring.
 * Only small, subject-isolated integer counters/bitsets persist in Astra preferences.
 * No internet, third-party service, bundled model or file access is required.
 */
internal class AstraEnglishCourse(
    private val readInt: (String, Int) -> Int,
    private val writeInt: (String, Int) -> Unit
) {
    internal data class Lesson(
        val title: String,
        val concept: String,
        val workedExample: String,
        val note: String,
        val question: String,
        val choices: List<String>,
        val answer: Char,
        val explanation: String
    ) {
        init {
            require(choices.size == 3 && answer in 'A'..'C')
            require(title.isNotBlank() && question.isNotBlank())
        }
    }

    companion object {
        private const val LAST = "astra_english_last_lesson"
        private const val VISITED = "astra_english_lessons_visited"
        private const val MASTERED = "astra_english_quizzes_mastered"
        private const val PENDING = "astra_english_pending_quiz"
        private const val ATTEMPTS = "astra_english_quiz_attempts"
        private const val CORRECT = "astra_english_quiz_correct"

        val lessons: List<Lesson> = listOf(
            Lesson(
                "Nouns and verbs",
                "A noun names a person, place, thing or idea. A verb expresses an action or state.",
                "In 'The scientist studies plants', 'scientist' and 'plants' are nouns; 'studies' is a verb.",
                "One word can have different functions depending on the sentence.",
                "Which word is the verb in 'The scientist studies plants'? ",
                listOf("scientist", "studies", "plants"), 'B',
                "'Studies' tells us what the scientist does."
            ),
            Lesson(
                "Pronouns and clear reference",
                "A pronoun can replace a noun. Ensure readers know which person or thing 'he', 'she', 'it' or 'they' refers to.",
                "'Mpho forgot her notebook. She returned for it.' Here 'She' refers to Mpho and 'it' to the notebook.",
                "Ambiguous pronouns confuse readers when several possible nouns appear.",
                "In 'Mpho forgot her notebook. She returned for it', what does 'it' refer to?",
                listOf("Mpho", "her journey", "the notebook"), 'C',
                "The singular object 'notebook' is what Mpho returns for."
            ),
            Lesson(
                "Subjects and predicates",
                "A clause normally has a subject and a predicate. The subject tells who or what; the predicate describes what it does or is.",
                "In 'The tired students finished the test', 'The tired students' is the subject; 'finished the test' is the predicate.",
                "Questions and commands can use different word order; a command often has an understood 'you'.",
                "What is the subject of 'The tired students finished the test'?",
                listOf("The tired students", "finished the test", "the test"), 'A',
                "The subject tells us who completed the action."
            ),
            Lesson(
                "Present simple and continuous",
                "Use the present simple for habits and general truths. Use am/is/are + -ing for an action in progress.",
                "'She reads every evening' is a habit. 'She is reading now' is happening at this moment.",
                "State verbs such as 'know' are not generally used in the continuous form.",
                "Which sentence describes an action happening right now?",
                listOf("She reads every evening.", "She is reading now.", "She read yesterday."), 'B',
                "'Is reading' is the present continuous form."
            ),
            Lesson(
                "Past tense and irregular verbs",
                "Use the simple past for completed events. Many verbs take -ed, but irregular verbs change form.",
                "'I walked to class' is regular; 'I went to class' uses the irregular past of 'go'.",
                "Do not combine 'did' with a past-tense main verb: write 'Did you go?', not 'Did you went?'.",
                "Which is the correct past tense of 'go'?",
                listOf("goed", "gone", "went"), 'C',
                "'Went' is the simple past; 'gone' is a past participle."
            ),
            Lesson(
                "Future time and plans",
                "Use 'will' for many predictions or decisions made now. Use 'be going to' for intentions and predictions based on current evidence.",
                "'I will answer the phone.' 'I am going to revise tonight.' Both refer to future time.",
                "Neither construction guarantees that an event will happen.",
                "Which sentence expresses a plan using 'going to'?",
                listOf("I am going to revise tonight.", "I revised last night.", "I revise every night."), 'A',
                "'Am going to revise' expresses a future intention."
            ),
            Lesson(
                "Subject–verb agreement",
                "In the present simple, a singular third-person subject normally takes a verb ending in -s; plural subjects do not.",
                "'The list of books is long' agrees with the singular subject 'list', not the nearby plural 'books'.",
                "Identify the head of a long subject rather than the closest noun.",
                "Choose the correct sentence.",
                listOf("The list of books are long.", "The list of books is long.", "The list of books be long."), 'B',
                "'List' is singular, so use 'is'."
            ),
            Lesson(
                "Articles and determiners",
                "'A' and 'an' introduce a nonspecific singular count noun; 'the' refers to an identifiable noun. Choose a/an by sound, not spelling alone.",
                "'An orange' begins with a vowel sound; 'a university' begins with a /y/ sound.",
                "Uncountable nouns such as 'advice' do not normally take a/an directly.",
                "Which article is correct in '___ university'?",
                listOf("an", "the only possible article is an", "a"), 'C',
                "'University' starts with a consonant /y/ sound, so 'a university' is correct."
            ),
            Lesson(
                "Adjectives and adverbs",
                "An adjective modifies a noun; an adverb often modifies a verb, adjective or another adverb.",
                "'She is a careful driver' uses an adjective. 'She drives carefully' uses an adverb.",
                "Not every adverb ends with -ly, and some adjectives end in -ly.",
                "Which word correctly completes 'She drives ___'?",
                listOf("carefully", "careful", "care"), 'A',
                "The adverb 'carefully' describes how she drives."
            ),
            Lesson(
                "Prepositions of time and place",
                "Prepositions connect a noun or pronoun to another part of a sentence. Common time patterns include 'at 6', 'on Monday' and 'in July'.",
                "'The lesson starts at 8:00 on Monday in November' uses three different time prepositions.",
                "Many preposition choices are idiomatic; learn examples as phrases.",
                "Which preposition fits 'The meeting starts ___ 8:00'?",
                listOf("on", "at", "in"), 'B',
                "Use 'at' with a specific clock time."
            ),
            Lesson(
                "Coordinating and subordinating conjunctions",
                "Conjunctions join words or clauses. 'And' and 'but' often join equal units; 'because' introduces a reason clause.",
                "'We stayed inside because it rained' combines a complete clause and a dependent reason clause.",
                "A dependent clause alone is often a fragment in formal prose.",
                "Which word introduces a reason?",
                listOf("and", "but", "because"), 'C',
                "'Because' introduces the reason for staying inside."
            ),
            Lesson(
                "Punctuation and apostrophes",
                "A full stop ends a statement; commas can separate introductory phrases or list items. Apostrophes mark contractions or possession.",
                "'It's raining' means 'It is raining'. 'The dog wagged its tail' uses the possessive 'its' without an apostrophe.",
                "Do not use an apostrophe simply to form a plural noun.",
                "Choose the correct sentence.",
                listOf("The dog wagged its tail.", "The dog wagged it's tail.", "The dog wagged its' tail."), 'A',
                "'Its' is a possessive determiner; 'it's' means 'it is'."
            ),
            Lesson(
                "Complete sentences and fragments",
                "A complete declarative sentence normally has an independent clause. A dependent clause starting with 'because' needs a main clause.",
                "'Because it rained, we stayed indoors' is complete. 'Because it rained' alone is a fragment.",
                "Sentence fragments may be deliberate in fiction but weaken many academic paragraphs.",
                "Which is a complete sentence?",
                listOf("Because it rained.", "Because it rained, we stayed indoors.", "While the weather was changing."), 'B',
                "The second option has both a dependent clause and an independent clause."
            ),
            Lesson(
                "Paragraph structure",
                "A focused paragraph makes one main point, supports it with evidence or examples, and explains the link.",
                "Topic: 'Walking supports concentration.' Evidence: 'A brief walk can offer a study break.' Explanation: 'The pause may help students return with renewed attention.'",
                "Avoid unsupported sweeping claims; distinguish evidence from a personal illustration.",
                "What is the main purpose of a topic sentence?",
                listOf("To cite every source", "To repeat the conclusion word for word", "To state the paragraph's controlling idea"), 'C',
                "A topic sentence guides the supporting information in the paragraph."
            ),
            Lesson(
                "Vocabulary in context",
                "Use context clues, nearby examples and word parts to infer a meaning, then check a dictionary for precision.",
                "'The result was provisional; it could be revised after further tests.' 'Provisional' means temporary or subject to confirmation.",
                "A familiar-looking word can have a different meaning in a specialist context.",
                "In the example, 'provisional' most nearly means:",
                listOf("temporary", "certain and permanent", "completely false"), 'A',
                "The result may change after further testing, so it is not final."
            ),
            Lesson(
                "Reading and editing for clarity",
                "Distinguish what a passage states from what you infer. Edit for a clear actor, accurate verbs, specific claims and logical sequence.",
                "Original: 'Mistakes were made in the figures.' Clearer: 'The analyst entered two incorrect figures.' The revised version identifies the actor and action.",
                "A more specific sentence is only better when the added detail is known and supported.",
                "Which revision identifies the actor and action most clearly?",
                listOf("Errors occurred in several areas.", "The analyst corrected two figures.", "Several things were adjusted."), 'B',
                "The second sentence specifies who acted and what was done."
            )
        )
        private const val VALID_MASK = 0x0000FFFF
    }

    fun respond(raw: String): String? {
        val command = raw.trim().lowercase(Locale.ROOT).trimEnd('?', '!', '.').trim()
        if (command.length > 90) return null
        return when (command) {
            "english course", "english syllabus", "learn english", "start english course", "study english" -> syllabus()
            "english progress", "my english progress" -> progress()
            "next english lesson" -> lesson((readInt(LAST, 0) + 1).coerceIn(1, lessons.size))
            "english quiz", "quiz me on english" -> quiz(readInt(LAST, 1).coerceIn(1, lessons.size))
            "skip english quiz" -> {
                if (readInt(PENDING, 0) !in 1..lessons.size) {
                    "No English quiz is active. Say 'english quiz' to start one."
                } else {
                    writeInt(PENDING, 0)
                    "English quiz skipped. Say 'english quiz' to retry."
                }
            }
            else -> when {
                command.startsWith("answer english") -> answer(command)
                command.startsWith("english lesson ") ->
                    numbered(command.removePrefix("english lesson ")) { lesson(it) }
                command.startsWith("english quiz ") ->
                    numbered(command.removePrefix("english quiz ")) { quiz(it) }
                else -> null
            }
        }
    }

    private fun numbered(raw: String, handler: (Int) -> String): String {
        val number = raw.toIntOrNull()
        return if (number != null && number in 1..lessons.size)
            handler(number) else
            "Choose an English lesson from 1 to ${lessons.size}; say 'english course' for the syllabus."
    }

    private fun syllabus(): String = buildString {
        appendLine("ENGLISH FOUNDATION · ${lessons.size} OFFLINE LESSONS")
        appendLine("No model or internet required.")
        lessons.forEachIndexed { index, item ->
            appendLine("${index + 1}. ${item.title}")
        }
        appendLine()
        append("Choose 'english lesson 1', 'next english lesson', 'english quiz 1', or 'english progress'.")
    }.trim()

    private fun lesson(index: Int): String {
        val item = lessons[index - 1]
        writeInt(LAST, index)
        writeInt(VISITED, readInt(VISITED, 0) or (1 shl (index - 1)))
        return buildString {
            appendLine("ENGLISH LESSON $index/${lessons.size} · ${item.title.uppercase(Locale.ROOT)}")
            appendLine("OFFLINE FOUNDATION")
            appendLine()
            appendLine(item.concept)
            appendLine()
            appendLine("WORKED EXAMPLE")
            appendLine(item.workedExample)
            appendLine()
            appendLine("CHECK YOUR THINKING")
            appendLine(item.note)
            appendLine()
            append("Practice: 'english quiz $index'. " +
                if (index < lessons.size) "Continue: 'english lesson ${index + 1}'." else
                    "Course complete: 'english progress'.")
        }.trim()
    }

    private fun quiz(index: Int): String {
        val item = lessons[index - 1]
        writeInt(PENDING, index)
        return buildString {
            appendLine("ENGLISH QUIZ $index/${lessons.size} · ${item.title}")
            appendLine(item.question)
            item.choices.forEachIndexed { i, choice ->
                appendLine("${('A'.code + i).toChar()}. $choice")
            }
            append("Reply 'answer english A', B or C; or 'skip english quiz'.")
        }.trim()
    }

    private fun answer(command: String): String {
        val active = readInt(PENDING, 0)
        if (active !in 1..lessons.size) {
            return "No English quiz is active. Say 'english quiz 1' or 'english quiz'."
        }
        val choice = command.removePrefix("answer english").trim().uppercase(Locale.ROOT)
        if (choice !in listOf("A", "B", "C")) {
            return "Choose A, B or C using 'answer english A'."
        }
        val item = lessons[active - 1]
        val correct = choice[0] == item.answer
        writeInt(PENDING, 0)
        writeInt(ATTEMPTS, readInt(ATTEMPTS, 0).coerceIn(0, 1_000_000) + 1)
        if (correct) {
            writeInt(CORRECT, readInt(CORRECT, 0).coerceIn(0, 1_000_000) + 1)
            writeInt(MASTERED, readInt(MASTERED, 0) or (1 shl (active - 1)))
        }
        return (if (correct) "Correct." else "Not quite. Correct answer: ${item.answer}.") +
            " ${item.explanation}\n" +
            "Try 'english quiz $active' to review, or 'english progress' for your results."
    }

    private fun progress(): String {
        val visited = Integer.bitCount(readInt(VISITED, 0) and VALID_MASK)
        val mastered = Integer.bitCount(readInt(MASTERED, 0) and VALID_MASK)
        val last = readInt(LAST, 0).coerceIn(0, lessons.size)
        val attempts = readInt(ATTEMPTS, 0).coerceAtLeast(0)
        val correct = readInt(CORRECT, 0).coerceIn(0, attempts)
        return "ENGLISH COURSE PROGRESS · OFFLINE\n" +
            "Lessons opened: $visited/${lessons.size}. " +
            "Lesson quizzes mastered: $mastered/${lessons.size}.\n" +
            "Quiz answers: $correct/$attempts correct. " +
            "Last opened: ${if (last == 0) "none" else "$last/${lessons.size}"}.\n" +
            "Say 'english course' for the syllabus or 'next english lesson' to continue."
    }
}
