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
        Card("До свидания", "do svidaniya", "Goodbye", setOf("goodbye", "bye"))
    )

    fun respond(raw: String): String? {
        val input = raw.trim()
            .trimEnd('?', '!', '.')
            .lowercase(Locale.ROOT)
            .trim()

        when (input) {
            "teach me russian", "learn russian", "russian lesson",
            "start russian lessons", "russian lesson 1" ->
                return lessonOne()

            "russian lesson 2", "next russian lesson" ->
                return lessonTwo()

            "russian quiz", "quiz me in russian", "quiz me on russian",
            "practice russian", "russian practice" ->
                return askQuestion()

            "russian progress", "my russian progress" -> {
                val answered = readInt(KEY_CORRECT, 0).coerceAtLeast(0)
                return "Russian practice: ${answered} correct answer(s). " +
                    "Say 'Russian quiz' to practise or 'Russian lesson 2' for more phrases."
            }
        }

        val pendingIndex = readInt(KEY_PENDING, 0) - 1
        if (pendingIndex !in cards.indices) return null

        if (input == "skip russian question" || input == "skip russian") {
            writeInt(KEY_PENDING, 0)
            writeInt(KEY_NEXT, (pendingIndex + 1) % cards.size)
            return "Skipped. ${cards[pendingIndex].russian} means ${cards[pendingIndex].english}. Say 'Russian quiz' to continue."
        }

        val explicitlyAnswering = input.startsWith("answer ")
        val answer = if (explicitlyAnswering) input.removePrefix("answer ").trim() else input

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
            "Reply 'answer ...'. Say 'skip Russian question' to reveal it."
    }

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
    }
}
