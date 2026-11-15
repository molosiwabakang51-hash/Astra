package com.astra.agent

import java.util.Locale

/**
 * Small deterministic, local-first language tutor. This is not an LLM.
 *
 * Progress keys are intentionally stored alongside Astra's existing preferences;
 * no migration or change to existing notes, tasks, or chat history is required.
 */
internal class RussianTutor(
    private val readInt: (String, Int) -> Int,
    private val writeInt: (String, Int) -> Unit
) {
    private data class Card(
        val russian: String,
        val romanized: String,
        val english: String,
        val acceptedAnswers: Set<String>
    )

    private val cards = listOf(
        Card("Спасибо", "spasibo", "Thank you", setOf("thank you", "thanks")),
        Card("Да", "da", "Yes", setOf("yes")),
        Card("Нет", "net", "No", setOf("no")),
        Card("Привет", "privet", "Hi", setOf("hi", "hello")),
        Card("Пожалуйста", "pozhaluysta", "Please", setOf("please", "you are welcome", "you're welcome")),
        Card("До свидания", "do svidaniya", "Goodbye", setOf("goodbye", "bye")),
        Card("Доброе утро", "dobroye utro", "Good morning", setOf("good morning")),
        Card("Добрый вечер", "dobryy vecher", "Good evening", setOf("good evening")),
        Card("Как дела", "kak dela", "How are you", setOf("how are you", "how's it going")),
        Card("Хорошо", "khorosho", "Fine", setOf("fine", "good", "well")),
        Card("Извините", "izvinite", "Excuse me", setOf("excuse me", "sorry")),
        Card("Вода", "voda", "Water", setOf("water")),
        Card("Хлеб", "khleb", "Bread", setOf("bread")),
        Card("Книга", "kniga", "Book", setOf("book")),
        Card("Дом", "dom", "House", setOf("house", "home")),
        Card("Школа", "shkola", "School", setOf("school")),
        Card("Друг", "drug", "Friend", setOf("friend")),
        Card("Семья", "semya", "Family", setOf("family")),
        Card("Один", "odin", "One", setOf("one")),
        Card("Два", "dva", "Two", setOf("two")),
        Card("Три", "tri", "Three", setOf("three")),
        Card("Четыре", "chetyre", "Four", setOf("four")),
        Card("Пять", "pyat", "Five", setOf("five")),
        Card("Сегодня", "segodnya", "Today", setOf("today")),
        Card("Завтра", "zavtra", "Tomorrow", setOf("tomorrow")),
        Card("Вчера", "vchera", "Yesterday", setOf("yesterday")),
        Card("Где", "gde", "Where", setOf("where")),
        Card("Кто", "kto", "Who", setOf("who")),
        Card("Читать", "chitat", "To read", setOf("to read", "read")),
        Card("Писать", "pisat", "To write", setOf("to write", "write"))
    )

    fun respond(raw: String): String? {
        val input = raw.trim()
            .trimEnd('?', '!', '.')
            .lowercase(Locale.ROOT)
            .trim()

        when (input) {
            "russian", "russian language", "teach me russian", "learn russian", "russian lesson",
            "start russian lessons", "russian lesson 1" ->
                return lesson(1)

            "russian lesson 2" -> return lesson(2)
            "russian lesson 3", "russian alphabet", "learn cyrillic" -> return lesson(3)
            "russian lesson 4", "russian numbers", "russian vocabulary" -> return lesson(4)
            "russian lesson 5", "russian grammar", "russian cases" -> return lesson(5)
            "russian lesson 6", "russian verbs", "russian verb aspect" -> return lesson(6)
            "next russian lesson" ->
                return lesson((readInt(KEY_LAST_LESSON, 1) + 1).coerceIn(1, 6))
            "russian course", "russian syllabus" ->
                return "Six offline Russian lessons: 1 Greetings; 2 Everyday phrases; " +
                    "3 Cyrillic; 4 Numbers and vocabulary; 5 The six cases; " +
                    "6 Verbs and aspect. Say 'Russian lesson 3', 'next Russian lesson', " +
                    "or 'Russian quiz'. These lessons work without an AI model."

            "russian quiz", "quiz me in russian", "quiz me on russian",
            "practice russian", "russian practice" ->
                return askQuestion()

            "russian progress", "my russian progress" -> {
                val answered = readInt(KEY_CORRECT, 0).coerceAtLeast(0)
                val lastLesson = readInt(KEY_LAST_LESSON, 0).coerceIn(0, 6)
                return "Russian practice: ${answered} correct answer(s). " +
                    "${cards.size} vocabulary cards available. " +
                    "Last lesson: ${if (lastLesson == 0) "none" else "$lastLesson/6"}. " +
                    "Say 'Russian quiz' to practise or 'Russian course' for the syllabus."
            }
        }

        val pendingIndex = readInt(KEY_PENDING, 0) - 1
        if (pendingIndex !in cards.indices) {
            if (input == "answer" || input.startsWith("answer ")) {
                return "There is no active Russian question. Say 'Russian quiz' to begin."
            }
            return null
        }

        if (input == "answer") {
            return "Please give the English meaning of '${cards[pendingIndex].russian}'. " +
                "Reply 'answer <translation>', or say 'skip Russian question'."
        }

        if (input == "skip russian question" || input == "skip russian") {
            writeInt(KEY_PENDING, 0)
            writeInt(KEY_NEXT, (pendingIndex + 1) % cards.size)
            return "Skipped. ${cards[pendingIndex].russian} means ${cards[pendingIndex].english}. Say 'Russian quiz' to continue."
        }

        val explicitlyAnswering = input.startsWith("answer ")
        val answer = if (explicitlyAnswering) input.removePrefix("answer ").trim() else input
        if (explicitlyAnswering && answer.isBlank()) {
            return "Please include your translation after 'answer'. The current word is '${cards[pendingIndex].russian}'."
        }

        // Do not intercept unrelated assistant commands while a quiz is pending.
        if (!explicitlyAnswering &&
            cards.none { answer in it.acceptedAnswers }
        ) return null

        val card = cards[pendingIndex]
        return if (answer in card.acceptedAnswers) {
            writeInt(KEY_CORRECT, readInt(KEY_CORRECT, 0).coerceAtLeast(0) + 1)
            writeInt(KEY_NEXT, (pendingIndex + 1) % cards.size)
            writeInt(KEY_PENDING, 0)
            "Correct. ${card.russian} (${card.romanized}) means ${card.english}. Say 'Russian quiz' for the next word."
        } else {
            "Not quite. Translate ${card.russian} into English. " +
                "Reply with 'answer ...' or say 'skip Russian question'."
        }
    }

    private fun askQuestion(): String {
        val index = readInt(KEY_NEXT, 0).coerceAtLeast(0) % cards.size
        writeInt(KEY_PENDING, index + 1)
        return "Russian practice: What does '${cards[index].russian}' mean in English? " +
            "Type the English translation, or reply 'answer <translation>'. Say 'skip Russian question' to reveal it."
    }

    private fun lesson(number: Int): String {
        writeInt(KEY_LAST_LESSON, number)
        return when (number) {
            1 -> lessonOne()
            2 -> lessonTwo()
            3 -> lessonThree()
            4 -> lessonFour()
            5 -> lessonFive()
            else -> lessonSix()
        }
    }

    private fun lessonThree(): String =
        """Russian lesson 3 — Reading Cyrillic

Russian has 33 letters: 10 vowels, 21 consonants and two signs.
А a, Б b, В v, Г g, Д d, Е ye/e, Ё yo, Ж zh, З z,
И i, Й y, К k, Л l, М m, Н n, О o, П p, Р r, С s,
Т t, У u, Ф f, Х kh, Ц ts, Ч ch, Ш sh, Щ shch,
Ъ hard sign, Ы y, Ь soft sign, Э e, Ю yu, Я ya.

Practise: дом (dom, house), книга (kniga, book), вода (voda, water).
Stress can change vowel sounds; romanization is approximate.
Say 'next Russian lesson' or 'Russian quiz'."""

    private fun lessonFour(): String =
        """Russian lesson 4 — Numbers and useful nouns

Один (odin) — one
Два (dva) — two
Три (tri) — three
Четыре (chetyre) — four
Пять (pyat) — five

Вода (voda) — water
Хлеб (khleb) — bread
Книга (kniga) — book
Дом (dom) — house
Семья (semya) — family

Russian number forms and noun endings can change with grammatical case.
Practise with 'Russian quiz', then continue to lesson 5."""

    private fun lessonFive(): String =
        """Russian lesson 5 — The six grammatical cases

1. Именительный (nominative): the subject of a sentence.
2. Родительный (genitive): possession, quantity and some negation.
3. Дательный (dative): recipient, often 'to someone'.
4. Винительный (accusative): direct object and certain destinations.
5. Творительный (instrumental): 'with' or 'by means of'.
6. Предложный (prepositional): after some prepositions for place or topic.

Example: Это книга (eto kniga, this is a book).
Я читаю книгу (ya chitayu knigu, I read a book).
The ending changes from книга to книгу because the noun is a direct object.
Say 'next Russian lesson' for verbs or 'Russian quiz' for vocabulary."""

    private fun lessonSix(): String =
        """Russian lesson 6 — Verbs and aspect

Читать (chitat) — to read; я читаю (ya chitayu) — I read.
Писать (pisat) — to write; я пишу (ya pishu) — I write.
Говорить (govorit) — to speak; я говорю (ya govoryu) — I speak.

Russian distinguishes imperfective and perfective verb aspect:
писать — to write, focusing on process or repetition.
написать — to write to completion, focusing on the result.

Example: Я писал письмо (I was writing a letter).
Я написал письмо (I wrote/finished a letter).

These are introductory examples, not complete conjugation rules.
Return to 'Russian course' or practise with 'Russian quiz'."""

    private fun lessonOne(): String =
        """Russian lesson 1 — Greetings

Привет (privet) — Hi
Здравствуйте (zdravstvuyte) — Hello (formal)
Спасибо (spasibo) — Thank you
Да (da) — Yes
Нет (net) — No

Say 'Russian quiz' to practise. Your correct answers are saved on this device."""

    private fun lessonTwo(): String =
        """Russian lesson 2 — Everyday phrases

Пожалуйста (pozhaluysta) — Please / You're welcome
До свидания (do svidaniya) — Goodbye
Доброе утро (dobroye utro) — Good morning
Как дела? (kak dela?) — How are you?
Хорошо (khorosho) — Good / Fine

Say 'Russian quiz' to practise the core vocabulary."""

    private companion object {
        const val KEY_CORRECT = "russian_correct"
        const val KEY_NEXT = "russian_next_question"
        const val KEY_PENDING = "russian_pending_question"
        const val KEY_LAST_LESSON = "russian_last_lesson"
    }
}
