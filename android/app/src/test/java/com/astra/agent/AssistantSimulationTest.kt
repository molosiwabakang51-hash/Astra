package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Deterministic simulation/regression suite. Fixed seeds make failures reproducible.
 * These are JVM model simulations, not Android-device UI, microphone, or network tests.
 */
class AssistantSimulationTest {
    @Test fun russianQuizStateMachineAcrossFiveThousandTurns() {
        val state = mutableMapOf<String, Int>()
        val tutor = RussianTutor(
            { key, default -> state[key] ?: default },
            { key, value -> state[key] = value }
        )
        // Keep this full 5,000-turn state-machine test aligned with the
        // preserved first-six order and the expanded 30-card Russian course.
        val answers = listOf(
            "thank you", "yes", "no", "hi", "please", "goodbye",
            "good morning", "good evening", "how are you", "fine",
            "excuse me", "water", "bread", "book", "house", "school",
            "friend", "family", "one", "two", "three", "four", "five",
            "today", "tomorrow", "yesterday", "where", "who",
            "to read", "to write"
        )
        val random = Random(0xA57A)
        var correct = 0
        repeat(5_000) { step ->
            val question = tutor.respond("Russian quiz")
            assertNotNull("step=$step", question)
            assertTrue("step=$step", question!!.contains("Russian practice"))
            when (random.nextInt(4)) {
                0 -> {
                    assertTrue(tutor.respond("answer")!!.contains("Please give"))
                    assertNull(tutor.respond("add task study"))
                }
                1 -> assertTrue(tutor.respond("answer nonsense")!!.contains("Not quite"))
                2 -> {
                    assertTrue(tutor.respond("skip russian question")!!.contains("Skipped"))
                    assertTrue(tutor.respond("answer")!!.contains("no active"))
                    return@repeat
                }
            }
            val index = state["russian_pending_question"]!! - 1
            assertTrue(index in answers.indices)
            assertTrue(tutor.respond("answer " + answers[index])!!.startsWith("Correct."))
            correct++
            assertEquals(correct, state["russian_correct"])
        }
        assertTrue(tutor.respond("Russian progress")!!.contains("$correct correct"))
    }

    @Test fun shoppingListModelAcrossThreeThousandOperations() {
        val state = mutableMapOf<String, String>()
        val assistant = EverydayAssistant(
            { key, default -> state[key] ?: default },
            { key, value -> state[key] = value },
            { "2026-11-14" }
        )
        val expected = linkedSetOf<String>()
        val random = Random(42L)
        repeat(3_000) { step ->
            val item = "item_" + random.nextInt(80)
            if (random.nextBoolean()) {
                val reply = assistant.respond("add $item to shopping list", emptyList())!!
                if (expected.add(item)) assertTrue("step=$step", reply.contains("Added"))
                else assertTrue("step=$step", reply.contains("already"))
            } else {
                val reply = assistant.respond("remove $item from shopping list", emptyList())!!
                if (expected.remove(item)) assertTrue("step=$step", reply.contains("Removed"))
                else assertTrue("step=$step", reply.contains("not on"))
            }
            val snapshot = assistant.respond("shopping list", emptyList())!!
            if (expected.isEmpty()) assertTrue(snapshot.contains("empty"))
            else {
                val actual = snapshot.lines().drop(1).map { it.substringAfter(". ") }
                assertEquals("step=$step", expected.toList(), actual)
            }
        }
    }

    @Test fun habitDailyResetAcrossOneHundredDays() {
        val state = mutableMapOf<String, String>()
        var today = "day_0"
        val assistant = EverydayAssistant(
            { key, default -> state[key] ?: default },
            { key, value -> state[key] = value },
            { today }
        )
        assertTrue(assistant.respond("add habit reading", emptyList())!!.contains("Tracking"))
        repeat(100) { day ->
            today = "day_$day"
            assertTrue("day=$day", assistant.respond("habits", emptyList())!!.contains("0/1"))
            assertTrue(assistant.respond("habit done reading", emptyList())!!.contains("Completed"))
            assertTrue(assistant.respond("habits", emptyList())!!.contains("1/1"))
            assertTrue(assistant.respond("habit done reading", emptyList())!!.contains("Already"))
        }
    }

    @Test fun unitConversionsRoundTripAcrossThreeThousandValues() {
        val random = Random(773L)
        val pairs = listOf(
            "km" to "mi", "kg" to "lb", "m" to "ft",
            "l" to "gal", "h" to "min", "cm" to "in"
        )
        repeat(3_000) { step ->
            val (from, to) = pairs[step % pairs.size]
            val value = random.nextDouble() * 10_000.0 + 1.0
            val forward = UnitConverter.respond("convert $value $from to $to")!!
            val converted = forward.substringAfter(" = ").substringBefore(" ").toDouble()
            val reverse = UnitConverter.respond("convert $converted $to to $from")!!
            val restored = reverse.substringAfter(" = ").substringBefore(" ").toDouble()
            assertTrue("step=$step from=$from to=$to original=$value restored=$restored",
                kotlin.math.abs(restored - value) <= 0.001)
        }
    }

    @Test fun randomMalformedCommandsDoNotCrashOrMutateLists() {
        val random = Random(20261114L)
        val state = mutableMapOf<String, String>()
        val assistant = EverydayAssistant(
            { key, default -> state[key] ?: default },
            { key, value -> state[key] = value },
            { "2026-11-14" }
        )
        val quizState = mutableMapOf<String, Int>()
        val tutor = RussianTutor(
            { key, default -> quizState[key] ?: default },
            { key, value -> quizState[key] = value }
        )
        val alphabet = "abcXYZ 0123?!@#<>+=-\\nПривет"
        repeat(4_000) {
            val input = buildString {
                repeat(random.nextInt(65)) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            assistant.respond(input, emptyList())
            tutor.respond(input)
            UnitConverter.respond(input)
            ResearchCommand.extract(input)
        }
        assertFalse(state.containsKey("astra_shopping_items"))
        assertFalse(state.containsKey("astra_habits"))
        assertTrue(quizState.isEmpty())
    }

    @Test fun speechLanguageRoundTripsAllSupportedTags() {
        for (language in SpeechLanguage.entries) {
            assertEquals(language, SpeechLanguage.fromTag(language.tag))
            assertEquals(language, SpeechLanguage.fromTag(language.tag.lowercase()))
        }
        assertEquals(SpeechLanguage.ENGLISH_US, SpeechLanguage.fromTag("unsupported"))
    }
}
