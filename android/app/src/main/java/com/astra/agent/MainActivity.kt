package com.astra.agent

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONObject

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.security.SecureRandom
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/*
============================================================
ASTRA v0.8 — LOCAL-FIRST ASSISTANT
============================================================

FRONT OFFICE
- Chat
- Tasks
- Notes
- Tools
- Settings

MIDDLE OFFICE
- Intent processing
- Mathematics
- Finance
- Utilities
- Local search
- Voice management

BACK OFFICE
- Persistent local storage
- Data migration
- Export/import
- Settings persistence

IMPORTANT:
This implementation uses Android's installed speech
services. On-device recognition is preferred, but a
separate speech model is not bundled.

Add RECORD_AUDIO permission to AndroidManifest.xml.

This version preserves the existing SharedPreferences
keys: history, notes, tasks.

============================================================
*/

// ============================================================
// CONSTANTS
// ============================================================

private const val ASTRA_VERSION = "0.9.3"
private const val MAX_HISTORY = 2000

private const val WELCOME =
    "Hello. How can I help you?"

// ============================================================
// BACK OFFICE — MODELS
// ============================================================

private data class ChatMessage(
    val role: String,
    val text: String
)

private enum class AssistantAction {
    NONE,
    CLEAR_CHAT,
    COPY_CHAT,
    SHARE_CHAT,
    OPEN_TASKS,
    OPEN_NOTES,
    OPEN_TOOLS,
    OPEN_SETTINGS
}

private data class AssistantResult(
    val text: String,
    val action: AssistantAction = AssistantAction.NONE,
    val sensitive: Boolean = false
)

// ============================================================
// BACK OFFICE — STORAGE
// ============================================================

private class AstraStorage(
    context: Context
) {

    private val prefs =
        context.getSharedPreferences(
            "astra",
            Context.MODE_PRIVATE
        )

    fun loadHistory(): List<ChatMessage> {

        return try {

            val array = JSONArray(
                prefs.getString(
                    "history",
                    "[]"
                )
            )

            List(array.length()) { index ->

                val item =
                    array.getJSONObject(index)

                ChatMessage(
                    item.optString("role"),
                    item.optString("text")
                )
            }

        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadNotes(): List<String> =
        loadStrings("notes")

    fun loadTasks(): List<String> =
        loadStrings("tasks")

    fun loadCompletedTasks(): List<String> =
        loadStrings("completed_tasks")

    private fun loadStrings(
        key: String
    ): List<String> {

        return try {

            val array = JSONArray(
                prefs.getString(
                    key,
                    "[]"
                )
            )

            List(array.length()) {
                array.getString(it)
            }

        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getBoolean(
        key: String,
        default: Boolean = false
    ): Boolean {

        return prefs.getBoolean(
            key,
            default
        )
    }

    fun setBoolean(
        key: String,
        value: Boolean
    ) {

        prefs.edit()
            .putBoolean(key, value)
            .apply()
    }

    fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun exportBackup(): String =
        AstraBackupCodec.encode(prefs.all)

    fun restoreBackup(payload: AstraBackupCodec.Payload) {
        val editor = prefs.edit()
        payload.values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                else -> throw IllegalArgumentException("Unsupported backup setting.")
            }
        }
        if (!editor.commit()) {
            throw IllegalStateException("Could not save the restored data.")
        }
    }

    fun save(
        history: List<ChatMessage>,
        notes: List<String>,
        tasks: List<String>,
        completed: List<String>
    ) {

        val historyArray = JSONArray()

        history.takeLast(
            MAX_HISTORY
        ).forEach {

            historyArray.put(
                JSONObject()
                    .put("role", it.role)
                    .put("text", it.text)
            )
        }

        fun stringArray(
            values: List<String>
        ): JSONArray {

            val result = JSONArray()

            values.forEach {
                result.put(it)
            }

            return result
        }

        prefs.edit()
            .putString(
                "history",
                historyArray.toString()
            )
            .putString(
                "notes",
                stringArray(notes).toString()
            )
            .putString(
                "tasks",
                stringArray(tasks).toString()
            )
            .putString(
                "completed_tasks",
                stringArray(completed).toString()
            )
            .apply()
    }
}

// ============================================================
// MIDDLE OFFICE — NUMBER FORMATTING
// ============================================================

private object Numbers {

    fun format(
        value: Double
    ): String {

        if (!value.isFinite()) {
            return "Undefined"
        }

        return try {

            BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString()

        } catch (_: Exception) {

            value.toString()
        }
    }

    fun percent(
        value: Double
    ): String {

        return "${format(value)}%"
    }
}

// ============================================================
// MIDDLE OFFICE — EXACT COMBINATORICS
// ============================================================

private object Combinatorics {

    fun factorial(
        n: Int
    ): BigInteger {

        require(n >= 0)

        var result = BigInteger.ONE

        for (i in 2..n) {

            result = result.multiply(
                BigInteger.valueOf(
                    i.toLong()
                )
            )
        }

        return result
    }

    fun permutations(
        n: Int,
        r: Int
    ): BigInteger {

        require(
            n >= 0 &&
            r >= 0 &&
            r <= n
        )

        var result = BigInteger.ONE

        for (i in 0 until r) {

            result = result.multiply(
                BigInteger.valueOf(
                    (n - i).toLong()
                )
            )
        }

        return result
    }

    fun combinations(
        n: Int,
        r: Int
    ): BigInteger {

        require(
            n >= 0 &&
            r >= 0 &&
            r <= n
        )

        val k = minOf(
            r,
            n - r
        )

        var result = BigInteger.ONE

        for (i in 1..k) {

            result = result
                .multiply(
                    BigInteger.valueOf(
                        (n - k + i).toLong()
                    )
                )
                .divide(
                    BigInteger.valueOf(
                        i.toLong()
                    )
                )
        }

        return result
    }
}

// ============================================================
// MIDDLE OFFICE — EXPRESSION PARSER
// ============================================================

private class ExpressionParser(
    private val source: String
) {

    private var position = 0

    fun parse(): Double {

        val result = sum()

        skipSpaces()

        require(
            position == source.length
        )

        require(
            result.isFinite()
        )

        return result
    }

    private fun skipSpaces() {

        while (
            position < source.length &&
            source[position].isWhitespace()
        ) {

            position++
        }
    }

    private fun eat(
        character: Char
    ): Boolean {

        skipSpaces()

        if (
            position < source.length &&
            source[position] == character
        ) {

            position++
            return true
        }

        return false
    }

    private fun sum(): Double {

        var result = product()

        while (true) {

            result = when {

                eat('+') ->
                    result + product()

                eat('-') ->
                    result - product()

                else ->
                    return result
            }
        }
    }

    private fun product(): Double {

        var result = unary()

        while (true) {

            result = when {

                eat('*') ->
                    result * unary()

                eat('/') -> {

                    val divisor = unary()

                    require(
                        divisor != 0.0
                    )

                    result / divisor
                }

                else ->
                    return result
            }
        }
    }

    private fun unary(): Double {

        if (eat('+')) {
            return unary()
        }

        if (eat('-')) {
            return -unary()
        }

        return power()
    }

    private fun power(): Double {

        val base = atom()

        return if (eat('^')) {

            base.pow(unary())

        } else {

            base
        }
    }

    private fun atom(): Double {

        if (eat('(')) {

            val result = sum()

            require(eat(')'))

            return result
        }

        skipSpaces()

        val start = position

        while (
            position < source.length &&
            (
                source[position].isDigit() ||
                source[position] == '.'
            )
        ) {

            position++
        }

        require(start < position)

        return source.substring(
            start,
            position
        ).toDouble()
    }
}

// ============================================================
// MIDDLE OFFICE — MATHEMATICS
// ============================================================

private object MathEngine {

    private const val N =
        """(-?\d+(?:\.\d+)?)"""

    fun process(
        raw: String
    ): String? {

        val input = raw.trim()
            .replace(
                Regex(
                    """[?!]+$"""
                ),
                ""
            )
            .replace(
                Regex(
                    """(?i)^(?:calculate|compute|evaluate|what is|what's|how much is)\s+"""
                ),
                ""
            )
            .trim()

        val factorial = Regex(
            """(?i)^(?:factorial of\s+)?(\d+)!?$"""
        ).matchEntire(input)

        if (
            factorial != null &&
            (
                input.contains("!") ||
                input.startsWith(
                    "factorial",
                    true
                )
            )
        ) {

            val n = factorial
                .groupValues[1]
                .toIntOrNull()

            if (
                n == null ||
                n > 500
            ) {

                return "Factorial input must be between 0 and 500."
            }

            return "= ${Combinatorics.factorial(n)}"
        }

        val permutations = Regex(
            """(?i)^(?:permutations?|npr)\s*\(?\s*(\d+)\s*[, ]\s*(\d+)\s*\)?$"""
        ).matchEntire(input)

        if (permutations != null) {

            val n = permutations
                .groupValues[1]
                .toIntOrNull()

            val r = permutations
                .groupValues[2]
                .toIntOrNull()

            if (
                n == null ||
                r == null ||
                n > 500 ||
                r > n
            ) {

                return "Require 0 ≤ r ≤ n ≤ 500."
            }

            return "P($n, $r) = ${Combinatorics.permutations(n, r)}"
        }

        val combinations = Regex(
            """(?i)^(?:combinations?|ncr)\s*\(?\s*(\d+)\s*[, ]\s*(\d+)\s*\)?$"""
        ).matchEntire(input)

        if (combinations != null) {

            val n = combinations
                .groupValues[1]
                .toIntOrNull()

            val r = combinations
                .groupValues[2]
                .toIntOrNull()

            if (
                n == null ||
                r == null ||
                n > 500 ||
                r > n
            ) {

                return "Require 0 ≤ r ≤ n ≤ 500."
            }

            return "C($n, $r) = ${Combinatorics.combinations(n, r)}"
        }

        val percentOf = Regex(
            """(?i)^$N\s*(?:percent|%)\s+of\s+$N$"""
        ).matchEntire(input)

        if (percentOf != null) {

            val a = percentOf
                .groupValues[1]
                .toDouble()

            val b = percentOf
                .groupValues[2]
                .toDouble()

            return "= ${Numbers.format(a * b / 100)}"
        }

        val percentageChange = Regex(
            """(?i)^percentage change\s+from\s+$N\s+to\s+$N$"""
        ).matchEntire(input)

        if (percentageChange != null) {

            val initial = percentageChange
                .groupValues[1]
                .toDouble()

            val final = percentageChange
                .groupValues[2]
                .toDouble()

            if (initial == 0.0) {
                return "Initial value cannot be zero."
            }

            return "Percentage change: ${Numbers.percent((final - initial) / abs(initial) * 100)}"
        }

        val percentageIncrease = Regex(
            """(?i)^(?:increase|decrease)\s+$N\s+by\s+$N\s*(?:percent|%)$"""
        ).matchEntire(input)

        if (percentageIncrease != null) {

            val value = percentageIncrease
                .groupValues[1]
                .toDouble()

            val percent = percentageIncrease
                .groupValues[2]
                .toDouble()

            val increase = input.startsWith(
                "increase",
                true
            )

            val result = if (increase) {

                value * (1 + percent / 100)

            } else {

                value * (1 - percent / 100)
            }

            return "= ${Numbers.format(result)}"
        }

        val root = Regex(
            """(?i)^(?:square root|sqrt)\s+(?:of\s+)?$N$"""
        ).matchEntire(input)

        if (root != null) {

            val n = root
                .groupValues[1]
                .toDouble()

            return if (n < 0) {

                "No real square root exists."

            } else {

                "= ${Numbers.format(sqrt(n))}"
            }
        }

        val logarithm = Regex(
            """(?i)^(?:ln|natural log)\s+(?:of\s+)?$N$"""
        ).matchEntire(input)

        if (logarithm != null) {

            val n = logarithm
                .groupValues[1]
                .toDouble()

            return if (n <= 0) {

                "The logarithm requires a positive input."

            } else {

                "= ${Numbers.format(ln(n))}"
            }
        }

        val power = Regex(
            """(?i)^$N\s+(?:to the power of|raised to)\s+$N$"""
        ).matchEntire(input)

        if (power != null) {

            val a = power
                .groupValues[1]
                .toDouble()

            val b = power
                .groupValues[2]
                .toDouble()

            return "= ${Numbers.format(a.pow(b))}"
        }

        val normalized = input
            .replace(
                Regex("""(?i)\bmultiplied by\b"""),
                "*"
            )
            .replace(
                Regex("""(?i)\bdivided by\b"""),
                "/"
            )
            .replace(
                Regex("""(?i)\btimes\b"""),
                "*"
            )
            .replace(
                Regex("""(?i)\bplus\b"""),
                "+"
            )
            .replace(
                Regex("""(?i)\bminus\b"""),
                "-"
            )
            .replace("×", "*")
            .replace("÷", "/")

        if (
            !normalized.matches(
                Regex(
                    """[0-9+\-*/^().\s]+"""
                )
            ) ||
            normalized.none {
                it.isDigit()
            }
        ) {

            return null
        }

        return try {

            val result =
                ExpressionParser(
                    normalized
                ).parse()

            "= ${Numbers.format(result)}"

        } catch (_: Exception) {

            "Invalid mathematical expression."
        }
    }
}

// ============================================================
// MIDDLE OFFICE — FINANCE
// ============================================================

private object FinanceEngine {

    private const val N =
        """(-?\d+(?:\.\d+)?)"""

    fun process(
        raw: String
    ): String? {

        val input = raw.trim()

        val compound = Regex(
            """(?i)^compound interest\s+$N\s+$N\s+$N(?:\s+(\d+))?$"""
        ).matchEntire(input)

        if (compound != null) {

            val principal =
                compound.groupValues[1].toDouble()

            val annualRate =
                compound.groupValues[2].toDouble() / 100

            val years =
                compound.groupValues[3].toDouble()

            val frequency =
                compound.groupValues[4]
                    .toIntOrNull() ?: 1

            if (
                principal < 0 ||
                years < 0 ||
                frequency !in 1..365 ||
                1 + annualRate / frequency <= 0
            ) {

                return "Invalid compound interest parameters."
            }

            val future =
                principal *
                (1 + annualRate / frequency)
                    .pow(frequency * years)

            return "Future value: ${Numbers.format(future)}\nInterest: ${Numbers.format(future - principal)}"
        }

        val simple = Regex(
            """(?i)^simple interest\s+$N\s+$N\s+$N$"""
        ).matchEntire(input)

        if (simple != null) {

            val principal =
                simple.groupValues[1].toDouble()

            val rate =
                simple.groupValues[2].toDouble() / 100

            val years =
                simple.groupValues[3].toDouble()

            if (
                principal < 0 ||
                years < 0
            ) {

                return "Invalid principal or time."
            }

            val interest =
                principal * rate * years

            return "Interest: ${Numbers.format(interest)}\nFuture value: ${Numbers.format(principal + interest)}"
        }

        val presentValue = Regex(
            """(?i)^present value\s+$N\s+$N\s+$N$"""
        ).matchEntire(input)

        if (presentValue != null) {

            val future =
                presentValue.groupValues[1].toDouble()

            val rate =
                presentValue.groupValues[2].toDouble() / 100

            val years =
                presentValue.groupValues[3].toDouble()

            if (
                rate <= -1 ||
                years < 0
            ) {

                return "Invalid rate or time."
            }

            val result =
                future / (1 + rate).pow(years)

            return "Present value: ${Numbers.format(result)}"
        }

        val futureValue = Regex(
            """(?i)^future value\s+$N\s+$N\s+$N$"""
        ).matchEntire(input)

        if (futureValue != null) {

            val principal =
                futureValue.groupValues[1].toDouble()

            val rate =
                futureValue.groupValues[2].toDouble() / 100

            val years =
                futureValue.groupValues[3].toDouble()

            if (
                rate <= -1 ||
                years < 0
            ) {

                return "Invalid rate or time."
            }

            return "Future value: ${Numbers.format(principal * (1 + rate).pow(years))}"
        }

        val dividendYield = Regex(
            """(?i)^dividend yield\s+$N\s+$N$"""
        ).matchEntire(input)

        if (dividendYield != null) {

            val dividend =
                dividendYield.groupValues[1].toDouble()

            val price =
                dividendYield.groupValues[2].toDouble()

            if (price <= 0) {
                return "Price must be positive."
            }

            return "Dividend yield: ${Numbers.percent(dividend / price * 100)}"
        }

        val holdingReturn = Regex(
            """(?i)^(?:investment return|holding period return|hpr)\s+$N\s+$N(?:\s+$N)?$"""
        ).matchEntire(input)

        if (holdingReturn != null) {

            val initial =
                holdingReturn.groupValues[1].toDouble()

            val final =
                holdingReturn.groupValues[2].toDouble()

            val income =
                holdingReturn.groupValues[3]
                    .toDoubleOrNull() ?: 0.0

            if (initial <= 0) {
                return "Initial investment must be positive."
            }

            return "Holding-period return: ${Numbers.percent((final - initial + income) / initial * 100)}"
        }

        val annuity = Regex(
            """(?i)^annuity pv\s+$N\s+$N\s+(\d+)$"""
        ).matchEntire(input)

        if (annuity != null) {

            val payment =
                annuity.groupValues[1].toDouble()

            val rate =
                annuity.groupValues[2].toDouble() / 100

            val periods =
                annuity.groupValues[3].toIntOrNull()

            if (
                periods == null ||
                periods > 100000 ||
                rate <= -1
            ) {

                return "Invalid annuity parameters."
            }

            val result = if (rate == 0.0) {

                payment * periods

            } else {

                payment *
                (1 - (1 + rate).pow(-periods)) /
                rate
            }

            return "Present value of ordinary annuity: ${Numbers.format(result)}"
        }

        val weighted = Regex(
            """(?i)^weighted return\s+(.+)$"""
        ).matchEntire(input)

        if (weighted != null) {

            val entries =
                weighted.groupValues[1].split(",")

            var totalWeight = 0.0
            var weightedSum = 0.0

            for (entry in entries) {

                val values = entry.trim()
                    .split(
                        Regex("""\s+""")
                    )

                if (values.size != 2) {

                    return "Format: weighted return 40 10, 60 5"
                }

                val weight =
                    values[0].toDoubleOrNull()

                val rate =
                    values[1].toDoubleOrNull()

                if (
                    weight == null ||
                    rate == null ||
                    weight < 0
                ) {

                    return "Invalid portfolio data."
                }

                totalWeight += weight

                weightedSum += weight * rate
            }

            if (totalWeight <= 0) {
                return "Total weight must be positive."
            }

            return "Weighted return: ${Numbers.percent(weightedSum / totalWeight)}"
        }

        return null
    }
}

// ============================================================
// MIDDLE OFFICE — UTILITIES
// ============================================================

private object UtilityEngine {

    private val random =
        SecureRandom()

    fun process(
        raw: String
    ): AssistantResult? {

        val input = raw.trim()

        val lower =
            input.lowercase(Locale.ENGLISH)

        if (
            lower == "flip coin" ||
            lower == "flip a coin"
        ) {

            return AssistantResult(
                if (random.nextBoolean()) {
                    "Heads"
                } else {
                    "Tails"
                }
            )
        }

        if (
            lower == "roll dice" ||
            lower == "roll a die"
        ) {

            return AssistantResult(
                "Dice: ${random.nextInt(6) + 1}"
            )
        }

        val randomNumber = Regex(
            """(?i)^random number\s+(-?\d+)\s+(-?\d+)$"""
        ).matchEntire(input)

        if (randomNumber != null) {

            val minimum =
                randomNumber.groupValues[1].toIntOrNull()

            val maximum =
                randomNumber.groupValues[2].toIntOrNull()

            if (
                minimum == null ||
                maximum == null ||
                minimum > maximum
            ) {

                return AssistantResult(
                    "Invalid range."
                )
            }

            val range =
                maximum.toLong() -
                minimum.toLong() + 1L

            val value =
                minimum.toLong() +
                (random.nextDouble() * range).toLong()

            return AssistantResult(
                "Random number: $value"
            )
        }

        val password = Regex(
            """(?i)^(?:generate password|password)(?:\s+(\d+))?$"""
        ).matchEntire(input)

        if (password != null) {

            val length =
                password.groupValues[1]
                    .toIntOrNull() ?: 16

            if (length !in 8..128) {

                return AssistantResult(
                    "Password length must be 8–128."
                )
            }

            val alphabet =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "abcdefghijklmnopqrstuvwxyz" +
                "0123456789" +
                "!@#%&*_-+="

            val result = buildString {

                repeat(length) {

                    append(
                        alphabet[
                            random.nextInt(
                                alphabet.length
                            )
                        ]
                    )
                }
            }

            return AssistantResult(
                result,
                sensitive = true
            )
        }

        val uppercase = Regex(
            """(?is)^uppercase\s+(.+)$"""
        ).matchEntire(input)

        if (uppercase != null) {

            return AssistantResult(
                uppercase.groupValues[1]
                    .uppercase(Locale.getDefault())
            )
        }

        val lowercase = Regex(
            """(?is)^lowercase\s+(.+)$"""
        ).matchEntire(input)

        if (lowercase != null) {

            return AssistantResult(
                lowercase.groupValues[1]
                    .lowercase(Locale.getDefault())
            )
        }

        val reverse = Regex(
            """(?is)^reverse text\s+(.+)$"""
        ).matchEntire(input)

        if (reverse != null) {

            return AssistantResult(
                reverse.groupValues[1]
                    .reversed()
            )
        }

        val count = Regex(
            """(?is)^count words\s+(.+)$"""
        ).matchEntire(input)

        if (count != null) {

            val text =
                count.groupValues[1]

            return AssistantResult(
                "Words: ${text.trim().split(Regex("""\s+""")).size}\nCharacters: ${text.length}"
            )
        }

        return null
    }
}

// ============================================================
// MIDDLE OFFICE — COMMAND PROCESSOR
// ============================================================

private class AstraProcessor(
    private val russianTutor: RussianTutor,
    private val everydayAssistant: EverydayAssistant,
    private val dialogue: AstraDialogue
) {

    fun process(
        raw: String,
        notes: MutableList<String>,
        tasks: MutableList<String>,
        completed: MutableList<String>
    ): AssistantResult {

        val clean = AstraInput.normalize(raw)
            .trimEnd('?', '!')
            .replace(
                Regex("""\s+"""),
                " "
            )

        val lower =
            clean.lowercase(Locale.ENGLISH)

        when (lower) {

            "clear chat",
            "clear history",
            "new conversation" -> {

                return AssistantResult(
                    "",
                    AssistantAction.CLEAR_CHAT
                )
            }

            "copy chat",
            "copy conversation" -> {

                return AssistantResult(
                    "",
                    AssistantAction.COPY_CHAT
                )
            }

            "share chat",
            "export chat" -> {

                return AssistantResult(
                    "",
                    AssistantAction.SHARE_CHAT
                )
            }

            "open tasks" -> {

                return AssistantResult(
                    "",
                    AssistantAction.OPEN_TASKS
                )
            }

            "open notes" -> {

                return AssistantResult(
                    "",
                    AssistantAction.OPEN_NOTES
                )
            }

            "open tools" -> {

                return AssistantResult(
                    "",
                    AssistantAction.OPEN_TOOLS
                )
            }

            "open settings" -> {

                return AssistantResult(
                    "",
                    AssistantAction.OPEN_SETTINGS
                )
            }
        }

        if (
            lower in listOf(
                "hi",
                "hello",
                "hey",
                "hello astra",
                "hi astra",
                "hey astra",
                "good morning",
                "good afternoon",
                "good evening"
            )
        ) {

            return AssistantResult(
                "Hello. What would you like me to do?"
            )
        }

        dialogue.respond(clean, tasks, notes, completed)?.let {
            return AssistantResult(it)
        }

        AstraRecall.respond(clean, notes, tasks)?.let {
            return AssistantResult(it)
        }

        russianTutor.respond(clean)?.let {
            return AssistantResult(it)
        }

        everydayAssistant.respond(clean, tasks)?.let {
            return AssistantResult(it)
        }

        if (
            lower in listOf(
                "time",
                "current time",
                "what time is it",
                "what's the time",
                "tell me the time"
            )
        ) {

            return AssistantResult(
                DateFormat.getTimeInstance(
                    DateFormat.SHORT
                ).format(Date())
            )
        }

        if (
            lower in listOf(
                "date",
                "today",
                "today's date",
                "what is the date",
                "what day is it"
            )
        ) {

            return AssistantResult(
                SimpleDateFormat(
                    "EEEE, d MMMM yyyy",
                    Locale.getDefault()
                ).format(Date())
            )
        }

        val addTask = Regex(
            """(?i)^(?:add task|add a task|create task|task|remind me to)\s+(.+)$"""
        ).matchEntire(clean)

        if (addTask != null) {

            val task =
                addTask.groupValues[1].trim()

            tasks.add(task)

            return AssistantResult(
                "Task added: $task"
            )
        }

        val done = Regex(
            """(?i)^(?:done|complete task|finish task)\s+(\d+)$"""
        ).matchEntire(clean)

        if (done != null) {

            val index = done
                .groupValues[1]
                .toIntOrNull()
                ?.minus(1)

            if (
                index != null &&
                index in tasks.indices
            ) {

                val task =
                    tasks.removeAt(index)

                completed.add(task)

                return AssistantResult(
                    "Completed: $task"
                )
            }

            return AssistantResult(
                "Task not found."
            )
        }

        if (
            lower in listOf(
                "tasks",
                "show tasks",
                "show my tasks"
            )
        ) {

            return AssistantResult(
                if (tasks.isEmpty()) {

                    "No outstanding tasks."

                } else {

                    tasks.mapIndexed { index, task ->
                        "${index + 1}. $task"
                    }.joinToString("\n")
                }
            )
        }

        if (
            lower in listOf(
                "completed tasks",
                "show completed tasks"
            )
        ) {

            return AssistantResult(
                if (completed.isEmpty()) {

                    "No completed tasks."

                } else {

                    completed.mapIndexed { index, task ->
                        "${index + 1}. $task"
                    }.joinToString("\n")
                }
            )
        }

        val note = Regex(
            """(?i)^(?:remember that|remember|note|save note)\s+(.+)$"""
        ).matchEntire(clean)

        if (note != null) {

            notes.add(
                note.groupValues[1].trim()
            )

            return AssistantResult(
                "Note saved."
            )
        }

        if (
            lower in listOf(
                "notes",
                "show notes",
                "show my notes"
            )
        ) {

            return AssistantResult(
                if (notes.isEmpty()) {

                    "No saved notes."

                } else {

                    notes.mapIndexed { index, item ->
                        "${index + 1}. $item"
                    }.joinToString("\n")
                }
            )
        }

        val search = Regex(
            """(?i)^(?:search|find)\s+(notes|tasks)\s+(?:for\s+)?(.+)$"""
        ).matchEntire(clean)

        if (search != null) {

            val source =
                if (
                    search.groupValues[1].equals(
                        "notes",
                        true
                    )
                ) {
                    notes
                } else {
                    tasks
                }

            val query =
                search.groupValues[2]

            val results =
                source.mapIndexedNotNull { index, item ->

                    if (
                        item.contains(
                            query,
                            true
                        )
                    ) {

                        "${index + 1}. $item"

                    } else {

                        null
                    }
                }

            return AssistantResult(
                if (results.isEmpty()) {

                    "No matches found."

                } else {

                    results.joinToString("\n")
                }
            )
        }

        BondAnalytics.respond(clean)?.let {
            return AssistantResult(it)
        }

        FinanceEngine.process(clean)?.let {

            return AssistantResult(it)
        }

        UtilityEngine.process(clean)?.let {

            return it
        }

        MathEngine.process(clean)?.let {

            return AssistantResult(it)
        }

        if (
            lower in listOf(
                "help",
                "commands",
                "what can you do"
            )
        ) {

            return AssistantResult(
                """
                LOCAL MEMORY
                memory overview
                recall bond duration
                what did I say about dividends
                find my notes about risk

                MISSION CONTROL
                mission control
                what should I do next
                make it high
                mark it done (requires confirmation)
                set task 1 priority high
                set task 2 priority medium
                show my progress

                FOLLOW-UP CONVERSATION
                add task (Astra asks what to add)
                remember (Astra asks for the note)
                cancel (stops a pending action)

                TASKS
                add task study finance
                show tasks
                done 1
                show completed tasks

                NOTES
                remember that bonds have duration risk
                show notes
                search notes for bonds

                MATHEMATICS
                calculate 12 * 5
                20 percent of 500
                increase 500 by 15%
                percentage change from 100 to 125
                square root of 81
                permutations 5 3
                combinations 5 3

                FINANCE
                bond analyze 1000 5 6 10 2
                Bond inputs: face, annual coupon %, YTM %, years, frequency
                Output: price, duration, convexity, DV01
                simple interest 10000 10 2
                compound interest 10000 10 2 12
                present value 10000 10 5
                future value 10000 10 5
                annuity pv 1000 5 10
                dividend yield 5 100
                investment return 100 120 5
                weighted return 40 10, 60 5

                DAILY LIFE
                plan my day
                add milk to shopping list
                shopping list
                remove milk from shopping list
                add habit exercise
                habit done exercise
                habits
                breathing exercise
                help me focus

                ONLINE REFERENCE (internet required)
                research the solar system
                search wikipedia for Botswana
                wikipedia bond duration
                Research queries are sent to Wikipedia.
                Astra does not send your private notes or tasks.

                UNIT CONVERSIONS
                convert 5 km to miles
                convert 100 c to f
                convert 75 kg to lb
                convert 2 hours to minutes

                LANGUAGES
                teach me Russian
                Russian lesson 2
                Russian quiz
                Russian progress
                answer thank you

                UTILITIES
                flip coin
                roll dice
                random number 1 100
                generate password 20
                uppercase hello
                lowercase HELLO
                reverse text hello
                count words this is a test

                SYSTEM
                copy chat
                share chat
                clear chat
                open tools
                open settings
                """.trimIndent()
            )
        }

        return AssistantResult(
            "I cannot answer that reliably with Astra's current offline tools. " +
                "I can help with tasks, notes, shopping lists, daily habits, unit conversions, " +
                "Russian practice, mathematics, and finance. Type 'help' for examples. " +
                "General open-ended questions will require an additional language model."
        )
    }
}

// ============================================================
// FRONT OFFICE — MESSAGE BUBBLE
// ============================================================

@OptIn(
    ExperimentalFoundationApi::class
)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onLongPress: () -> Unit
) {

    val isUser =
        message.role == "You"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (isUser) {
                Arrangement.End
            } else {
                Arrangement.Start
            }
    ) {

        Surface(
            color =
                if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
        ) {

            Column(
                modifier = Modifier.padding(14.dp)
            ) {

                Text(
                    message.role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(
                    Modifier.height(5.dp)
                )

                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

// ============================================================
// FRONT OFFICE — MAIN ACTIVITY
// ============================================================

class MainActivity :
    ComponentActivity(),
    TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    private var recognizer: SpeechRecognizer? = null

    private var ttsReady by mutableStateOf(false)

    private var offlineVoiceAvailable by mutableStateOf(false)

    private var offlineVoiceName by mutableStateOf("")

    private var listening by mutableStateOf(false)

    private var recognitionStatus by mutableStateOf(
        "Not initialized"
    )

    private var recognitionAvailable by mutableStateOf(false)

    private var speechResultCallback:
        ((String) -> Unit)? = null

    private var speechErrorCallback:
        ((String) -> Unit)? = null

    private var pendingVoiceStart:
        (() -> Unit)? = null

    override fun onInit(
        status: Int
    ) {

        ttsReady =
            status == TextToSpeech.SUCCESS

        if (ttsReady) {

            tts?.language =
                Locale.ENGLISH

            selectOfflineVoice()
        }
    }

    private fun selectOfflineVoice() {

        try {

            val voices =
                tts?.voices ?: emptySet<Voice>()

            val localVoice = voices
                .filter {

                    !it.isNetworkConnectionRequired &&
                    it.locale.language == "en"
                }
                .firstOrNull()

            if (localVoice != null) {

                tts?.voice = localVoice

                offlineVoiceAvailable = true

                offlineVoiceName =
                    localVoice.name

            } else {

                offlineVoiceAvailable = false

                offlineVoiceName = ""
            }

        } catch (_: Exception) {

            offlineVoiceAvailable = false
        }
    }

    private fun speak(
        text: String,
        offlineOnly: Boolean
    ) {

        if (!ttsReady) {
            return
        }

        if (
            offlineOnly &&
            !offlineVoiceAvailable
        ) {

            Toast.makeText(
                this,
                "No offline English voice installed.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "astra"
        )
    }

    private fun initializeRecognizer() {

        recognizer?.destroy()

        recognizer = null

        listening = false

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {

                recognizer =
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(
                        this
                    )

                recognitionAvailable = true

                recognitionStatus =
                    "On-device speech service installed (language support not verified)"

            } else {

                recognitionAvailable = false

                recognitionStatus =
                    "On-device recognition unavailable"
            }

        } catch (_: Exception) {

            recognitionAvailable = false

            recognitionStatus =
                "Recognition initialization failed"
        }
    }

    private fun startRecognition(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        languageTag: String,
        onLanguageUnsupported: (() -> Unit)? = null
    ) {

        speechResultCallback = onResult
        speechErrorCallback = onError

        val engine =
            recognizer ?: run {

                onError(
                    "Local recognition unavailable."
                )

                return
            }

        if (listening) {

            engine.stopListening()

            listening = false

            return
        }

        engine.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    listening = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(
                    rmsdB: Float
                ) {}

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {}

                override fun onEndOfSpeech() {

                    listening = false
                }

                override fun onError(
                    error: Int
                ) {

                    listening = false

                    if (
                        (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                         error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) &&
                        onLanguageUnsupported != null
                    ) {
                        onLanguageUnsupported()
                        return
                    }

                    speechErrorCallback?.invoke(
                        when (error) {

                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "No speech recognized."

                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "No speech detected."

                            SpeechRecognizer.ERROR_AUDIO ->
                                "Microphone error."

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission required."

                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                                "Speech recognition does not support the selected language. Check installed recognition languages in Android settings."

                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                "Speech recognition language is not available on this device. Install its offline language pack or select another language."

                            SpeechRecognizer.ERROR_NETWORK ->
                                "Speech service requested a network connection. Offline recognition may not be installed."

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Speech recognition is busy. Stop listening and try again."

                            SpeechRecognizer.ERROR_CLIENT ->
                                "Speech recognition could not start. Close and reopen the microphone, then try again."

                            else ->
                                "Speech recognition failed (code $error). Check the device speech service and microphone settings."
                        }
                    )
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    listening = false

                    val text = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()

                    if (!text.isNullOrBlank()) {

                        speechResultCallback?.invoke(
                            text
                        )

                    } else {

                        speechErrorCallback?.invoke(
                            "No speech recognized."
                        )
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                true
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                languageTag
            )
        }

        try {

            listening = true

            engine.startListening(intent)

        } catch (_: Exception) {

            listening = false

            onError(
                "Unable to start recognition."
            )
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        tts = TextToSpeech(
            this,
            this
        )

        initializeRecognizer()

        val storage =
            AstraStorage(this)

        val dialogue = AstraDialogue()

        val processor = AstraProcessor(
            RussianTutor(storage::getInt, storage::setInt),
            EverydayAssistant(storage::getString, storage::setString),
            dialogue
        )

        setContent {

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF79BFFF),
                    onPrimary = Color(0xFF061626),
                    primaryContainer = Color(0xFF153653),
                    onPrimaryContainer = Color(0xFFE6F3FF),
                    background = Color(0xFF090D14),
                    onBackground = Color(0xFFE8F0F9),
                    surface = Color(0xFF111925),
                    onSurface = Color(0xFFE8F0F9),
                    surfaceVariant = Color(0xFF253341),
                    onSurfaceVariant = Color(0xFFD0DDE9),
                    secondary = Color(0xFFBACBDD)
                )
            ) {

                val history = remember {

                    mutableStateListOf<ChatMessage>().apply {

                        addAll(
                            storage.loadHistory()
                        )

                        if (isEmpty()) {

                            add(
                                ChatMessage(
                                    "Astra",
                                    WELCOME
                                )
                            )
                        }
                    }
                }

                val notes = remember {

                    mutableStateListOf<String>().apply {

                        addAll(
                            storage.loadNotes()
                        )
                    }
                }

                val tasks = remember {

                    mutableStateListOf<String>().apply {

                        addAll(
                            storage.loadTasks()
                        )
                    }
                }

                val completed = remember {

                    mutableStateListOf<String>().apply {

                        addAll(
                            storage.loadCompletedTasks()
                        )
                    }
                }

                var input by remember {
                    mutableStateOf("")
                }

                var activeResearchCount by remember {
                    mutableIntStateOf(0)
                }

                var dialogueAwaiting by remember {
                    mutableStateOf(false)
                }

                var tab by remember {
                    mutableIntStateOf(0)
                }

                var showMenu by remember {
                    mutableStateOf(false)
                }

                var showClearDialog by remember {
                    mutableStateOf(false)
                }

                var pendingBackupRestore by remember {
                    mutableStateOf<AstraBackupCodec.Payload?>(null)
                }

                var selectedMessage by remember {
                    mutableStateOf<ChatMessage?>(null)
                }

                var editingNoteIndex by remember {
                    mutableIntStateOf(-1)
                }

                var editingNoteText by remember {
                    mutableStateOf("")
                }

                var deleteNoteIndex by remember {
                    mutableIntStateOf(-1)
                }

                var autoSendVoice by remember {

                    mutableStateOf(
                        storage.getBoolean(
                            "auto_send_voice"
                        )
                    )
                }

                var speakResponses by remember {

                    mutableStateOf(
                        storage.getBoolean(
                            "speak_responses"
                        )
                    )
                }

                var localOnlyTts by remember {

                    mutableStateOf(
                        storage.getBoolean(
                            "local_only_tts",
                            true
                        )
                    )
                }

                var allowSystemSpeech by remember {

                    mutableStateOf(
                        storage.getBoolean(
                            "allow_system_speech"
                        )
                    )
                }

                var voiceError by remember {
                    mutableStateOf("")
                }

                var speechLanguage by remember {
                    mutableStateOf(
                        SpeechLanguage.fromTag(
                            storage.getString("speech_language", "en-US")
                        )
                    )
                }

                var speechLanguageMenuOpen by remember {
                    mutableStateOf(false)
                }

                var toolInput by remember {
                    mutableStateOf("")
                }

                var toolOutput by remember {
                    mutableStateOf("")
                }

                val keyboard =
                    LocalSoftwareKeyboardController.current

                val research = remember {
                    WikipediaResearch()
                }

                val researchScope = rememberCoroutineScope()

                fun persist() {

                    storage.save(
                        history.toList(),
                        notes.toList(),
                        tasks.toList(),
                        completed.toList()
                    )
                }

                fun copyText(
                    value: String
                ) {

                    val clipboard =
                        getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "Astra",
                            value
                        )
                    )

                    Toast.makeText(
                        this@MainActivity,
                        "Copied",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                fun shareText(
                    value: String
                ) {

                    val intent = Intent(
                        Intent.ACTION_SEND
                    ).apply {

                        type = "text/plain"

                        putExtra(
                            Intent.EXTRA_TEXT,
                            value
                        )
                    }

                    startActivity(
                        Intent.createChooser(
                            intent,
                            "Share"
                        )
                    )
                }

                fun submit(
                    raw: String
                ) {

                    val message = raw.trim()

                    if (message.isEmpty()) {
                        return
                    }

                    val normalized = AstraInput.normalize(message)
                    val researchTopic = ResearchCommand.extract(normalized)
                    if (researchTopic != null) {
                        dialogue.dismissIncompletePrompt()
                        dialogueAwaiting = false
                        val placeholder =
                            "Searching Wikipedia for " + researchTopic + "..."

                        history.add(ChatMessage("You", message))
                        history.add(ChatMessage("Astra", placeholder))
                        while (history.size > MAX_HISTORY) {
                            history.removeAt(0)
                        }
                        val placeholderIndex = history.lastIndex
                        persist()
                        input = ""
                        keyboard?.hide()

                        activeResearchCount += 1
                        researchScope.launch {
                            try {
                                val answer = research.search(researchTopic)
                                // Don't restore results after a conversation clear.
                                if (
                                    placeholderIndex in history.indices &&
                                    history[placeholderIndex].role == "Astra" &&
                                    history[placeholderIndex].text == placeholder
                                ) {
                                    history[placeholderIndex] =
                                        ChatMessage("Astra", answer)
                                    persist()
                                }
                            } finally {
                                activeResearchCount = (activeResearchCount - 1).coerceAtLeast(0)
                            }
                        }
                        return
                    }

                    val result =
                        processor.process(
                            normalized,
                            notes,
                            tasks,
                            completed
                        )
                    dialogueAwaiting = dialogue.awaitingResponse

                    if (!result.sensitive) {

                        history.add(
                            ChatMessage(
                                "You",
                                message
                            )
                        )
                    }

                    when (result.action) {

                        AssistantAction.CLEAR_CHAT -> {
                            showClearDialog = true
                        }

                        AssistantAction.COPY_CHAT -> {

                            copyText(
                                exportConversation(
                                    history
                                )
                            )
                        }

                        AssistantAction.SHARE_CHAT -> {

                            shareText(
                                exportConversation(
                                    history
                                )
                            )
                        }

                        AssistantAction.OPEN_TASKS -> {
                            tab = 1
                        }

                        AssistantAction.OPEN_NOTES -> {
                            tab = 2
                        }

                        AssistantAction.OPEN_TOOLS -> {
                            tab = 3
                        }

                        AssistantAction.OPEN_SETTINGS -> {
                            tab = 4
                        }

                        AssistantAction.NONE -> Unit
                    }

                    if (
                        result.text.isNotBlank()
                    ) {

                        if (result.sensitive) {

                            copyText(result.text)

                            history.add(
                                ChatMessage(
                                    "Astra",
                                    "Sensitive output generated and copied. It was not saved to chat history."
                                )
                            )

                        } else {

                            history.add(
                                ChatMessage(
                                    "Astra",
                                    result.text
                                )
                            )

                            if (speakResponses) {

                                speak(
                                    result.text,
                                    localOnlyTts
                                )
                            }
                        }
                    }

                    while (
                        history.size > MAX_HISTORY
                    ) {
                        history.removeAt(0)
                    }

                    persist()

                    input = ""

                    keyboard?.hide()
                }

                val systemSpeech =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts
                            .StartActivityForResult()
                    ) { result ->

                        val words = result.data
                            ?.getStringArrayListExtra(
                                RecognizerIntent.EXTRA_RESULTS
                            )
                            ?.firstOrNull()

                        if (!words.isNullOrBlank()) {

                            if (autoSendVoice) {
                                submit(words)
                            } else {
                                input = words
                            }
                        }
                    }

                val exportBackupLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        if (uri != null) {
                            researchScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val backup = storage.exportBackup()
                                        val output = contentResolver.openOutputStream(uri)
                                            ?: throw IllegalStateException("Cannot write the selected file.")
                                        output.use {
                                            it.write(backup.toByteArray(Charsets.UTF_8))
                                        }
                                    }
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Astra backup exported.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (error: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        error.message ?: "Could not export backup.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }

                val importBackupLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            researchScope.launch {
                                try {
                                    pendingBackupRestore = withContext(Dispatchers.IO) {
                                        val source = contentResolver.openInputStream(uri)
                                            ?: throw IllegalStateException("Cannot open selected file.")
                                        val contents = source.use {
                                            AstraBackupCodec.readLimited(it)
                                        }
                                        AstraBackupCodec.parse(contents)
                                    }
                                } catch (error: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        error.message ?: "Could not read backup.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }

                val microphonePermission =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->

                        if (granted) {

                            initializeRecognizer()

                            pendingVoiceStart?.invoke()

                        } else {

                            voiceError =
                                "Microphone permission denied."
                        }

                        pendingVoiceStart = null
                    }

                fun launchSystemSpeech() {
                    try {
                        val intent = Intent(
                            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                        ).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                                true
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE,
                                speechLanguage.tag
                            )
                        }
                        systemSpeech.launch(intent)
                    } catch (_: Exception) {
                        voiceError =
                            "No compatible system speech service is installed."
                    }
                }

                fun startVoice() {
                    voiceError = ""

                    val resultCallback: (String) -> Unit = { words ->
                        if (autoSendVoice) {
                            submit(words)
                        } else {
                            input = words
                        }
                    }

                    val errorCallback: (String) -> Unit = { message ->
                        voiceError = message
                    }

                    val unsupportedFallback: (() -> Unit)? =
                        if (allowSystemSpeech) {
                            { launchSystemSpeech() }
                        } else {
                            null
                        }

                    if (
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingVoiceStart = {
                            startRecognition(
                                resultCallback,
                                errorCallback,
                                speechLanguage.tag,
                                unsupportedFallback
                            )
                        }
                        microphonePermission.launch(
                            Manifest.permission.RECORD_AUDIO
                        )
                        return
                    }

                    if (recognitionAvailable) {
                        startRecognition(
                            resultCallback,
                            errorCallback,
                            speechLanguage.tag,
                            unsupportedFallback
                        )
                    } else if (allowSystemSpeech) {
                        launchSystemSpeech()
                    } else {
                        voiceError =
                            "On-device speech recognition is unavailable. Enable system fallback or install a compatible offline speech model."
                    }
                }

                val scroll =
                    rememberLazyListState()

                LaunchedEffect(
                    history.size,
                    tab
                ) {

                    if (
                        tab == 0 &&
                        history.isNotEmpty()
                    ) {

                        scroll.animateScrollToItem(
                            history.lastIndex
                        )
                    }
                }

                val restoreToConfirm = pendingBackupRestore
                if (restoreToConfirm != null) {
                    AlertDialog(
                        onDismissRequest = { pendingBackupRestore = null },
                        title = { Text("Restore Astra data?") },
                        text = {
                            Text(
                                restoreToConfirm.preview() +
                                "\n\nRestoring replaces existing saved chat, notes, tasks, habits, " +
                                "shopping lists and preferences. This action cannot be undone. " +
                                "Ensure the source file is trustworthy."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    pendingBackupRestore = null
                                    researchScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                storage.restoreBackup(restoreToConfirm)
                                            }
                                            history.clear()
                                            history.addAll(storage.loadHistory())
                                            if (history.isEmpty()) {
                                                history.add(ChatMessage("Astra", WELCOME))
                                            }
                                            notes.clear()
                                            notes.addAll(storage.loadNotes())
                                            tasks.clear()
                                            tasks.addAll(storage.loadTasks())
                                            completed.clear()
                                            completed.addAll(storage.loadCompletedTasks())
                                            autoSendVoice = storage.getBoolean("auto_send_voice")
                                            speakResponses = storage.getBoolean("speak_responses")
                                            localOnlyTts = storage.getBoolean("local_only_tts", true)
                                            allowSystemSpeech = storage.getBoolean("allow_system_speech")
                                            speechLanguage = SpeechLanguage.fromTag(
                                                storage.getString("speech_language", "en-US")
                                            )
                                            dialogue.reset()
                                            dialogueAwaiting = false
                                            tab = 4
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Astra data restored.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } catch (error: Exception) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                error.message ?: "Could not restore backup.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            ) { Text("Restore data") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { pendingBackupRestore = null }
                            ) { Text("Cancel") }
                        }
                    )
                }

                if (showClearDialog) {

                    AlertDialog(
                        onDismissRequest = {
                            showClearDialog = false
                        },
                        title = {
                            Text("Clear conversation?")
                        },
                        text = {
                            Text(
                                "This permanently deletes saved chat history."
                            )
                        },
                        confirmButton = {

                            TextButton(
                                onClick = {

                                    history.clear()

                                    history.add(
                                        ChatMessage(
                                            "Astra",
                                            WELCOME
                                        )
                                    )

                                    persist()

                                    dialogue.reset()
                                    dialogueAwaiting = false
                                    showClearDialog = false
                                }
                            ) {
                                Text("Clear")
                            }
                        },
                        dismissButton = {

                            TextButton(
                                onClick = {
                                    showClearDialog = false
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                selectedMessage?.let { message ->

                    AlertDialog(
                        onDismissRequest = {
                            selectedMessage = null
                        },
                        title = {
                            Text("Message options")
                        },
                        text = {
                            Text(message.text)
                        },
                        confirmButton = {

                            TextButton(
                                onClick = {

                                    copyText(message.text)

                                    selectedMessage = null
                                }
                            ) {
                                Text("Copy")
                            }
                        },
                        dismissButton = {

                            TextButton(
                                onClick = {

                                    selectedMessage = null

                                    shareText(message.text)
                                }
                            ) {
                                Text("Share")
                            }
                        }
                    )
                }

                if (editingNoteIndex >= 0) {

                    AlertDialog(
                        onDismissRequest = {
                            editingNoteIndex = -1
                        },
                        title = {
                            Text("Edit note")
                        },
                        text = {

                            OutlinedTextField(
                                value = editingNoteText,
                                onValueChange = {
                                    editingNoteText = it
                                },
                                label = {
                                    Text("Note")
                                }
                            )
                        },
                        confirmButton = {

                            TextButton(
                                onClick = {

                                    val index =
                                        editingNoteIndex

                                    if (
                                        index in notes.indices &&
                                        editingNoteText.isNotBlank()
                                    ) {

                                        notes[index] =
                                            editingNoteText.trim()

                                        persist()
                                    }

                                    editingNoteIndex = -1
                                }
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {

                            TextButton(
                                onClick = {
                                    editingNoteIndex = -1
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (deleteNoteIndex >= 0) {

                    AlertDialog(
                        onDismissRequest = {
                            deleteNoteIndex = -1
                        },
                        title = {
                            Text("Delete note?")
                        },
                        text = {
                            Text(
                                "This cannot be undone."
                            )
                        },
                        confirmButton = {

                            TextButton(
                                onClick = {

                                    if (
                                        deleteNoteIndex in notes.indices
                                    ) {

                                        notes.removeAt(
                                            deleteNoteIndex
                                        )

                                        persist()
                                    }

                                    deleteNoteIndex = -1
                                }
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {

                            TextButton(
                                onClick = {
                                    deleteNoteIndex = -1
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Color(0xFF080E19),
                                Color(0xFF0A1424),
                                Color(0xFF070B13)
                            )
                        )
                    ),
                    containerColor = Color.Transparent,

                    topBar = {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            if (tab == 0) {
                                AstraBrand(listening = listening)
                            } else {
                                Text(
                                    when (tab) {
                                        1 -> "Tasks"
                                        2 -> "Notes"
                                        3 -> "Tools"
                                        else -> "Settings"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }

                            if (tab == 0) {

                                Box {

                                    TextButton(
                                        onClick = {
                                            showMenu = true
                                        }
                                    ) {
                                        Text("⋮")
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = {
                                            showMenu = false
                                        }
                                    ) {

                                        DropdownMenuItem(
                                            text = {
                                                Text("Copy chat")
                                            },
                                            onClick = {

                                                showMenu = false

                                                copyText(
                                                    exportConversation(
                                                        history
                                                    )
                                                )
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Text("Share chat")
                                            },
                                            onClick = {

                                                showMenu = false

                                                shareText(
                                                    exportConversation(
                                                        history
                                                    )
                                                )
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Text("New conversation")
                                            },
                                            onClick = {

                                                showMenu = false

                                                showClearDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },

                    bottomBar = {

                        NavigationBar {

                            listOf(
                                "Chat",
                                "Tasks",
                                "Notes",
                                "Tools",
                                "Settings"
                            ).forEachIndexed { index, title ->

                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = {
                                        tab = index
                                    },
                                    icon = {
                                        Text(
                                            listOf(
                                                "✦",
                                                "✓",
                                                "▤",
                                                "⌘",
                                                "⚙"
                                            )[index]
                                        )
                                    },
                                    label = {
                                        Text(title)
                                    }
                                )
                            }
                        }
                    }

                ) { padding ->

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .animateContentSize(animationSpec = tween(220))
                            .padding(
                                horizontal = 14.dp
                            )
                    ) {

                        when (tab) {

                            // ========================================
                            // CHAT
                            // ========================================

                            0 -> {

                                AstraCommandDeck(
                                    listening = listening,
                                    researching = activeResearchCount > 0,
                                    openTasks = tasks.size,
                                    completedTasks = completed.size,
                                    storedNotes = notes.size,
                                    awaitingReply = dialogueAwaiting,
                                    onCommand = { command -> submit(command) }
                                )

                                Spacer(Modifier.height(8.dp))

                                LazyColumn(
                                    modifier =
                                        Modifier.weight(1f),
                                    state = scroll,
                                    verticalArrangement =
                                        Arrangement.spacedBy(9.dp),
                                    contentPadding =
                                        PaddingValues(
                                            vertical = 12.dp
                                        )
                                ) {

                                    itemsIndexed(
                                        history
                                    ) { _, message ->

                                        MessageBubble(
                                            message = message,
                                            onLongPress = {

                                                selectedMessage =
                                                    message
                                            }
                                        )
                                    }
                                }

                                if (
                                    voiceError.isNotBlank()
                                ) {

                                    Text(
                                        voiceError,
                                        color =
                                            MaterialTheme.colorScheme.error,
                                        style =
                                            MaterialTheme.typography.bodySmall
                                    )
                                }

                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    verticalAlignment =
                                        Alignment.CenterVertically,
                                    horizontalArrangement =
                                        Arrangement.spacedBy(8.dp)
                                ) {

                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = {
                                            input = it
                                        },
                                        modifier =
                                            Modifier.weight(1f),
                                        placeholder = {
                                            Text(
                                                "Message Astra..."
                                            )
                                        },
                                        maxLines = 4,
                                        shape =
                                            RoundedCornerShape(24.dp),
                                        keyboardOptions =
                                            KeyboardOptions(
                                                imeAction =
                                                    ImeAction.Send
                                            ),
                                        keyboardActions =
                                            KeyboardActions(
                                                onSend = {
                                                    submit(input)
                                                }
                                            )
                                    )

                                    FilledIconButton(
                                        onClick = {
                                            submit(input)
                                        },
                                        enabled =
                                            input.isNotBlank()
                                    ) {
                                        Text("➤")
                                    }
                                }

                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {

                                    TextButton(
                                        onClick = {
                                            startVoice()
                                        }
                                    ) {

                                        Text(
                                            if (listening) {
                                                "Stop"
                                            } else {
                                                "Speak"
                                            }
                                        )
                                    }

                                    TextButton(
                                        onClick = {

                                            val reply =
                                                history.lastOrNull {
                                                    it.role == "Astra"
                                                }?.text ?: ""

                                            speak(
                                                reply,
                                                localOnlyTts
                                            )
                                        }
                                    ) {
                                        Text("Read aloud")
                                    }
                                }
                            }

                            // ========================================
                            // TASKS
                            // ========================================

                            1 -> {

                                Spacer(
                                    Modifier.height(12.dp)
                                )

                                Text(
                                    "Outstanding: ${tasks.size} | Completed: ${completed.size}"
                                )

                                Spacer(
                                    Modifier.height(12.dp)
                                )

                                LazyColumn {

                                    itemsIndexed(
                                        tasks
                                    ) { index, task ->

                                        Card(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        vertical = 4.dp
                                                    )
                                        ) {

                                            Row(
                                                modifier =
                                                    Modifier.padding(12.dp),
                                                verticalAlignment =
                                                    Alignment.CenterVertically
                                            ) {

                                                Text(
                                                    task,
                                                    modifier =
                                                        Modifier.weight(1f)
                                                )

                                                TextButton(
                                                    onClick = {

                                                        if (
                                                            index in tasks.indices
                                                        ) {

                                                            completed.add(
                                                                tasks.removeAt(
                                                                    index
                                                                )
                                                            )

                                                            persist()
                                                        }
                                                    }
                                                ) {
                                                    Text("Done")
                                                }
                                            }
                                        }
                                    }

                                    if (completed.isNotEmpty()) {

                                        item {

                                            Spacer(
                                                Modifier.height(16.dp)
                                            )

                                            Text(
                                                "Completed tasks",
                                                style =
                                                    MaterialTheme.typography.titleMedium
                                            )
                                        }

                                        itemsIndexed(
                                            completed
                                        ) { index, task ->

                                            Row(
                                                modifier =
                                                    Modifier.fillMaxWidth(),
                                                verticalAlignment =
                                                    Alignment.CenterVertically
                                            ) {

                                                Text(
                                                    task,
                                                    modifier =
                                                        Modifier.weight(1f)
                                                )

                                                TextButton(
                                                    onClick = {

                                                        if (
                                                            index in completed.indices
                                                        ) {

                                                            tasks.add(
                                                                completed.removeAt(
                                                                    index
                                                                )
                                                            )

                                                            persist()
                                                        }
                                                    }
                                                ) {
                                                    Text("Restore")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ========================================
                            // NOTES
                            // ========================================

                            2 -> {

                                Spacer(
                                    Modifier.height(12.dp)
                                )

                                LazyColumn {

                                    itemsIndexed(
                                        notes
                                    ) { index, note ->

                                        Card(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        vertical = 4.dp
                                                    )
                                        ) {

                                            Column(
                                                modifier =
                                                    Modifier.padding(12.dp)
                                            ) {

                                                Text(note)

                                                Row(
                                                    modifier =
                                                        Modifier.fillMaxWidth(),
                                                    horizontalArrangement =
                                                        Arrangement.End
                                                ) {

                                                    TextButton(
                                                        onClick = {

                                                            editingNoteIndex =
                                                                index

                                                            editingNoteText =
                                                                note
                                                        }
                                                    ) {
                                                        Text("Edit")
                                                    }

                                                    TextButton(
                                                        onClick = {

                                                            deleteNoteIndex =
                                                                index
                                                        }
                                                    ) {
                                                        Text("Delete")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ========================================
                            // TOOLS
                            // ========================================

                            3 -> {

                                Spacer(
                                    Modifier.height(12.dp)
                                )

                                Text(
                                    "Local tools",
                                    style =
                                        MaterialTheme.typography.titleMedium
                                )

                                Spacer(
                                    Modifier.height(8.dp)
                                )

                                Text(
                                    "Mathematics, finance, combinatorics and text utilities."
                                )

                                Spacer(
                                    Modifier.height(12.dp)
                                )

                                OutlinedTextField(
                                    value = toolInput,
                                    onValueChange = {
                                        toolInput = it
                                    },
                                    label = {
                                        Text("Tool command")
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth()
                                )

                                Spacer(
                                    Modifier.height(8.dp)
                                )

                                Button(
                                    onClick = {

                                        val result =
                                            processor.process(
                                                toolInput,
                                                notes,
                                                tasks,
                                                completed
                                            )

                                        toolOutput =
                                            if (result.sensitive) {

                                                copyText(result.text)

                                                "Sensitive result copied to clipboard."

                                            } else {

                                                result.text
                                            }

                                        persist()
                                    }
                                ) {
                                    Text("Run")
                                }

                                Spacer(
                                    Modifier.height(12.dp)
                                )

                                Text(toolOutput)
                            }

                            // ========================================
                            // SETTINGS
                            // ========================================

                            4 -> {

                                CompositionLocalProvider(
                                    LocalContentColor provides MaterialTheme.colorScheme.onBackground
                                ) {
                                LazyColumn(
                                    contentPadding =
                                        PaddingValues(
                                            vertical = 12.dp
                                        ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(14.dp)
                                ) {

                                    item {

                                        Text(
                                            "Astra v$ASTRA_VERSION",
                                            style =
                                                MaterialTheme.typography.titleLarge
                                        )
                                    }

                                    item {

                                        Text(
                                            "A local-first assistant for tasks, notes, daily planning, shopping, habits, language practice, mathematics, finance and unit conversions. Explicit Wikipedia research requires the internet and sends only the requested topic. General-purpose language-model responses are not yet installed."
                                        )
                                    }

                                    item {
                                        HorizontalDivider()
                                    }

                                    item {

                                        Text(
                                            "Voice recognition",
                                            style =
                                                MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    item {

                                        Text(
                                            recognitionStatus
                                        )
                                    }

                                    item {
                                        Column(
                                            verticalArrangement =
                                                Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "Recognition language",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Box {
                                                OutlinedButton(
                                                    onClick = {
                                                        speechLanguageMenuOpen = true
                                                    }
                                                ) {
                                                    Text(speechLanguage.displayName)
                                                }

                                                DropdownMenu(
                                                    expanded = speechLanguageMenuOpen,
                                                    onDismissRequest = {
                                                        speechLanguageMenuOpen = false
                                                    }
                                                ) {
                                                    SpeechLanguage.entries.forEach { language ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(language.displayName)
                                                            },
                                                            onClick = {
                                                                speechLanguage = language
                                                                speechLanguageMenuOpen = false
                                                                storage.setString(
                                                                    "speech_language",
                                                                    language.tag
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                "The selected language must be supported by the installed recognizer. System fallback may use the network even when offline recognition is preferred.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    item {

                                        OutlinedButton(
                                            onClick = {
                                                initializeRecognizer()
                                            }
                                        ) {
                                            Text("Check local recognition")
                                        }
                                    }

                                    item {

                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            Text(
                                                "Allow system speech fallback",
                                                modifier =
                                                    Modifier.weight(1f)
                                            )

                                            Switch(
                                                checked =
                                                    allowSystemSpeech,
                                                onCheckedChange = {

                                                    allowSystemSpeech = it

                                                    storage.setBoolean(
                                                        "allow_system_speech",
                                                        it
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    item {

                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            Text(
                                                "Automatically send recognized speech",
                                                modifier =
                                                    Modifier.weight(1f)
                                            )

                                            Switch(
                                                checked =
                                                    autoSendVoice,
                                                onCheckedChange = {

                                                    autoSendVoice = it

                                                    storage.setBoolean(
                                                        "auto_send_voice",
                                                        it
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    item {
                                        HorizontalDivider()
                                    }

                                    item {

                                        Text(
                                            "Speech output",
                                            style =
                                                MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    item {

                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            Text(
                                                "Offline voice only",
                                                modifier =
                                                    Modifier.weight(1f)
                                            )

                                            Switch(
                                                checked =
                                                    localOnlyTts,
                                                onCheckedChange = {

                                                    localOnlyTts = it

                                                    storage.setBoolean(
                                                        "local_only_tts",
                                                        it
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    item {

                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            Text(
                                                "Speak responses",
                                                modifier =
                                                    Modifier.weight(1f)
                                            )

                                            Switch(
                                                checked =
                                                    speakResponses,
                                                onCheckedChange = {

                                                    speakResponses = it

                                                    storage.setBoolean(
                                                        "speak_responses",
                                                        it
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    item {

                                        Text(
                                            if (offlineVoiceAvailable) {
                                                "Offline voice: $offlineVoiceName"
                                            } else {
                                                "Offline English voice unavailable."
                                            }
                                        )
                                    }

                                    item {

                                        OutlinedButton(
                                            onClick = {

                                                selectOfflineVoice()

                                                speak(
                                                    "Astra voice test.",
                                                    localOnlyTts
                                                )
                                            }
                                        ) {
                                            Text("Test voice")
                                        }
                                    }

                                    item {
                                        HorizontalDivider()
                                    }

                                    item {

                                        Text(
                                            "Conversation",
                                            style =
                                                MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    item {

                                        Text(
                                            "Messages: ${history.size} / $MAX_HISTORY"
                                        )
                                    }

                                    item {

                                        OutlinedButton(
                                            onClick = {

                                                copyText(
                                                    exportConversation(
                                                        history
                                                    )
                                                )
                                            }
                                        ) {
                                            Text("Copy chat")
                                        }
                                    }

                                    item {

                                        OutlinedButton(
                                            onClick = {

                                                shareText(
                                                    exportConversation(
                                                        history
                                                    )
                                                )
                                            }
                                        ) {
                                            Text("Share chat")
                                        }
                                    }

                                    item {

                                        OutlinedButton(
                                            onClick = {
                                                showClearDialog = true
                                            }
                                        ) {
                                            Text("Clear chat")
                                        }
                                    }

                                    item {
                                        HorizontalDivider()
                                    }

                                    item {
                                        HorizontalDivider()
                                    }

                                    item {
                                        Text(
                                            "Data backup & recovery",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }

                                    item {
                                        Text(
                                            "Export a local Astra JSON backup containing chat, notes, " +
                                            "tasks, shopping lists, habits, learning progress and settings. " +
                                            "The file is not encrypted, so store it privately. " +
                                            "Import replaces the current saved data after confirmation."
                                        )
                                    }

                                    item {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    exportBackupLauncher.launch("Astra-backup.json")
                                                }
                                            ) { Text("Export backup") }

                                            OutlinedButton(
                                                onClick = {
                                                    importBackupLauncher.launch(
                                                        arrayOf("application/json", "text/plain", "*/*")
                                                    )
                                                }
                                            ) { Text("Import backup") }
                                        }
                                    }

                                    item {
                                        HorizontalDivider()
                                    }

                                    item {
                                        Text(
                                            "Help & user guide",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }

                                    item {
                                        Text(
                                            "CHAT — Type a request and press Send. Try: hi Astra, help, teach me Russian, calculate 25 * 4, or what can you do? Some requests are not yet supported by the offline command engine."
                                        )
                                    }

                                    item {
                                        Text(
                                            "TASKS — Use the Tasks tab to create and complete tasks. NOTES — Use Notes to save and review information. TOOLS — Open Tools for calculations and utilities."
                                        )
                                    }

                                    item {
                                        Text(
                                            "VOICE — Grant microphone permission, select a supported recognition language in your device settings, then tap Speak. Error 12 means the speech service does not support the selected language; error 13 means its language files are unavailable. An installed text-to-speech voice does not guarantee recognition support."
                                        )
                                    }

                                    item {
                                        Text(
                                            "PRIVACY — Notes, tasks and conversation history are stored on this device. System speech fallback may use network services. Disable fallback for stricter local-only operation."
                                        )
                                    }

                                    item {

                                        Text(
                                            "Privacy and limitations",
                                            style =
                                                MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    item {

                                        Text(
                                            "Conversation history, notes and tasks are stored locally. Clipboard and shared exports may be accessible to other applications."
                                        )
                                    }

                                    item {

                                        Text(
                                            "On-device speech recognition requires a compatible installed speech service. System speech fallback may use network services."
                                        )
                                    }

                                    item {

                                        Text(
                                            "Astra currently uses deterministic command processing. Generative AI, background simulations, scheduled reminders and live financial data are not included."
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun exportConversation(
        messages: List<ChatMessage>
    ): String {

        return buildString {

            append("ASTRA CONVERSATION\n\n")

            messages.forEach {

                append(it.role)
                append(": ")
                append(it.text)
                append("\n\n")
            }

        }.trim()
    }

    override fun onDestroy() {

        recognizer?.cancel()
        recognizer?.destroy()

        recognizer = null

        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }
}
