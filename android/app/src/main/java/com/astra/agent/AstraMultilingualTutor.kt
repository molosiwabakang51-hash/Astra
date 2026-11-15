package com.astra.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Fully offline, deterministic starter courses for eight additional languages.
 * No private notes, model inference, remote translation, or paid service.
 * Each quiz has independently persisted progress in Astra preferences.
 */
internal class AstraMultilingualTutor(
    private val readInt: (String, Int) -> Int,
    private val writeInt: (String, Int) -> Unit,
    private val readString: (String, String) -> String,
    private val writeString: (String, String) -> Unit
) {
    internal enum class Language(val id: String, val display: String, vararg val aliases: String) {
        SETSWANA("setswana", "Setswana (Botswana)", "setswana", "tswana"),
        FRENCH("french", "French", "french", "français", "francais"),
        SPANISH("spanish", "Spanish", "spanish", "español", "espanol"),
        PORTUGUESE("portuguese", "Portuguese", "portuguese", "português", "portugues"),
        GERMAN("german", "German", "german", "deutsch"),
        SWAHILI("swahili", "Swahili", "swahili", "kiswahili"),
        JAPANESE("japanese", "Japanese", "japanese", "日本語"),
        MANDARIN("mandarin", "Mandarin Chinese", "mandarin", "chinese", "普通话")
    }
    internal data class Card(
        val phrase: String,
        val english: String,
        val pronunciation: String = "",
        val alternatives: List<String> = emptyList()
    )
    private fun card(text: String, english: String, pronunciation: String = "",
        vararg alternatives: String
    ): Card = Card(text, english, pronunciation, alternatives.toList())

    private val decks: Map<Language, List<Card>> = mapOf(
        Language.SETSWANA to listOf(
            card("Dumela", "Hello"), card("Dumelang", "Hello (to a group)", "", "hello everyone"),
            card("Ke a leboga", "Thank you", "", "thanks"), card("Ee", "Yes"),
            card("Nnyaa", "No"), card("O tsogile jang?", "How are you?"),
            card("Ke teng", "I am fine", "", "i am well", "i'm fine"),
            card("Ke kopa metsi", "May I have water?", "", "i want some water"),
            card("Tsamaya sentle", "Go well"), card("Sala sentle", "Stay well"),
            card("Ke kopa thuso", "I need help"), card("Leina la gago ke mang?", "What is your name?")
        ),
        Language.FRENCH to listOf(
            card("Bonjour", "Hello"), card("Au revoir", "Goodbye"),
            card("Merci", "Thank you", "", "thanks"), card("S'il vous plaît", "Please"),
            card("Oui", "Yes"), card("Non", "No"), card("Comment ça va ?", "How are you?"),
            card("Je vais bien", "I am fine", "", "i am well", "i'm fine"),
            card("Eau", "Water"), card("Pain", "Bread"), card("Un", "One"), card("Deux", "Two")
        ),
        Language.SPANISH to listOf(
            card("Hola", "Hello"), card("Adiós", "Goodbye"),
            card("Gracias", "Thank you", "", "thanks"), card("Por favor", "Please"),
            card("Sí", "Yes"), card("No", "No"), card("¿Cómo estás?", "How are you?"),
            card("Estoy bien", "I am fine", "", "i am well", "i'm fine"),
            card("Agua", "Water"), card("Pan", "Bread"), card("Uno", "One"), card("Dos", "Two")
        ),
        Language.PORTUGUESE to listOf(
            card("Olá", "Hello"), card("Adeus", "Goodbye"),
            card("Obrigado", "Thank you", "", "thanks"), card("Por favor", "Please"),
            card("Sim", "Yes"), card("Não", "No"), card("Como estás?", "How are you?"),
            card("Estou bem", "I am fine", "", "i am well", "i'm fine"),
            card("Água", "Water"), card("Pão", "Bread"), card("Um", "One"), card("Dois", "Two")
        ),
        Language.GERMAN to listOf(
            card("Hallo", "Hello"), card("Tschüss", "Goodbye"),
            card("Danke", "Thank you", "", "thanks"), card("Bitte", "Please"),
            card("Ja", "Yes"), card("Nein", "No"), card("Wie geht es dir?", "How are you?"),
            card("Mir geht es gut", "I am fine", "", "i am well", "i'm fine"),
            card("Wasser", "Water"), card("Brot", "Bread"), card("Eins", "One"), card("Zwei", "Two")
        ),
        Language.SWAHILI to listOf(
            card("Jambo", "Hello"), card("Kwaheri", "Goodbye"),
            card("Asante", "Thank you", "", "thanks"), card("Tafadhali", "Please"),
            card("Ndiyo", "Yes"), card("Hapana", "No"), card("Habari gani?", "How are you?"),
            card("Nzuri", "Fine", "", "good", "i am fine"),
            card("Maji", "Water"), card("Mkate", "Bread"), card("Moja", "One"), card("Mbili", "Two")
        ),
        Language.JAPANESE to listOf(
            card("こんにちは", "Hello", "konnichiwa"),
            card("さようなら", "Goodbye", "sayounara"),
            card("ありがとう", "Thank you", "arigatou", "thanks"),
            card("お願いします", "Please", "onegaishimasu"),
            card("はい", "Yes", "hai"), card("いいえ", "No", "iie"),
            card("お元気ですか？", "How are you?", "ogenki desu ka"),
            card("元気です", "I am fine", "genki desu", "i am well", "i'm fine"),
            card("水", "Water", "mizu"), card("パン", "Bread", "pan"),
            card("一", "One", "ichi"), card("二", "Two", "ni")
        ),
        Language.MANDARIN to listOf(
            card("你好", "Hello", "nǐ hǎo"), card("再见", "Goodbye", "zài jiàn"),
            card("谢谢", "Thank you", "xiè xie", "thanks"),
            card("请", "Please", "qǐng"), card("是的", "Yes", "shì de"),
            card("不是", "No", "bú shì"),
            card("你好吗？", "How are you?", "nǐ hǎo ma"),
            card("我很好", "I am fine", "wǒ hěn hǎo", "i am well", "i'm fine"),
            card("水", "Water", "shuǐ"), card("面包", "Bread", "miàn bāo"),
            card("一", "One", "yī"), card("二", "Two", "èr")
        )
    )

    companion object {
        private const val ACTIVE = "astra_learning_language"
        private val spacing = Regex("""\s+""")
        fun progressKeys(): List<String> = Language.entries.flatMap { language ->
            listOf("next", "correct", "pending", "lesson").map {
                "lang_" + language.id + "_" + it
            }
        }
        fun availableLanguages(): String =
            "Offline courses: Russian, Setswana, French, Spanish, Portuguese, " +
            "German, Swahili, Japanese and Mandarin Chinese. " +
            "Type 'learn setswana', 'learn japanese', 'french lesson 2', " +
            "'spanish quiz', or 'language progress'. " +
            "Answers are English translations; speech needs the corresponding Android pack."
    }

    fun respond(raw: String): String? {
        val text = raw.trim().replace(spacing, " ")
        val lower = text.lowercase(Locale.ROOT).trimEnd('?', '!', '.')
        if (lower in setOf("languages", "language courses", "show languages",
                "available languages", "learn languages", "list languages")) {
            return availableLanguages()
        }
        if (lower in setOf("language progress", "languages progress")) {
            val total = Language.entries.sumOf {
                readInt(key(it, "correct"), 0).coerceAtLeast(0)
            }
            val active = current()
            return "Multilingual practice: " + total +
                " correct answers across eight additional languages. " +
                (active?.let {
                    it.display + ": " + readInt(key(it, "correct"), 0) + " correct. "
                } ?: "No additional course selected. ") +
                "Type 'languages' to see the available courses."
        }
        for (language in Language.entries) {
            for (alias in language.aliases) {
                if (lower == "learn " + alias || lower == "teach me " + alias ||
                    lower == alias || lower == alias + " course" ||
                    lower == alias + " lesson" || lower == alias + " lesson 1") {
                    activate(language)
                    return lesson(language, 1)
                }
                if (lower == alias + " lesson 2" || lower == alias + " lesson 3") {
                    activate(language)
                    return lesson(language, lower.last().digitToInt())
                }
                if (lower == alias + " quiz" || lower == "practice " + alias ||
                    lower == alias + " practice" || lower == "quiz me in " + alias) {
                    activate(language)
                    return quiz(language)
                }
                if (lower == alias + " progress") {
                    return language.display + ": " + readInt(key(language, "correct"), 0) +
                        " correct answers; " + decks.getValue(language).size +
                        " practice phrases. Type '" + language.id + " quiz' to continue."
                }
            }
        }
        if (lower == "next language lesson") {
            val language = current() ?: return "Select a course first: 'learn setswana'."
            val next = (readInt(key(language, "lesson"), 1) + 1).coerceAtMost(3)
            return lesson(language, next)
        }
        if (lower in setOf("skip language question", "skip language quiz")) {
            val language = current() ?: return "No active language quiz."
            val pending = readInt(key(language, "pending"), 0) - 1
            if (pending !in decks.getValue(language).indices) return "No active language quiz."
            writeInt(key(language, "pending"), 0)
            writeInt(key(language, "next"), (pending + 1) % decks.getValue(language).size)
            val card = decks.getValue(language)[pending]
            return "Skipped. " + card.phrase + " means " + card.english +
                ". Type '" + language.id + " quiz' to continue."
        }
        if (lower == "answer" || lower.startsWith("answer ")) {
            val language = current() ?: return null
            val index = readInt(key(language, "pending"), 0) - 1
            if (index !in decks.getValue(language).indices) return null
            val card = decks.getValue(language)[index]
            if (lower == "answer") {
                return "Translate '" + card.phrase +
                    "' into English. Reply 'answer <translation>' " +
                    "or 'skip language question'."
            }
            val typed = canonical(lower.removePrefix("answer ").trim())
            val accepted = (listOf(card.english) + card.alternatives)
                .any { canonical(it) == typed }
            if (!accepted) {
                return "Not quite. Translate '" + card.phrase +
                    "' into English. Try 'answer ...' or 'skip language question'."
            }
            writeInt(key(language, "pending"), 0)
            writeInt(key(language, "next"), (index + 1) % decks.getValue(language).size)
            val count = readInt(key(language, "correct"), 0).coerceAtLeast(0) + 1
            writeInt(key(language, "correct"), count)
            return "Correct. " + card.phrase + " means " + card.english +
                ". Progress: " + count + " correct. Type '" +
                language.id + " quiz' for the next phrase."
        }
        return null
    }

    private fun activate(language: Language) {
        val previous = current()
        if (previous != null && previous != language) {
            writeInt(key(previous, "pending"), 0)
        }
        writeString(ACTIVE, language.id)
    }
    private fun current(): Language? =
        Language.entries.firstOrNull { it.id == readString(ACTIVE, "") }
    private fun key(language: Language, suffix: String) =
        "lang_" + language.id + "_" + suffix

    private fun lesson(language: Language, number: Int): String {
        val module = number.coerceIn(1, 3)
        writeInt(key(language, "lesson"), module)
        val content = decks.getValue(language)
            .drop((module - 1) * 4).take(4)
            .joinToString("\n") {
                it.phrase +
                    (if (it.pronunciation.isEmpty()) ""
                     else " (" + it.pronunciation + ")") +
                    " — " + it.english
            }
        val title = when (module) {
            1 -> "Greetings"
            2 -> "Conversation and common requests"
            else -> "Daily phrases and numbers"
        }
        return language.display + " lesson " + module + "/3 — " + title +
            "\n\n" + content + "\n\n" +
            if (module < 3)
                "Type 'next language lesson' or '" + language.id + " quiz' to practise."
            else
                "Lesson complete. Type '" + language.id + " quiz' for offline practice."
    }

    private fun quiz(language: Language): String {
        val list = decks.getValue(language)
        val index = readInt(key(language, "next"), 0).coerceAtLeast(0) % list.size
        writeInt(key(language, "pending"), index + 1)
        return language.display + " practice: What does '" + list[index].phrase +
            "' mean in English? Reply 'answer <translation>' " +
            "or 'skip language question'."
    }

    private fun canonical(s: String): String =
        Normalizer.normalize(s.trim().lowercase(Locale.ROOT), Normalizer.Form.NFKC)
            .replace(Regex("""[\p{P}\p{S}]+"""), " ")
            .replace(spacing, " ").trim()
}
