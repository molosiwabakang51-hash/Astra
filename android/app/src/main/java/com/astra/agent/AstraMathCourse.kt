package com.astra.agent

import java.util.Locale

/**
 * A local, deterministic Mathematics curriculum. Lessons and quiz answers are
 * authored examples, not claims of neural reasoning or live tutoring.
 * Only small integer counters/bitsets persist in existing Astra preferences.
 */
internal class AstraMathCourse(
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
        private const val LAST = "astra_math_last_lesson"
        private const val VISITED = "astra_math_lessons_visited"
        private const val MASTERED = "astra_math_quizzes_mastered"
        private const val PENDING = "astra_math_pending_quiz"
        private const val ATTEMPTS = "astra_math_quiz_attempts"
        private const val CORRECT = "astra_math_quiz_correct"

        val lessons: List<Lesson> = listOf(
            Lesson(
                "Whole-number arithmetic",
                "Use order of operations: multiplication and division precede addition and subtraction, with equal-priority operations evaluated left to right.",
                "6 + 4 × 3 = 6 + 12 = 18, not 30.",
                "Parentheses can change the order; check signs when subtracting negatives.",
                "Evaluate 6 + 4 × 3.",
                listOf("18", "30", "10"), 'A',
                "Multiply 4 × 3 first, then add 6."
            ),
            Lesson(
                "Fractions and decimals",
                "A fraction a/b represents a part of a whole. To add fractions, use a common denominator.",
                "1/2 + 1/4 = 2/4 + 1/4 = 3/4 = 0.75.",
                "A denominator must never be zero.",
                "What is 1/2 + 1/4?",
                listOf("2/6", "3/4", "1/8"), 'B',
                "Convert 1/2 to 2/4; add 2/4 + 1/4."
            ),
            Lesson(
                "Ratios and proportions",
                "A ratio compares quantities; equivalent ratios multiply or divide both terms by the same nonzero number.",
                "A 2:3 ratio totaling 25 allocates 10 and 15 because 2+3 = 5 parts and each part is 5.",
                "Keep units consistent and distinguish part-to-part from part-to-whole.",
                "A 2:3 ratio splits 25 into which amounts?",
                listOf("5 and 20", "12 and 13", "10 and 15"), 'C',
                "Five total parts at 5 each gives 10 and 15."
            ),
            Lesson(
                "Percentages and changes",
                "A percentage is a quantity per 100. Percentage change = (new − old)/old × 100 when the old value is nonzero.",
                "A price rising from 80 to 100 increases by 20/80 × 100 = 25%.",
                "A 20% loss followed by a 20% gain does not restore the original value.",
                "From 80 to 100 is what percentage increase?",
                listOf("25%", "20%", "80%"), 'A',
                "The original denominator is 80; 20/80 = 0.25."
            ),
            Lesson(
                "Exponents and roots",
                "Powers represent repeated multiplication; roots reverse powers for appropriate domains.",
                "2^4 = 16, and the principal square root of 81 is 9.",
                "A square root symbol denotes the nonnegative principal root, while x² = 81 has two real solutions.",
                "What is 2^4?",
                listOf("8", "16", "32"), 'B',
                "2 × 2 × 2 × 2 = 16."
            ),
            Lesson(
                "Negative numbers and inequalities",
                "Adding a negative number moves left on the number line. Multiplying or dividing an inequality by a negative reverses its sign.",
                "−3x > 12 implies x < −4 after dividing by −3.",
                "Use care with inequality directions and strict versus inclusive bounds.",
                "Solve −3x > 12.",
                listOf("x > −4", "x < 4", "x < −4"), 'C',
                "Divide by −3 and reverse > to <."
            ),
            Lesson(
                "Linear equations",
                "A linear equation can be solved by performing the same operation on both sides.",
                "3x + 5 = 20 → 3x = 15 → x = 5.",
                "Verify the solution by substituting it back into the original equation.",
                "Solve 3x + 5 = 20.",
                listOf("x = 5", "x = 15", "x = 25/3"), 'A',
                "Subtract 5, then divide both sides by 3."
            ),
            Lesson(
                "Simultaneous equations",
                "Two equations can determine two unknowns; elimination or substitution reduces them to one variable.",
                "x + y = 10 and x − y = 2; add them to get 2x = 12, so x = 6 and y = 4.",
                "Inconsistent systems can have no solution; dependent systems can have infinitely many.",
                "For x+y=10 and x−y=2, what is y?",
                listOf("6", "4", "2"), 'B',
                "Add equations for x = 6, then y = 4."
            ),
            Lesson(
                "Quadratic equations",
                "A quadratic equation has a squared variable. Factoring can reveal its roots when the product is zero.",
                "x² − 5x + 6 = (x − 2)(x − 3), so x = 2 or x = 3.",
                "Not every quadratic factors neatly; the quadratic formula can handle other cases.",
                "Roots of x² − 5x + 6 = 0?",
                listOf("−2 and −3", "1 and 6", "2 and 3"), 'C',
                "Set each factor (x−2) and (x−3) to zero."
            ),
            Lesson(
                "Functions and substitution",
                "A function assigns an output to an input. Evaluate it by substituting the input into the formula.",
                "For f(x) = 2x² − 1, f(3) = 2 × 9 − 1 = 17.",
                "A function can have domain restrictions; for example 1/x is undefined at x = 0.",
                "If f(x) = 2x²−1, what is f(3)?",
                listOf("17", "11", "35"), 'A',
                "Square 3 before multiplying by 2."
            ),
            Lesson(
                "Coordinate geometry",
                "The slope between (x₁,y₁) and (x₂,y₂) is (y₂−y₁)/(x₂−x₁), provided x₂ ≠ x₁.",
                "Between (1,2) and (3,6), slope = (6−2)/(3−1) = 2.",
                "Vertical lines have undefined slope because the denominator is zero.",
                "Slope between (1,2) and (3,6)?",
                listOf("4", "2", "1/2"), 'B',
                "Rise 4 divided by run 2 equals 2."
            ),
            Lesson(
                "Geometry and area",
                "Area measures two-dimensional space, while perimeter is the length around a shape.",
                "A rectangle 5 m by 3 m has area 15 m² and perimeter 16 m.",
                "Do not confuse squared area units with linear perimeter units.",
                "Area of a 5 m × 3 m rectangle?",
                listOf("8 m²", "16 m²", "15 m²"), 'C',
                "Multiply length × width = 15 m²."
            ),
            Lesson(
                "Probability",
                "For equally likely outcomes, P(event) = favorable outcomes / possible outcomes. The probability lies between 0 and 1.",
                "On a fair six-sided die, the even faces are 2, 4 and 6. Therefore P(even) = 3/6 = 1/2.",
                "The formula assumes equally likely outcomes; not every real-world event satisfies that assumption.",
                "Probability of an even number on a fair die?",
                listOf("1/2", "1/3", "2/3"), 'A',
                "Three of the six faces are even: 2,4,6."
            ),
            Lesson(
                "Statistics and averages",
                "The arithmetic mean equals total values divided by count; the median is the middle value after sorting.",
                "Values 2, 3 and 10 have mean 5 but median 3. A large outlier affects the mean more.",
                "Averages can obscure dispersion and sample size.",
                "Median of 2, 3, 10?",
                listOf("5", "3", "10"), 'B',
                "The middle sorted value is 3."
            ),
            Lesson(
                "Counting arrangements",
                "Permutations count ordered selections; combinations count unordered selections. For n distinct objects choose r, nCr = n!/(r!(n−r)!).",
                "Choosing 2 people from 5 without regard to order gives 5C2 = 10 possible pairs.",
                "Treat order correctly and assume no replacement only when specified.",
                "How many unordered pairs from 5 distinct people?",
                listOf("20", "5", "10"), 'C',
                "5C2 = 5 × 4 / 2 = 10."
            ),
            Lesson(
                "Compound growth",
                "Repeated percentage growth multiplies a quantity by (1+r)^n, where the rate r matches each period.",
                "A value of P1,000 growing 10% annually for 2 years becomes P1,000 × 1.1² = P1,210.",
                "Constant growth is an assumption; inflation and fees affect realized buying power.",
                "P1,000 grows 10% annually for 2 years. Final value?",
                listOf("P1,210", "P1,200", "P1,100"), 'A',
                "1,000 × 1.10 × 1.10 = P1,210."
            )
        )
        private const val VALID_MASK = 0x0000FFFF
    }

    fun respond(raw: String): String? {
        val command = raw.trim().lowercase(Locale.ROOT).trimEnd('?', '!', '.').trim()
        if (command.length > 90) return null
        return when (command) {
            "math course", "mathematics course", "math syllabus",
            "learn math", "learn mathematics", "teach me math",
            "teach me mathematics", "start math course" -> syllabus()
            "math progress", "mathematics progress" -> progress()
            "next math lesson" -> lesson((readInt(LAST, 0) + 1).coerceIn(1, lessons.size))
            "math quiz", "quiz me on math" -> quiz(readInt(LAST, 1).coerceIn(1, lessons.size))
            "skip math quiz" -> {
                if (readInt(PENDING, 0) !in 1..lessons.size) {
                    "No Maths quiz is active. Say 'math quiz' to start one."
                } else {
                    writeInt(PENDING, 0)
                    "Maths quiz skipped. Say 'math quiz' to retry."
                }
            }
            else -> when {
                command.startsWith("answer math") -> answer(command)
                command.startsWith("math lesson ") ->
                    numbered(command.removePrefix("math lesson ")) { lesson(it) }
                command.startsWith("math quiz ") ->
                    numbered(command.removePrefix("math quiz ")) { quiz(it) }
                else -> null
            }
        }
    }

    private fun numbered(raw: String, handler: (Int) -> String): String {
        val number = raw.toIntOrNull()
        return if (number != null && number in 1..lessons.size)
            handler(number) else
            "Choose a Maths lesson from 1 to ${lessons.size}; say 'math course' for the syllabus."
    }

    private fun syllabus(): String = buildString {
        appendLine("MATHEMATICS FOUNDATION · ${lessons.size} OFFLINE LESSONS")
        appendLine("No model or internet required.")
        lessons.forEachIndexed { index, item ->
            appendLine("${index + 1}. ${item.title}")
        }
        appendLine()
        append("Choose 'math lesson 1', 'next math lesson', 'math quiz 1', or 'math progress'.")
    }.trim()

    private fun lesson(index: Int): String {
        val item = lessons[index - 1]
        writeInt(LAST, index)
        writeInt(VISITED, readInt(VISITED, 0) or (1 shl (index - 1)))
        return buildString {
            appendLine("MATH LESSON $index/${lessons.size} · ${item.title.uppercase(Locale.ROOT)}")
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
            append("Practice: 'math quiz $index'. " +
                if (index < lessons.size) "Continue: 'math lesson ${index + 1}'." else
                    "Course complete: 'math progress'.")
        }.trim()
    }

    private fun quiz(index: Int): String {
        val item = lessons[index - 1]
        writeInt(PENDING, index)
        return buildString {
            appendLine("MATH QUIZ $index/${lessons.size} · ${item.title}")
            appendLine(item.question)
            item.choices.forEachIndexed { i, choice ->
                appendLine("${('A'.code + i).toChar()}. $choice")
            }
            append("Reply 'answer math A', B or C; or 'skip math quiz'.")
        }.trim()
    }

    private fun answer(command: String): String {
        val active = readInt(PENDING, 0)
        if (active !in 1..lessons.size) {
            return "No Maths quiz is active. Say 'math quiz 1' or 'math quiz'."
        }
        val choice = command.removePrefix("answer math").trim().uppercase(Locale.ROOT)
        if (choice !in listOf("A", "B", "C")) {
            return "Choose A, B or C using 'answer math A'."
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
            "Try 'math quiz $active' to review, or 'math progress' for your results."
    }

    private fun progress(): String {
        val visited = Integer.bitCount(readInt(VISITED, 0) and VALID_MASK)
        val mastered = Integer.bitCount(readInt(MASTERED, 0) and VALID_MASK)
        val last = readInt(LAST, 0).coerceIn(0, lessons.size)
        val attempts = readInt(ATTEMPTS, 0).coerceAtLeast(0)
        val correct = readInt(CORRECT, 0).coerceIn(0, attempts)
        return "MATHEMATICS COURSE PROGRESS · OFFLINE\n" +
            "Lessons opened: $visited/${lessons.size}. " +
            "Lesson quizzes mastered: $mastered/${lessons.size}.\n" +
            "Quiz answers: $correct/$attempts correct. " +
            "Last opened: ${if (last == 0) "none" else "$last/${lessons.size}"}.\n" +
            "Say 'math course' for the syllabus or 'next math lesson' to continue."
    }
}
