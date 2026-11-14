package com.astra.agent

import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local, deterministic assistant skills for daily life.
 * Uses the existing Astra preference store through injected read/write functions.
 * List values are URL-encoded individually so punctuation and Unicode survive round trips.
 */
internal class EverydayAssistant(
    private val read: (String, String) -> String,
    private val write: (String, String) -> Unit,
    private val dateToday: () -> String = {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
) {
    private fun load(key: String): MutableList<String> =
        read(key, "")
            .split('\n')
            .filter { it.isNotEmpty() }
            .mapNotNull { encoded ->
                runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
            }
            .toMutableList()

    private fun save(key: String, values: List<String>) {
        write(key, values.joinToString("\n") { URLEncoder.encode(it, "UTF-8") })
    }

    private fun locate(items: List<String>, requested: String): Int {
        val numeric = requested.trim().toIntOrNull()
        if (numeric != null && numeric in 1..items.size) return numeric - 1
        return items.indexOfFirst { it.equals(requested.trim(), ignoreCase = true) }
    }

    private fun dailyCompletions(): MutableList<String> {
        val currentDate = dateToday()
        if (read("astra_habit_date", "") != currentDate) {
            write("astra_habit_date", currentDate)
            save("astra_habit_completed_today", emptyList())
        }
        return load("astra_habit_completed_today")
    }

    fun respond(raw: String, tasks: List<String>): String? {
        val input = raw.trim().trimEnd('?', '!', '.').trim()
        val lower = input.lowercase(Locale.ROOT)

        when (lower) {
            "shopping list", "my shopping list", "show shopping list",
            "show my shopping list", "what's on my shopping list" ->
                return shoppingOverview()
            "habits", "my habits", "show habits", "habit tracker",
            "habit progress", "show habit progress" ->
                return habitsOverview()
            "plan my day", "daily plan", "daily briefing",
            "plan today", "organize my day", "organise my day" ->
                return dailyPlan(tasks)
            "how to use shopping list", "shopping help" ->
                return "Try 'add milk to shopping list', 'shopping list', or " +
                    "'remove milk from shopping list'. This list stays on your device."
            "habit help" ->
                return "Try 'add habit read', 'habits', 'habit done read', or " +
                    "'remove habit read'. A completion is tracked for today."
            "breathing exercise", "help me breathe", "box breathing" ->
                return "Box breathing: inhale gently for 4 counts, hold for 4, " +
                    "exhale for 4, then hold for 4. Repeat if comfortable; " +
                    "stop if you feel lightheaded."
            "focus tips", "help me focus", "study focus" ->
                return "Focus session: choose one specific task, silence avoidable " +
                    "notifications, work for 25 minutes, then take a 5-minute break. " +
                    "Astra does not start a background timer from this command."
        }

        val addShopping = Regex(
            """(?i)^(?:add\s+(.+?)\s+to\s+(?:my\s+)?shopping\s+list|(?:shopping\s+add|add\s+to\s+(?:my\s+)?shopping\s+list)\s+(.+))$"""
        ).matchEntire(input)
        if (addShopping != null) {
            val name = (addShopping.groupValues[1].ifBlank { addShopping.groupValues[2] }).trim()
            if (name.isBlank()) return "Provide an item to add."
            val items = load("astra_shopping_items")
            if (locate(items, name) >= 0) return name + " is already on your shopping list."
            items.add(name)
            save("astra_shopping_items", items)
            return "Added to shopping list: " + name + "."
        }

        val removeShopping = Regex(
            """(?i)^(?:remove\s+(.+?)\s+from\s+(?:my\s+)?shopping\s+list|(?:shopping\s+remove|remove\s+from\s+(?:my\s+)?shopping\s+list)\s+(.+))$"""
        ).matchEntire(input)
        if (removeShopping != null) {
            val name = (removeShopping.groupValues[1].ifBlank { removeShopping.groupValues[2] }).trim()
            val items = load("astra_shopping_items")
            val index = locate(items, name)
            if (index < 0) return "That item is not on your shopping list."
            val deleted = items.removeAt(index)
            save("astra_shopping_items", items)
            return "Removed from shopping list: " + deleted + "."
        }

        val addHabit = Regex("""(?i)^(?:add habit|track habit|create habit)\s+(.+)$""")
            .matchEntire(input)
        if (addHabit != null) {
            val name = addHabit.groupValues[1].trim()
            if (name.isBlank()) return "Provide a habit to track."
            val habits = load("astra_habits")
            if (locate(habits, name) >= 0) return name + " is already a habit."
            habits.add(name)
            save("astra_habits", habits)
            return "Tracking habit: " + name + ". Mark it with 'habit done " + name + "'."
        }

        val removeHabit = Regex("""(?i)^remove habit\s+(.+)$""").matchEntire(input)
        if (removeHabit != null) {
            val habits = load("astra_habits")
            val index = locate(habits, removeHabit.groupValues[1])
            if (index < 0) return "Habit not found."
            val removed = habits.removeAt(index)
            save("astra_habits", habits)
            val done = dailyCompletions()
            done.removeAll { it.equals(removed, ignoreCase = true) }
            save("astra_habit_completed_today", done)
            return "Removed habit: " + removed + "."
        }

        val completeHabit = Regex(
            """(?i)^(?:habit done|done habit|complete habit|mark habit)\s+(.+?)(?:\s+done)?$"""
        ).matchEntire(input)
        if (completeHabit != null) {
            val habits = load("astra_habits")
            val index = locate(habits, completeHabit.groupValues[1])
            if (index < 0) return "Habit not found. Use 'add habit ...' first."
            val name = habits[index]
            val completed = dailyCompletions()
            if (locate(completed, name) >= 0) return "Already completed today: " + name + "."
            completed.add(name)
            save("astra_habit_completed_today", completed)
            return "Completed today: " + name + ". (" + completed.size + " of " +
                habits.size + " habits)"
        }

        return UnitConverter.respond(input)
    }

    private fun shoppingOverview(): String {
        val items = load("astra_shopping_items")
        if (items.isEmpty()) return "Your shopping list is empty. Try 'add milk to shopping list'."
        return "Shopping list:\n" + items.mapIndexed { i, item ->
            (i + 1).toString() + ". " + item
        }.joinToString("\n")
    }

    private fun habitsOverview(): String {
        val habits = load("astra_habits")
        if (habits.isEmpty()) return "No habits tracked yet. Try 'add habit read 20 minutes'."
        val done = dailyCompletions()
        val completedCount = habits.count { locate(done, it) >= 0 }
        return "Habits today (" + completedCount + "/" + habits.size + " completed):\n" +
            habits.mapIndexed { index, habit ->
                (index + 1).toString() + ". " +
                    (if (locate(done, habit) >= 0) "[done] " else "[ ] ") + habit
            }.joinToString("\n")
    }

    private fun dailyPlan(tasks: List<String>): String {
        val habits = load("astra_habits")
        val completed = dailyCompletions()
        val taskSummary = if (tasks.isEmpty()) {
            "No outstanding tasks. Try 'add task ...'."
        } else {
            tasks.take(10).mapIndexed { i, item ->
                (i + 1).toString() + ". " + item
            }.joinToString("\n") +
                (if (tasks.size > 10) "\n... plus " + (tasks.size - 10) + " more tasks." else "")
        }
        val habitsSummary = if (habits.isEmpty()) {
            "No habits added. Try 'add habit ...'."
        } else {
            habits.joinToString("\n") { habit ->
                (if (locate(completed, habit) >= 0) "[done] " else "[ ] ") + habit
            }
        }
        return "Daily overview — " + dateToday() +
            "\n\nTASKS\n" + taskSummary + "\n\nHABITS\n" + habitsSummary +
            "\n\nTip: Choose your most important task first. " +
            "This is an overview of your saved items, not an automatically scheduled calendar."
    }
}
