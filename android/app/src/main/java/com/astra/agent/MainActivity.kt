package com.astra.agent

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
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
ASTRA v0.10.2 — OFFLINE AI ASSISTANT
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

private val ASTRA_VERSION: String get() = BuildConfig.VERSION_NAME
private const val MAX_HISTORY = AstraConversationHistory.MAX_RETAINED_TURNS

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
    NEW_CONVERSATION,
    OPEN_CONVERSATIONS,
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

    fun loadArchivedChats(): List<AstraChatArchive.Chat> =
        AstraChatArchive.decode(getString("astra_chat_archive", "[]"))

    fun saveArchivedChats(chats: List<AstraChatArchive.Chat>) {
        setString("astra_chat_archive", AstraChatArchive.encode(chats))
    }

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

                val role = item.optString("role")
                ChatMessage(
                    role,
                    AstraOfflinePolicy.recoverInterruptedMessage(
                        role, item.optString("text")
                    )
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

    /**
     * Commit all workflow changes together before updating Compose UI.
     * Existing JSON and percent-encoded collection formats stay unchanged.
     */
    fun commitWorkflow(next: AstraWorkflow.Snapshot) {
        fun json(items: List<String>): String =
            JSONArray().apply { items.forEach { put(it) } }.toString()

        val saved = prefs.edit()
            .putString("tasks", json(next.tasks))
            .putString("completed_tasks", json(next.completed))
            .putString("notes", json(next.notes))
            .putString("astra_shopping_items", AstraWorkflow.encodeItems(next.shopping))
            .putString("astra_habits", AstraWorkflow.encodeItems(next.habits))
            .commit()
        check(saved) { "Could not save the workflow; nothing was applied." }
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
    private val multilingualTutor: AstraMultilingualTutor,
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

        AstraConversationCommands.classify(lower)?.let { command ->
            return AssistantResult(
                "",
                when (command) {
                    AstraConversationCommands.Action.NEW_CONVERSATION ->
                        AssistantAction.NEW_CONVERSATION
                    AstraConversationCommands.Action.OPEN_CONVERSATIONS ->
                        AssistantAction.OPEN_CONVERSATIONS
                    AstraConversationCommands.Action.CLEAR_CURRENT ->
                        AssistantAction.CLEAR_CHAT
                }
            )
        }

        when (lower) {

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

        // An explicit lesson or quiz answer takes precedence over an
        // unfinished task/note dialogue. Never save a language command as
        // a task merely because another prompt was pending.
        multilingualTutor.respond(clean)?.let {
            dialogue.dismissIncompletePrompt()
            return AssistantResult(it)
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

        // Numeric projections are deterministic; never ask Qwen3 to
        // hallucinate an investment calculation.
        AstraNaturalFinance.respond(clean)?.let {
            return AssistantResult(it)
        }

        // Common educational finance questions receive a reliable offline
        // explanation even if Qwen3 weights have not yet been installed.
        AstraFinanceEducation.respond(clean)?.let {
            return AssistantResult(it)
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

        if (lower == "help" || lower == "what can you do") {
            return AssistantResult(AstraHelp.concise())
        }

        if (lower == "commands") {

            return AssistantResult(
                """
                OFFLINE AI
                Ask any normal question in chat.
                ask ai explain bond convexity
                AI runs on your phone without a model server.
                Tap Stop while Astra generates a response.

                LOCAL MEMORY
                memory overview
                recall bond duration
                what did I say about dividends
                find my notes about risk
                ask my memory why did I choose bonds?
                analyse my notes about portfolio risk
                Tap Astra icon → Search all local memory → Ask with evidence.
                Grounded AI answers include the original source excerpts.

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

                NATURAL FINANCIAL CALCULATIONS (offline)
                What will P50,000 grow to at 10% over 7 years?
                Calculate compound interest on P10,000 at 10% for 2 years compounded monthly
                How much should I invest today to reach P100,000 at 10% in 7 years?
                How long to double P10,000 at 10% compounded monthly?
                No extra deposits, fees or taxes are assumed.

                FINANCE EXPLANATIONS (always offline)
                explain compound interest
                what is yield to maturity
                explain bond duration
                explain convexity
                what is dividend yield
                explain portfolio diversification

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

                LANGUAGES (entirely offline)
                languages
                learn setswana
                setswana quiz
                learn french
                learn spanish
                learn portuguese
                learn german
                learn swahili
                learn japanese
                learn mandarin
                next language lesson
                language progress
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
                "Russian and multilingual practice, mathematics, and finance. " +
                "Type 'languages' for courses or 'help' for examples. " +
                "General open-ended questions are handled by the bundled offline language model."
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

            selectOfflineVoice()
        }
    }

    private fun selectedSpeechLanguage(): SpeechLanguage =
        SpeechLanguage.fromTag(
            getSharedPreferences("astra", Context.MODE_PRIVATE)
                .getString("speech_language", "en-US")
        )

    /**
     * Pick an installed non-network voice matching the user's language.
     * Never silently read Japanese or Setswana through an English voice.
     */
    private fun selectOfflineVoice() {
        val target = Locale.forLanguageTag(selectedSpeechLanguage().tag)
        offlineVoiceAvailable = false
        offlineVoiceName = ""
        if (!ttsReady) return
        try {
            val options = tts?.voices.orEmpty().filter { voice ->
                !voice.isNetworkConnectionRequired &&
                    voice.locale.language.equals(target.language, ignoreCase = true)
            }
            val picked = options.firstOrNull {
                it.locale.country.equals(target.country, ignoreCase = true)
            } ?: options.firstOrNull()
            if (picked != null && tts?.setVoice(picked) == TextToSpeech.SUCCESS) {
                offlineVoiceAvailable = true
                offlineVoiceName = picked.name
            }
        } catch (_: Exception) {
            offlineVoiceAvailable = false
            offlineVoiceName = ""
        }
    }

    private fun speak(text: String, offlineOnly: Boolean) {
        if (!ttsReady) return
        val selected = selectedSpeechLanguage()
        selectOfflineVoice()
        if (offlineOnly && !offlineVoiceAvailable) {
            Toast.makeText(
                this,
                "No offline " + selected.displayName + " voice installed. " +
                    "Install its Android text-to-speech data or select another language.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!offlineVoiceAvailable) {
            val locale = Locale.forLanguageTag(selected.tag)
            val status = tts?.setLanguage(locale)
            if (status == TextToSpeech.LANG_NOT_SUPPORTED ||
                status == TextToSpeech.LANG_MISSING_DATA
            ) {
                Toast.makeText(
                    this,
                    "No compatible " + selected.displayName +
                        " speech voice installed.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "astra")
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
        val workflowSession = AstraWorkflowSession()
        val vocabularyStore = AstraVocabularyStore(
            java.io.File(applicationContext.noBackupFilesDir, "languages/vocabulary")
        )

        val processor = AstraProcessor(
            RussianTutor(storage::getInt, storage::setInt, vocabularyStore),
            AstraMultilingualTutor(
                storage::getInt, storage::setInt,
                storage::getString, storage::setString,
                vocabularyStore
            ),
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
                var chatGeneration by remember { mutableIntStateOf(0) }
                // This trainable neural adapter is separate from immutable GGUF weights.
                val neuralLearner = remember {
                    AstraContinualLearning(
                        java.io.File(
                            applicationContext.noBackupFilesDir,
                            "neural/topic-adapter.bin"
                        )
                    )
                }
                val localBooks = remember {
                    AstraBookLibrary(
                        java.io.File(applicationContext.noBackupFilesDir, "knowledge/books")
                    )
                }
                var importedBookCount by remember {
                    mutableIntStateOf(localBooks.listBooks().size)
                }
                var useBooksInChat by remember {
                    mutableStateOf(storage.getBoolean("astra_use_books_in_chat"))
                }
                var bookImportRunning by remember { mutableStateOf(false) }
                var bookImportStatus by remember { mutableStateOf("") }
                var linkedLibraryFolder by remember {
                    mutableStateOf(AstraFolderAccess.linked(applicationContext))
                }
                var folderImportStatus by remember { mutableStateOf("") }
                var allFilesGranted by remember {
                    mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        Environment.isExternalStorageManager())
                }
                var confirmClearBooks by remember { mutableStateOf(false) }
                var selectedVocabularyLanguage by remember {
                    mutableStateOf("setswana")
                }
                var vocabularyLanguageMenuOpen by remember { mutableStateOf(false) }
                var vocabularyImportRunning by remember { mutableStateOf(false) }
                var vocabularyImportStatus by remember { mutableStateOf("") }
                var confirmClearVocabulary by remember { mutableStateOf(false) }
                var importedVocabularyCount by remember {
                    mutableIntStateOf(runCatching {
                        vocabularyStore.list("setswana").size
                    }.getOrDefault(0))
                }
                val onDeviceBrain = remember {
                    AstraOnDeviceBrain(applicationContext, neuralLearner, localBooks)
                }
                var neuralTrainingEnabled by remember {
                    mutableStateOf(storage.getBoolean("astra_neural_learning_opt_in"))
                }
                var neuralTrainingSteps by remember {
                    mutableIntStateOf(neuralLearner.status().steps)
                }
                var knowledgeSyncRunning by remember { mutableStateOf(false) }
                var knowledgeSyncStatus by remember { mutableStateOf("") }
                var offlineJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                var offlineAnswerMessageIndex by remember { mutableIntStateOf(-1) }
                // Grounded-memory prompts use an isolated local model context.
                var privateInferenceCounter by remember { mutableIntStateOf(0) }
                val offlineTestPrefs = remember {
                    applicationContext.getSharedPreferences("astra", Context.MODE_PRIVATE)
                }
                var offlineModelTestStatus by remember {
                    mutableStateOf(
                        if (offlineTestPrefs.getBoolean("astra_offline_test_incomplete", false))
                            "PREVIOUS OFFLINE TEST WAS INTERRUPTED. The Android process may " +
                                "have crashed or been stopped during native inference. " +
                                "Last stage: " +
                                offlineTestPrefs.getString("astra_offline_test_stage", "unknown") +
                                ". " + AstraProcessExitReason.latest(applicationContext) +
                                " Keep the imported model; collect a crash log before retrying."
                        else ""
                    ).let { selfTestWarning ->
                        listOfNotNull(
                            selfTestWarning.takeIf { it.isNotBlank() },
                            AstraInferenceCheckpoint.interruptedSummary(applicationContext)
                        ).joinToString("\n\n")
                    }
                }
                var offlineModelTestRunning by remember { mutableStateOf(false) }
                var languageBenchmarkRunning by remember { mutableStateOf(false) }
                var languageBenchmarkStatus by remember { mutableStateOf("") }
                var benchmarkHistory by remember {
                    mutableStateOf(storage.getString("astra_private_benchmark_trends", ""))
                }
                var showBenchmarkHistory by remember { mutableStateOf(false) }
                var languageBenchmarkJob by remember {
                    mutableStateOf<kotlinx.coroutines.Job?>(null)
                }
                var modelImportRunning by remember { mutableStateOf(false) }
                var modelImportStatus by remember { mutableStateOf("") }
                var confirmOneTimeModelDownload by remember { mutableStateOf(false) }
                var modelDownloadJob by remember {
                    mutableStateOf<kotlinx.coroutines.Job?>(null)
                }

                var tab by remember {
                    mutableIntStateOf(0)
                }

                var showFindInChat by remember { mutableStateOf(false) }
                var findInChatQuery by remember { mutableStateOf("") }
                var showMemorySearch by remember { mutableStateOf(false) }
                var memorySearchQuery by remember { mutableStateOf("") }

                var showClearDialog by remember {
                    mutableStateOf(false)
                }

                val archivedChats = remember {
                    mutableStateListOf<AstraChatArchive.Chat>().apply {
                        addAll(storage.loadArchivedChats())
                    }
                }
                var showChatSwitcher by remember { mutableStateOf(false) }
                var showArchiveFullDialog by remember { mutableStateOf(false) }
                var activeChatTitle by remember {
                    mutableStateOf(storage.getString("astra_active_chat_title", ""))
                }
                var chatSearchQuery by remember { mutableStateOf("") }
                var renameActiveChat by remember { mutableStateOf(false) }
                var renameArchivedChatId by remember { mutableStateOf<String?>(null) }
                var renameChatDraft by remember { mutableStateOf("") }
                var deleteChatId by remember { mutableStateOf<String?>(null) }
                var returnToChatLibrary by remember { mutableStateOf(false) }
                var navigationPopupOpen by remember { mutableStateOf(false) }
                var commandDeckExpanded by remember { mutableStateOf(false) }

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

                var aiReplyLanguage by remember {
                    mutableStateOf(AstraReplyLanguage.fromId(
                        storage.getString("astra_ai_reply_language", "auto")
                    ))
                }
                var aiReplyLanguageMenuOpen by remember { mutableStateOf(false) }

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

                // One shared list state for chat and search-result navigation.
                // User drags suspend auto-follow until they reach the end again.
                val scroll = rememberLazyListState()
                var followNewest by remember { mutableStateOf(true) }
                val userDraggingChat by scroll.interactionSource.collectIsDraggedAsState()

                fun currentArchiveRecord(): AstraChatArchive.Chat? {
                    if (history.none { it.role == "You" }) return null
                    val messages = history.map {
                        AstraChatArchive.Message(it.role, it.text)
                    }
                    return AstraChatArchive.Chat(
                        java.util.UUID.randomUUID().toString(),
                        activeChatTitle.ifBlank { AstraChatArchive.title(messages) },
                        messages
                    )
                }

                // Stop native generation before archiving or replacing the
                // active transcript, preserving the text already generated.
                fun interruptOfflineForTransition() {
                    if (offlineJob?.isActive == true) {
                        offlineJob?.cancel()
                        val messageIndex = offlineAnswerMessageIndex
                        if (messageIndex in history.indices &&
                            history[messageIndex].role == "Astra"
                        ) {
                            val existing = history[messageIndex].text
                            val updated = if (existing == "ASTRA // THINKING OFFLINE...")
                                "Offline response interrupted."
                            else existing + "\n\n[Response interrupted]"
                            history[messageIndex] = ChatMessage("Astra", updated)
                        }
                    }
                    offlineJob = null
                    offlineAnswerMessageIndex = -1
                }

                fun startNewConversation() {
                    interruptOfflineForTransition()
                    val nextArchive = AstraChatLibrary.archive(
                        archivedChats.toList(), currentArchiveRecord()
                    )
                    if (nextArchive == null) {
                        // Ask the user to manage storage; never evict history
                        // simply because they started another conversation.
                        showArchiveFullDialog = true
                        return
                    }
                    storage.saveArchivedChats(nextArchive)
                    archivedChats.clear()
                    archivedChats.addAll(nextArchive)
                    chatGeneration++
                    followNewest = true
                    activeChatTitle = ""
                    storage.setString("astra_active_chat_title", "")
                    history.clear()
                    history.add(ChatMessage("Astra", WELCOME))
                    dialogue.reset()
                    workflowSession.reset()
                    dialogueAwaiting = false
                    input = ""
                    tab = 0
                    storage.save(
                        history.toList(), notes.toList(), tasks.toList(), completed.toList()
                    )
                }

                fun switchToConversation(chat: AstraChatArchive.Chat) {
                    interruptOfflineForTransition()
                    val next = AstraChatLibrary.activate(
                        archivedChats.toList(), chat.id, currentArchiveRecord()
                    ) ?: return
                    // Persist the complete swap before replacing the current
                    // transcript. No unrelated saved thread is discarded.
                    storage.saveArchivedChats(next.archived)
                    archivedChats.clear()
                    archivedChats.addAll(next.archived)
                    chatGeneration++
                    followNewest = true
                    activeChatTitle = next.active.title
                    storage.setString("astra_active_chat_title", activeChatTitle)
                    chatSearchQuery = ""
                    history.clear()
                    history.addAll(next.active.messages.map {
                        ChatMessage(it.role, it.text)
                    })
                    if (history.isEmpty()) history.add(ChatMessage("Astra", WELCOME))
                    dialogue.reset()
                    workflowSession.reset()
                    dialogueAwaiting = false
                    input = ""
                    tab = 0
                    storage.save(
                        history.toList(), notes.toList(), tasks.toList(), completed.toList()
                    )
                }

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

                // All model output stays on-device and streams into the active bubble.
                fun enqueueOffline(
                    displayMessage: String,
                    prompt: String,
                    grounding: AstraGroundedMemory.Request? = null
                ) {
                    if (modelImportRunning) {
                        history.add(ChatMessage(
                            "Astra", "An offline model is being imported. Ask again after import."
                        ))
                        persist()
                        return
                    }
                    if (offlineModelTestRunning) {
                        history.add(ChatMessage(
                            "Astra",
                            "The offline AI self-test is running. Try again when it finishes."
                        ))
                        persist()
                        return
                    }
                    if (offlineJob?.isActive == true) {
                        history.add(ChatMessage(
                            "Astra", "A response is already being generated. Stop it first."
                        ))
                        persist()
                        return
                    }
                    // Only explicit grounded questions capture private memories.
                    // Ordinary Qwen3 questions continue to see chat turns only.
                    val groundedNotes = if (grounding != null) notes.toList() else emptyList()
                    val groundedTasks = if (grounding != null) tasks.toList() else emptyList()
                    val groundedCurrent = if (grounding != null) history.map {
                        AstraChatArchive.Message(it.role, it.text)
                    } else emptyList()
                    val groundedArchived =
                        if (grounding != null) archivedChats.toList() else emptyList()
                    // Search the retained chat, not just the latest 160 messages.
                    // AstraConversationContext still sends only eight short,
                    // bounded, same-chat excerpts to the reloaded native model.
                    val turns = AstraConversationHistory.forRecovery(history).mapNotNull { entry ->
                        val role = when (entry.role) {
                            "You" -> "user"
                            "Astra" -> "assistant"
                            else -> null
                        }
                        role?.let { AstraOnDeviceBrain.Turn(it, entry.text) }
                    } + AstraOnDeviceBrain.Turn("user", prompt)
                    val generation = chatGeneration
                    val placeholder = "ASTRA // THINKING OFFLINE..."
                    var displayed = placeholder
                    history.add(ChatMessage("You", displayMessage))
                    history.add(ChatMessage("Astra", placeholder))
                    while (history.size > MAX_HISTORY) history.removeAt(0)
                    val target = history.lastIndex
                    offlineAnswerMessageIndex = target
                    persist()
                    activeResearchCount++

                    val job = researchScope.launch {
                        val runningJob = coroutineContext[kotlinx.coroutines.Job]
                        fun stillActiveBubble(): Boolean =
                            chatGeneration == generation &&
                                target in history.indices &&
                                history[target].role == "Astra" &&
                                history[target].text == displayed

                        var groundedFooter = ""
                        try {
                            var effectiveTurns = turns
                            var effectiveGeneration = generation
                            if (grounding != null) {
                                if (!stillActiveBubble()) return@launch
                                val scanning = "Finding relevant private evidence on this phone..."
                                history[target] = ChatMessage("Astra", scanning)
                                displayed = scanning
                                val brief = withContext(Dispatchers.Default) {
                                    AstraGroundedMemory.retrieve(
                                        grounding, groundedNotes, groundedTasks,
                                        groundedCurrent, groundedArchived
                                    )
                                }
                                if (!stillActiveBubble()) return@launch
                                if (brief.evidence.isEmpty()) {
                                    history[target] = ChatMessage(
                                        "Astra",
                                        "I found no relevant user-written evidence in your " +
                                            "local notes, tasks or conversations. I won't " +
                                            "invent a personal memory; try a different topic."
                                    )
                                    persist()
                                    return@launch
                                }
                                groundedFooter = brief.sourceFooter()
                                if (!onDeviceBrain.hasLocalModel()) {
                                    history[target] = ChatMessage(
                                        "Astra",
                                        "I located this private evidence. For an AI analysis, " +
                                            "import Qwen3 once under Settings.\n\n" +
                                            groundedFooter
                                    )
                                    persist()
                                    return@launch
                                }
                                // Distinct negative engine context prevents old
                                // conversations contaminating grounded answers.
                                privateInferenceCounter--
                                effectiveGeneration = privateInferenceCounter
                                effectiveTurns = listOf(
                                    AstraOnDeviceBrain.Turn(
                                        "user", brief.modelPrompt()
                                    )
                                )
                            }
                            val response = try {
                                onDeviceBrain.reply(
                                    effectiveTurns, effectiveGeneration
                                ) { partial ->
                                    // Compose state and conversation history are
                                    // mutated on the UI thread, not the model thread.
                                    withContext(Dispatchers.Main.immediate) {
                                        if (stillActiveBubble()) {
                                            history[target] = ChatMessage("Astra", partial)
                                            displayed = partial
                                        }
                                    }
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Exception) {
                                "Offline model unavailable: " +
                                    (error.message ?: "The on-device inference engine failed.") +
                                    " Astra's built-in tools are still available."
                            }
                            if (stillActiveBubble()) {
                                val visibleResponse = if (grounding != null) {
                                    "ON-DEVICE AI INTERPRETATION · check against sources\n\n" +
                                        response + "\n\n" + groundedFooter
                                } else response
                                history[target] = ChatMessage("Astra", visibleResponse)
                                persist()
                                if (speakResponses && grounding == null &&
                                    !response.startsWith("Offline model unavailable:")) {
                                    speak(response, localOnlyTts)
                                }
                            }
                        } catch (cancel: CancellationException) {
                            // Preserve already streamed text; never leave a
                            // permanent 'thinking' placeholder in saved chat.
                            if (stillActiveBubble()) {
                                val stopped = if (displayed == placeholder)
                                    "Offline response stopped."
                                else displayed + "\n\n[Response stopped]" +
                                    if (groundedFooter.isNotBlank()) {
                                        "\n\n" + groundedFooter
                                    } else ""
                                history[target] = ChatMessage("Astra", stopped)
                                persist()
                            }
                            throw cancel
                        } finally {
                            // A cancelled old chat must not clear a newer job.
                            if (offlineJob === runningJob) {
                                offlineJob = null
                                offlineAnswerMessageIndex = -1
                            }
                            activeResearchCount = (activeResearchCount - 1).coerceAtLeast(0)
                        }
                    }
                    offlineJob = job
                    input = ""
                    keyboard?.hide()
                }

                fun submit(
                    raw: String
                ) {

                    val message = raw.trim()

                    // The benchmark uses the same native Qwen session. A real
                    // user conversation takes priority over test generations.
                    languageBenchmarkJob?.cancel()

                    if (message.isEmpty()) {
                        return
                    }

                    // A deliberate Send always returns focus to the newest reply.
                    followNewest = true

                    // Return the conversation to its spacious default after sending.
                    commandDeckExpanded = false
                    val normalized = AstraInput.normalize(message)

                    // Only a user-authored, explicitly prefixed plan can trigger
                    // local actions. Every step is simulated before the owner
                    // types 'confirm workflow'; model-generated text never runs it.
                    // Keep original newlines for multi-line plans.
                    val workflowSnapshot = AstraWorkflow.Snapshot(
                        tasks.toList(), completed.toList(), notes.toList(),
                        AstraWorkflow.decodeItems(
                            storage.getString("astra_shopping_items", "")
                        ),
                        AstraWorkflow.decodeItems(
                            storage.getString("astra_habits", "")
                        )
                    )
                    val workflow = workflowSession.handle(message, workflowSnapshot)
                        ?: if (message != normalized) {
                            workflowSession.handle(normalized, workflowSnapshot)
                        } else null
                    if (workflow != null) {
                        var reply = workflow.text
                        val next = workflow.apply
                        dialogue.dismissIncompletePrompt()
                        if (next != null) {
                            try {
                                // Persist first; only then replace UI collections.
                                storage.commitWorkflow(next)
                                tasks.clear()
                                tasks.addAll(next.tasks)
                                completed.clear()
                                completed.addAll(next.completed)
                                notes.clear()
                                notes.addAll(next.notes)
                                dialogue.reset()
                                workflowSession.reset()
                            } catch (failure: Exception) {
                                reply = "Workflow could not be saved. " +
                                    (failure.message ?: "No changes were applied.")
                            }
                        }
                        dialogueAwaiting = reply.startsWith("WORKFLOW PREVIEW")
                        history.add(ChatMessage("You", message))
                        history.add(ChatMessage("Astra", reply))
                        while (history.size > MAX_HISTORY) history.removeAt(0)
                        input = ""
                        persist()
                        keyboard?.hide()
                        return
                    }

                    // One-time special access is always owner-approved in Android
                    // Settings. Queries read only filenames from shared storage;
                    // never other apps' sandbox data, media bodies or contacts.
                    val sharedFileQuery = AstraSharedStorageSearch.parse(normalized)
                    if (sharedFileQuery != null) {
                        val authorized = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            Environment.isExternalStorageManager()
                        val placeholder = "Searching shared filenames on this phone..."
                        history.add(ChatMessage("You", message))
                        history.add(ChatMessage("Astra", if (authorized) placeholder else
                            "Shared file search is off. Enable optional All files access " +
                                "under Astra Settings, then try again."))
                        while (history.size > MAX_HISTORY) history.removeAt(0)
                        val target = history.lastIndex
                        input = ""
                        persist()
                        keyboard?.hide()
                        if (authorized) {
                            val originalGeneration = chatGeneration
                            researchScope.launch {
                                val reply = try {
                                    withContext(Dispatchers.IO) {
                                        AstraSharedStorageSearch.search(
                                            Environment.getExternalStorageDirectory(),
                                            sharedFileQuery
                                        ).render(sharedFileQuery)
                                    }
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    "Shared file search could not read the selected storage. " +
                                        "Verify the Android permission and try again."
                                }
                                if (chatGeneration == originalGeneration &&
                                    history.getOrNull(target)?.text == placeholder) {
                                    history[target] = ChatMessage("Astra", reply)
                                    persist()
                                }
                            }
                        }
                        return
                    }

                    AstraReplyLanguage.parseCommand(normalized)?.let { chosen ->
                        aiReplyLanguage = chosen
                        storage.setString("astra_ai_reply_language", chosen.id)
                        history.add(ChatMessage("You", message))
                        history.add(ChatMessage(
                            "Astra", AstraReplyLanguage.selectionMessage(chosen)
                        ))
                        while (history.size > MAX_HISTORY) history.removeAt(0)
                        input = ""
                        persist()
                        keyboard?.hide()
                        return
                    }

                    val neuralCommand = normalized.lowercase(Locale.ROOT).trim()
                    if (neuralCommand in setOf(
                            "learning status", "neural learning status",
                            "start neural learning", "stop neural learning",
                            "reset neural learning"
                        )
                    ) {
                        val reply = when (neuralCommand) {
                            "start neural learning" -> {
                                neuralTrainingEnabled = true
                                storage.setBoolean("astra_neural_learning_opt_in", true)
                                "On-device neural topic training enabled. It trains only " +
                                    "a small retrieval adapter, not Qwen3's GGUF weights."
                            }
                            "stop neural learning" -> {
                                neuralTrainingEnabled = false
                                storage.setBoolean("astra_neural_learning_opt_in", false)
                                "On-device neural topic training stopped. Your existing " +
                                    "local adapter weights are retained."
                            }
                            "reset neural learning" -> {
                                neuralTrainingEnabled = false
                                storage.setBoolean("astra_neural_learning_opt_in", false)
                                neuralLearner.reset()
                                neuralTrainingSteps = 0
                                "Training stopped and local neural adapter weights cleared."
                            }
                            else -> "Local neural topic training: " +
                                (if (neuralTrainingEnabled) "enabled" else "disabled") +
                                ". Updates: $neuralTrainingSteps. " +
                                "Qwen3 language-model weights are unchanged."
                        }
                        history.add(ChatMessage("You", message))
                        history.add(ChatMessage("Astra", reply))
                        while (history.size > MAX_HISTORY) history.removeAt(0)
                        input = ""
                        persist()
                        keyboard?.hide()
                        return
                    }

                    // Explicit opt-in. No transcript or exact message is written
                    // to the training file: only bounded numeric neural weights.
                    if (neuralTrainingEnabled && AstraLearningSignals.eligible(normalized)) {
                        val trainingExample = normalized.take(350)
                        researchScope.launch(Dispatchers.IO) {
                            if (storage.getBoolean("astra_neural_learning_opt_in")) {
                                runCatching { neuralLearner.learn(trainingExample) }
                                    .getOrNull()?.let { state ->
                                        withContext(Dispatchers.Main) {
                                            neuralTrainingSteps = state.steps
                                        }
                                    }
                            }
                        }
                    }

                    // Evidence-grounded analysis is opt-in and runs on the phone.
                    // It cannot take actions, browse, or silently access memory.
                    val groundedRequest = AstraGroundedMemory.parse(message)
                    if (groundedRequest != null) {
                        enqueueOffline(message, groundedRequest.question, groundedRequest)
                        return
                    }

                    // Explicit, source-grounded local recall. This search never
                    // sends notes, tasks or chat content to a language model.
                    val memoryRequest = AstraMemoryRecall.parse(message)
                    if (memoryRequest != null) {
                        // Capture a non-null sealed request before crossing the
                        // coroutine boundary; Kotlin cannot smart-cast a nullable
                        // closure capture inside the asynchronous when.
                        val selectedMemoryRequest: AstraMemoryRecall.Request = memoryRequest
                        val notesSnapshot = notes.toList()
                        val tasksSnapshot = tasks.toList()
                        val currentSnapshot = history.map {
                            AstraChatArchive.Message(it.role, it.text)
                        }
                        val archiveSnapshot = archivedChats.toList()
                        val generation = chatGeneration
                        val placeholder = "Searching private memory on this phone..."
                        history.add(ChatMessage("You", message))
                        history.add(ChatMessage("Astra", placeholder))
                        while (history.size > MAX_HISTORY) history.removeAt(0)
                        val target = history.lastIndex
                        persist()
                        input = ""
                        keyboard?.hide()
                        activeResearchCount += 1
                        researchScope.launch {
                            try {
                                val reply = withContext(Dispatchers.Default) {
                                    when (val request = selectedMemoryRequest) {
                                        is AstraMemoryRecall.Request.Overview ->
                                            AstraMemoryRecall.overview(
                                                notesSnapshot, tasksSnapshot,
                                                currentSnapshot, archiveSnapshot
                                            )
                                        is AstraMemoryRecall.Request.Search ->
                                            AstraMemoryRecall.find(
                                                request.term, notesSnapshot, tasksSnapshot,
                                                currentSnapshot, archiveSnapshot,
                                                notesOnly = request.notesOnly
                                            )
                                    }
                                }
                                if (chatGeneration == generation &&
                                    target in history.indices &&
                                    history[target].role == "Astra" &&
                                    history[target].text == placeholder) {
                                    history[target] = ChatMessage("Astra", reply)
                                    persist()
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Exception) {
                                if (chatGeneration == generation &&
                                    target in history.indices &&
                                    history[target].text == placeholder) {
                                    history[target] = ChatMessage(
                                        "Astra",
                                        "Private memory search was interrupted. Please try again."
                                    )
                                    persist()
                                }
                            } finally {
                                activeResearchCount = (activeResearchCount - 1).coerceAtLeast(0)
                            }
                        }
                        return
                    }
                    // Explicit model request, always fulfilled on this phone.
                    if (message.startsWith("ask ai ", ignoreCase = true)) {
                        val prompt = message.substring(7).trim()
                        if (prompt.isNotBlank()) {
                            enqueueOffline(message, prompt)
                        } else {
                            history.add(ChatMessage("Astra", "Type a question after 'ask ai'."))
                            persist()
                            input = ""
                        }
                        return
                    }
                    // Explicit questions about imported books must reach
                    // local retrieval before finance, tutor or other fixed
                    // command handlers can intercept their wording.
                    if (AstraBookQuery.extract(normalized) != null) {
                        enqueueOffline(message, normalized)
                        return
                    }
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
                        val researchGeneration = chatGeneration
                        persist()
                        input = ""
                        keyboard?.hide()

                        activeResearchCount += 1
                        researchScope.launch {
                            try {
                                val answer = research.search(researchTopic)
                                // Don't restore results after a conversation clear.
                                if (
                                    chatGeneration == researchGeneration &&
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

                    // Navigation commands are handled without writing a synthetic
                    // user message to a history that is about to be switched.
                    if (result.action == AssistantAction.NEW_CONVERSATION) {
                        startNewConversation()
                        keyboard?.hide()
                        return
                    }
                    if (result.action == AssistantAction.OPEN_CONVERSATIONS) {
                        showChatSwitcher = true
                        input = ""
                        keyboard?.hide()
                        return
                    }

                    // Native offline inference handles unsupported free-form requests.
                    // Actions and sensitive built-in outputs never enter the model path.
                    if (AstraOfflinePolicy.shouldUseModel(
                            assistantText = result.text,
                            sensitive = result.sensitive,
                            hasAction = result.action != AssistantAction.NONE
                        )
                    ) {
                        enqueueOffline(message, message)
                        return
                    }

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

                        AssistantAction.NEW_CONVERSATION,
                        AssistantAction.OPEN_CONVERSATIONS -> Unit

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

                val linkLibraryFolderLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocumentTree()
                    ) { selected ->
                        if (selected != null) {
                            try {
                                AstraFolderAccess.link(applicationContext, selected)
                                linkedLibraryFolder = true
                                folderImportStatus =
                                    "Folder connected. Astra remembers this read-only " +
                                    "authorization across phone restarts and signed updates."
                            } catch (failure: Exception) {
                                linkedLibraryFolder =
                                    AstraFolderAccess.linked(applicationContext)
                                folderImportStatus =
                                    "Could not retain folder access: " +
                                    (failure.message ?: "Android rejected access.")
                            }
                        }
                    }

                val allFilesPermissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        allFilesGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            Environment.isExternalStorageManager()
                    }

                val importBookLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null && !bookImportRunning) {
                            bookImportRunning = true
                            bookImportStatus = "Importing selected book on this phone..."
                            researchScope.launch {
                                try {
                                    val imported = withContext(Dispatchers.IO) {
                                        val title = contentResolver.query(
                                            uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                                            null, null, null
                                        )?.use { cursor ->
                                            if (cursor.moveToFirst())
                                                cursor.getString(0)
                                            else null
                                        } ?: uri.lastPathSegment ?: "unnamed.txt"
                                        val source = contentResolver.openInputStream(uri)
                                            ?: error("Could not open the selected book.")
                                        source.use { localBooks.importBook(title, it) }
                                    }
                                    importedBookCount = localBooks.listBooks().size
                                    bookImportStatus =
                                        "Indexed " + imported.chunks + " private passages from " +
                                            imported.title +
                                            (if (imported.truncated) " (partial index)." else ".")
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (error: Exception) {
                                    bookImportStatus = "Book import failed: " +
                                        (error.message ?: "unsupported file")
                                } finally {
                                    bookImportRunning = false
                                }
                            }
                        }
                    }

                val importVocabularyLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null && !vocabularyImportRunning) {
                            val language = selectedVocabularyLanguage
                            vocabularyImportRunning = true
                            vocabularyImportStatus =
                                "Importing your " + language + " vocabulary on this phone..."
                            researchScope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        val stream = contentResolver.openInputStream(uri)
                                            ?: error("Cannot read selected vocabulary document.")
                                        stream.use { vocabularyStore.importTsv(language, it) }
                                    }
                                    importedVocabularyCount =
                                        vocabularyStore.list(language).size
                                    vocabularyImportStatus =
                                        "Added " + result.added + " cards to " + language +
                                            "; " + result.alreadyPresent + " duplicates skipped. " +
                                            "Total user-added cards: " + result.totalCustomWords + "."
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (error: Exception) {
                                    vocabularyImportStatus =
                                        "Vocabulary import rejected; no changes: " +
                                            (error.message ?: "Invalid TSV file.")
                                } finally {
                                    vocabularyImportRunning = false
                                }
                            }
                        }
                    }


                val exportVocabularyBundleLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("text/tab-separated-values")
                    ) { uri ->
                        if (uri != null && !vocabularyImportRunning) {
                            vocabularyImportRunning = true
                            vocabularyImportStatus = "Exporting all locally imported language cards..."
                            researchScope.launch {
                                try {
                                    val cards = withContext(Dispatchers.IO) {
                                        val output = contentResolver.openOutputStream(uri)
                                            ?: error("Cannot write vocabulary backup.")
                                        output.use {
                                            AstraVocabularyBundle.export(vocabularyStore, it)
                                        }
                                    }
                                    vocabularyImportStatus =
                                        "Saved " + cards + " words across all nine languages. " +
                                        "Keep this TSV archive together with your JSON backup."
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (error: Exception) {
                                    vocabularyImportStatus = "Vocabulary backup failed: " +
                                        (error.message ?: "Cannot write archive.")
                                } finally {
                                    vocabularyImportRunning = false
                                }
                            }
                        }
                    }

                val restoreVocabularyBundleLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null && !vocabularyImportRunning) {
                            vocabularyImportRunning = true
                            vocabularyImportStatus = "Validating all language cards before restoring..."
                            researchScope.launch {
                                try {
                                    val restored = withContext(Dispatchers.IO) {
                                        val input = contentResolver.openInputStream(uri)
                                            ?: error("Cannot open vocabulary archive.")
                                        input.use {
                                            AstraVocabularyBundle.restore(vocabularyStore, it)
                                        }
                                    }
                                    importedVocabularyCount = withContext(Dispatchers.IO) {
                                        vocabularyStore.list(selectedVocabularyLanguage).size
                                    }
                                    vocabularyImportStatus =
                                        "Added " + restored.added + " vocabulary cards across " +
                                        restored.languages + " language decks; " +
                                        restored.skipped + " duplicates skipped. " +
                                        "Quiz progress and existing words were preserved."
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (error: Exception) {
                                    vocabularyImportStatus = "Vocabulary restore failed: " +
                                        (error.message ?: "Archive invalid.") +
                                        " If disk storage failed, retrying is safe; " +
                                        "existing words were not deleted."
                                } finally {
                                    vocabularyImportRunning = false
                                }
                            }
                        }
                    }

                if (confirmClearVocabulary) {
                    val language = selectedVocabularyLanguage
                    AlertDialog(
                        onDismissRequest = { confirmClearVocabulary = false },
                        title = { Text("Delete imported " + language + " words?") },
                        text = {
                            Text(
                                "Deletes only the extra vocabulary imported into Astra for " +
                                    language + ". Built-in lessons, the original documents " +
                                    "and other languages remain unchanged. " +
                                    "This cannot be undone in Astra."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmClearVocabulary = false
                                researchScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            vocabularyStore.clear(language)
                                        }
                                        if (selectedVocabularyLanguage == language) {
                                            importedVocabularyCount = 0
                                        }
                                        vocabularyImportStatus =
                                            "Removed imported " + language +
                                                " vocabulary. Built-in cards were kept."
                                    } catch (error: Exception) {
                                        vocabularyImportStatus =
                                            error.message ?: "Could not clear private vocabulary."
                                    }
                                }
                            }) { Text("Delete custom words") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                confirmClearVocabulary = false
                            }) { Text("Cancel") }
                        }
                    )
                }

                if (confirmClearBooks) {
                    AlertDialog(
                        onDismissRequest = { confirmClearBooks = false },
                        title = { Text("Remove imported books?") },
                        text = {
                            Text("Delete all locally imported book excerpts? This does not " +
                                "delete your original files in Downloads or other apps. " +
                                "This cannot be undone inside Astra.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmClearBooks = false
                                useBooksInChat = false
                                storage.setBoolean("astra_use_books_in_chat", false)
                                offlineJob?.cancel()
                                researchScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { localBooks.clear() }
                                        importedBookCount = 0
                                        bookImportStatus = "Private book library cleared."
                                    } catch (error: Exception) {
                                        bookImportStatus = "Could not delete all books: " +
                                            (error.message ?: "Unknown local storage error.")
                                    }
                                }
                            }) { Text("Delete imported books") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmClearBooks = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                val importModelLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { selected ->
                        if (selected != null && !modelImportRunning &&
                            offlineJob?.isActive != true && !offlineModelTestRunning) {
                            modelImportRunning = true
                            modelImportStatus = "Importing a locally saved model..."
                            researchScope.launch {
                                try {
                                    val bytes = AstraModelImport.importDocument(
                                        applicationContext, selected
                                    ) { copied ->
                                        withContext(Dispatchers.Main) {
                                            modelImportStatus = "Importing offline model: " +
                                                (copied / (1024 * 1024)) + " MiB..."
                                        }
                                    }
                                    modelImportStatus =
                                        "MODEL READY ON THIS PHONE · " +
                                            (bytes / (1024 * 1024)) + " MiB verified. " +
                                            "No network was used."
                                } catch (cancel: kotlinx.coroutines.CancellationException) {
                                    modelImportStatus = "Model import cancelled."
                                    throw cancel
                                } catch (error: Exception) {
                                    modelImportStatus =
                                        "MODEL IMPORT FAILED: " +
                                            (error.message ?: "Cannot read the model document.")
                                } finally {
                                    modelImportRunning = false
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

                // A drag is a clear signal that the reader wants to inspect
                // earlier text. Reaching the end re-enables automatic following.
                // Observe scroll layout in a flow rather than triggering a full
                // chat recomposition for every pixel of a user gesture.
                LaunchedEffect(scroll, userDraggingChat, chatGeneration) {
                    snapshotFlow { scroll.canScrollForward }.collect { canScrollForward ->
                        followNewest = AstraChatViewport.nextFollowState(
                            wasFollowing = followNewest,
                            userDragging = userDraggingChat,
                            canScrollForward = canScrollForward
                        )
                    }
                }

                LaunchedEffect(
                    history.size,
                    history.lastOrNull()?.text,
                    tab,
                    followNewest
                ) {
                    if (tab == 0 && followNewest && history.isNotEmpty()) {
                        // Avoid animating on every streamed token batch. Jump
                        // only when the user is following the latest answer.
                        scroll.scrollToItem(history.lastIndex)
                        // A long last bubble can exceed the viewport height.
                        // Scroll down to the bottom of that bubble as well.
                        val last = scroll.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == history.lastIndex }
                        if (last != null) {
                            val remaining = (last.offset + last.size -
                                scroll.layoutInfo.viewportEndOffset).coerceAtLeast(0)
                            if (remaining > 0) scroll.scrollBy(remaining.toFloat())
                        }
                    }
                }

                // Private cross-chat recall is user-initiated, never a
                // background upload or silent model-memory injection.
                if (showMemorySearch) {
                    AlertDialog(
                        onDismissRequest = { showMemorySearch = false },
                        title = { Text("Search your private memory") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Recall exact excerpts, or ask the on-device AI to " +
                                        "interpret matching evidence. Both run locally. " +
                                        "AI interpretations can be wrong, so original " +
                                        "source excerpts are always attached.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedTextField(
                                    value = memorySearchQuery,
                                    onValueChange = { memorySearchQuery = it.take(130) },
                                    label = { Text("Recall a topic") },
                                    placeholder = { Text("e.g. bond duration") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    enabled = memorySearchQuery.trim().length >= 2,
                                    onClick = {
                                        val query = memorySearchQuery.trim()
                                        showMemorySearch = false
                                        tab = 0
                                        submit("recall " + query)
                                    }
                                ) {
                                    Text("Find sources")
                                }
                                TextButton(
                                    enabled = memorySearchQuery.trim().length >= 3,
                                    onClick = {
                                        val query = memorySearchQuery.trim()
                                        showMemorySearch = false
                                        tab = 0
                                        submit("ask my memory " + query)
                                    }
                                ) {
                                    Text("Ask with evidence")
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showMemorySearch = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Hidden until requested: the chat retains its full-height layout.
                if (showFindInChat) {
                    val foundIndices = AstraMessageSearch.find(
                        history.map { it.text }, findInChatQuery
                    )
                    AlertDialog(
                        onDismissRequest = { showFindInChat = false },
                        title = { Text("Find in current chat") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = findInChatQuery,
                                    onValueChange = { findInChatQuery = it.take(150) },
                                    label = { Text("Search messages") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (findInChatQuery.isBlank()) {
                                    Text(
                                        "Search this conversation on your device.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Text(
                                        if (foundIndices.isEmpty()) "No matching messages"
                                        else foundIndices.size.toString() + " matching messages",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    if (foundIndices.isNotEmpty()) {
                                        LazyColumn(
                                            modifier = Modifier.heightIn(max = 360.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            itemsIndexed(foundIndices.take(40)) { _, messageIndex ->
                                                TextButton(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    onClick = {
                                                        showFindInChat = false
                                                        // Search jumps are intentional; don't snap
                                                        // back to the newest reply as tokens arrive.
                                                        followNewest = false
                                                        researchScope.launch {
                                                            scroll.animateScrollToItem(messageIndex)
                                                        }
                                                    }
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalAlignment = Alignment.Start
                                                    ) {
                                                        Text(
                                                            (messageIndex + 1).toString() +
                                                                " · " + history[messageIndex].role,
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                        Text(
                                                            AstraMessageSearch.excerpt(
                                                                history[messageIndex].text,
                                                                findInChatQuery
                                                            ),
                                                            maxLines = 3,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (foundIndices.size > 40) {
                                            Text(
                                                "Showing the first 40 matches. Refine your search to see more.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showFindInChat = false }) {
                                Text("Close")
                            }
                        }
                    )
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
                                    interruptOfflineForTransition()
                                    researchScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                storage.restoreBackup(restoreToConfirm)
                                            }
                                            archivedChats.clear()
                                            archivedChats.addAll(storage.loadArchivedChats())
                                            activeChatTitle = storage.getString(
                                                "astra_active_chat_title", ""
                                            )
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
                                            chatGeneration++
                                            dialogue.reset()
                    workflowSession.reset()
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

                if (showArchiveFullDialog) {
                    AlertDialog(
                        onDismissRequest = { showArchiveFullDialog = false },
                        title = { Text("Conversation archive is full") },
                        text = {
                            Text(
                                "You have ${AstraChatArchive.MAX_CHATS} saved conversations. " +
                                "Astra will not delete older chats automatically. " +
                                "Export a manual backup and delete an unwanted saved " +
                                "conversation to make room, or switch to an existing one."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showArchiveFullDialog = false
                                    chatSearchQuery = ""
                                    showChatSwitcher = true
                                }
                            ) { Text("Manage conversations") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showArchiveFullDialog = false }
                            ) { Text("Stay here") }
                        }
                    )
                }

                if (showChatSwitcher) {
                    AlertDialog(
                        onDismissRequest = { showChatSwitcher = false },
                        title = { Text("Conversations") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "CURRENT CHAT",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            activeChatTitle.ifBlank {
                                                history.firstOrNull {
                                                    it.role == "You"
                                                }?.text?.take(55) ?: "New conversation"
                                            },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            renameActiveChat = true
                                            renameChatDraft = activeChatTitle.ifBlank {
                                                history.firstOrNull {
                                                    it.role == "You"
                                                }?.text?.take(55) ?: "New conversation"
                                            }
                                            returnToChatLibrary = true
                                            showChatSwitcher = false
                                        }
                                    ) { Text("Rename") }
                                }
                                OutlinedButton(
                                    onClick = {
                                        showChatSwitcher = false
                                        chatSearchQuery = ""
                                        startNewConversation()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("＋ New conversation") }
                                OutlinedTextField(
                                    value = chatSearchQuery,
                                    onValueChange = { chatSearchQuery = it.take(100) },
                                    label = { Text("Search saved conversations") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val visibleChats = AstraChatLibrary.searchWithPreview(
                                    archivedChats.toList(),
                                    chatSearchQuery
                                )
                                Text(
                                    "${visibleChats.size} saved conversation(s)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Column(
                                    modifier = Modifier.heightIn(max = 300.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    if (visibleChats.isEmpty()) {
                                        Text(
                                            if (archivedChats.isEmpty()) {
                                                "No previous conversations yet."
                                            } else {
                                                "No saved conversations match your search."
                                            }
                                        )
                                    }
                                    visibleChats.forEach { hit ->
                                        val chat = hit.chat
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    showChatSwitcher = false
                                                    switchToConversation(chat)
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        chat.title.ifBlank {
                                                            "Conversation"
                                                        },
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        "${chat.messages.size} messages",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    if (hit.snippet != null) {
                                                        Text(
                                                            hit.snippet,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    renameArchivedChatId = chat.id
                                                    renameChatDraft = chat.title
                                                    returnToChatLibrary = true
                                                    showChatSwitcher = false
                                                },
                                                modifier = Modifier.semantics {
                                                    contentDescription =
                                                        "Rename ${chat.title}"
                                                }
                                            ) { Text("✎") }
                                            IconButton(
                                                onClick = {
                                                    deleteChatId = chat.id
                                                    showChatSwitcher = false
                                                },
                                                modifier = Modifier.semantics {
                                                    contentDescription =
                                                        "Delete ${chat.title}"
                                                }
                                            ) { Text("×") }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showChatSwitcher = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                if (renameActiveChat || renameArchivedChatId != null) {
                    AlertDialog(
                        onDismissRequest = {
                            renameActiveChat = false
                            renameArchivedChatId = null
                            if (returnToChatLibrary) showChatSwitcher = true
                            returnToChatLibrary = false
                        },
                        title = { Text("Rename conversation") },
                        text = {
                            OutlinedTextField(
                                value = renameChatDraft,
                                onValueChange = { renameChatDraft = it.take(100) },
                                label = { Text("Conversation title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = renameChatDraft.isNotBlank(),
                                onClick = {
                                    val title = AstraChatLibrary.title(renameChatDraft)
                                    if (title.isNotBlank()) {
                                        if (renameActiveChat) {
                                            activeChatTitle = title
                                            storage.setString(
                                                "astra_active_chat_title", title
                                            )
                                        } else {
                                            val id = renameArchivedChatId
                                            if (id != null) {
                                                val updated = AstraChatLibrary.rename(
                                                    archivedChats.toList(), id, title
                                                )
                                                storage.saveArchivedChats(updated)
                                                archivedChats.clear()
                                                archivedChats.addAll(updated)
                                            }
                                        }
                                    }
                                    renameActiveChat = false
                                    renameArchivedChatId = null
                                    if (returnToChatLibrary) showChatSwitcher = true
                                    returnToChatLibrary = false
                                }
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    renameActiveChat = false
                                    renameArchivedChatId = null
                                    if (returnToChatLibrary) showChatSwitcher = true
                                    returnToChatLibrary = false
                                }
                            ) { Text("Cancel") }
                        }
                    )
                }

                val deleteChat = archivedChats.firstOrNull {
                    it.id == deleteChatId
                }
                if (deleteChatId != null) {
                    AlertDialog(
                        onDismissRequest = {
                            deleteChatId = null
                            showChatSwitcher = true
                        },
                        title = { Text("Delete saved conversation?") },
                        text = {
                            Text(
                                "Delete \"${deleteChat?.title ?: "Conversation"}\" and " +
                                    "all its saved messages? This cannot be undone."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val id = deleteChatId
                                    if (id != null) {
                                        val updated = AstraChatLibrary.delete(
                                            archivedChats.toList(), id
                                        )
                                        storage.saveArchivedChats(updated)
                                        archivedChats.clear()
                                        archivedChats.addAll(updated)
                                    }
                                    deleteChatId = null
                                    showChatSwitcher = true
                                }
                            ) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    deleteChatId = null
                                    showChatSwitcher = true
                                }
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
                                    interruptOfflineForTransition()
                                    history.clear()

                                    history.add(
                                        ChatMessage(
                                            "Astra",
                                            WELCOME
                                        )
                                    )
                                    activeChatTitle = ""
                                    storage.setString("astra_active_chat_title", "")

                                    persist()

                                    chatGeneration++
                                    dialogue.reset()
                    workflowSession.reset()
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

                if (confirmOneTimeModelDownload) {
                    AlertDialog(
                        onDismissRequest = { confirmOneTimeModelDownload = false },
                        title = { Text("Download free offline AI model?") },
                        text = {
                            Text(
                                "Qwen3 1.7B requires a one-time download of about " +
                                    "1.28 GB. Use Wi-Fi to avoid mobile data charges. " +
                                    "The download is resumable, verified with SHA-256, " +
                                    "and stays on your phone. Afterwards, inference " +
                                    "is entirely offline; there is no subscription."
                            )
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                confirmOneTimeModelDownload = false
                            }) { Text("Cancel") }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmOneTimeModelDownload = false
                                if (!modelImportRunning &&
                                    offlineJob?.isActive != true &&
                                    !offlineModelTestRunning
                                ) {
                                    modelImportRunning = true
                                    modelImportStatus =
                                        "Downloading free offline model; progress is saved..."
                                    modelDownloadJob = researchScope.launch {
                                        try {
                                            val count = AstraModelDownload.download(
                                                applicationContext
                                            ) { received, expected ->
                                                withContext(Dispatchers.Main.immediate) {
                                                    modelImportStatus =
                                                        "Downloading Qwen3 1.7B: " +
                                                            (received / 1048576L) + " / " +
                                                            (expected / 1048576L) +
                                                            " MiB · resumable"
                                                }
                                            }
                                            modelImportStatus =
                                                "QWEN3 1.7B READY · " +
                                                    (count / 1048576L) +
                                                    " MiB verified on this phone. " +
                                                    "No paid API or server is needed."
                                        } catch (cancel: CancellationException) {
                                            modelImportStatus =
                                                "Download paused. Tap Get free model " +
                                                    "again to resume from saved bytes."
                                            throw cancel
                                        } catch (error: Exception) {
                                            modelImportStatus =
                                                "Download interrupted: " +
                                                    (error.message ?: "Check connection.") +
                                                    " Tap Get free model to retry."
                                        } finally {
                                            modelImportRunning = false
                                            modelDownloadJob = null
                                        }
                                    }
                                }
                            }) {
                                Text("Download once")
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
                                    horizontal = 14.dp,
                                    vertical = 4.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Box {
                                Box(
                                    modifier = Modifier
                                        .semantics { contentDescription = "Open Astra navigation and chat options" }
                                        .clickable { navigationPopupOpen = true }
                                ) {
                                    AstraBrand(listening = listening)
                                }
                                DropdownMenu(
                                    expanded = navigationPopupOpen,
                                    onDismissRequest = { navigationPopupOpen = false }
                                ) {
                                    listOf(
                                        "Chat", "Tasks", "Notes", "Tools", "Settings"
                                    ).forEachIndexed { index, title ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Text(
                                                        listOf("◌", "✓", "▤", "⌘", "⚙")[index],
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(title, style = MaterialTheme.typography.bodyMedium)
                                                    if (tab == index) Text("✓")
                                                }
                                            },
                                            onClick = {
                                                tab = index
                                                navigationPopupOpen = false
                                            }
                                        )
                                    }
                                    if (tab == 0) {
                                        HorizontalDivider()
                                        Text(
                                            "CHAT OPTIONS",
                                            modifier = Modifier.padding(
                                                start = 16.dp, top = 8.dp, bottom = 4.dp
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )


                                        DropdownMenuItem(
                                            text = { Text("Find in chat") },
                                            onClick = {
                                                navigationPopupOpen = false
                                                findInChatQuery = ""
                                                showFindInChat = true
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Search all local memory") },
                                            onClick = {
                                                navigationPopupOpen = false
                                                memorySearchQuery = ""
                                                showMemorySearch = true
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Text("Copy chat")
                                            },
                                            onClick = {

                                                navigationPopupOpen = false

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

                                                navigationPopupOpen = false

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

                                                navigationPopupOpen = false

                                                startNewConversation()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Conversations") },
                                            onClick = {
                                                navigationPopupOpen = false
                                                chatSearchQuery = ""
                                                showChatSwitcher = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename current chat") },
                                            onClick = {
                                                navigationPopupOpen = false
                                                renameActiveChat = true
                                                renameChatDraft = activeChatTitle.ifBlank {
                                                    history.firstOrNull {
                                                        it.role == "You"
                                                    }?.text?.take(55) ?: "New conversation"
                                                }
                                                returnToChatLibrary = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (commandDeckExpanded) "Hide mission control" else "Show mission control") },
                                            onClick = {
                                                commandDeckExpanded = !commandDeckExpanded
                                                navigationPopupOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Read last response aloud") },
                                            enabled = history.any { it.role == "Astra" },
                                            onClick = {
                                                navigationPopupOpen = false
                                                val reply = history.lastOrNull { it.role == "Astra" }?.text.orEmpty()
                                                if (reply.isNotBlank()) speak(reply, localOnlyTts)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Clear current chat") },
                                            onClick = {
                                                navigationPopupOpen = false
                                                showClearDialog = true
                                            }
                                        )
                                    }

                                }
                            }

                            // Visible build identification makes old APKs unmistakable.
                            // This is a label, never a second navigation control.
                            Text(
                                text = "v" + ASTRA_VERSION + "  ·  OFFLINE AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )

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

                                // Dashboard is on demand to maximize chat space.
                                AnimatedVisibility(
                                    visible = commandDeckExpanded && offlineJob?.isActive != true,
                                    enter = expandVertically(animationSpec = tween(240)) +
                                        androidx.compose.animation.fadeIn(animationSpec = tween(180)),
                                    exit = shrinkVertically(animationSpec = tween(190)) +
                                        androidx.compose.animation.fadeOut(animationSpec = tween(130))
                                ) {
                                    AstraCommandDeck(
                                        listening = listening,
                                        researching = activeResearchCount > 0,
                                        openTasks = tasks.size,
                                        completedTasks = completed.size,
                                        storedNotes = notes.size,
                                        awaitingReply = dialogueAwaiting,
                                        expanded = true,
                                        onExpandedChange = { commandDeckExpanded = it },
                                        onCommand = { command -> submit(command) }
                                    )
    
                                }

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

                                // This appears only after the reader scrolls
                                // away; it does not consume permanent chat space.
                                if (AstraChatViewport.showReturnToLatest(
                                        followNewest, scroll.canScrollForward
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        TextButton(
                                            onClick = { followNewest = true },
                                            modifier = Modifier.semantics {
                                                contentDescription = "Jump to latest message"
                                            }
                                        ) {
                                            Text("↓  Latest messages")
                                        }
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

                                // A failed or stopped local generation can be retried in
                                // place. The original user text is preserved and routed
                                // through submit again (including grounded-memory prompts).
                                val retryQuestion = AstraResponseRetry.eligibleQuestion(
                                    history.takeLast(2).map {
                                        AstraResponseRetry.Message(it.role, it.text)
                                    },
                                    offlineJob?.isActive == true || modelImportRunning ||
                                        offlineModelTestRunning
                                )
                                if (retryQuestion != null) {
                                    TextButton(
                                        onClick = {
                                            val draft = input
                                            // Remove only the exact tail validated above.
                                            val stillEligible = AstraResponseRetry.eligibleQuestion(
                                                history.takeLast(2).map {
                                                    AstraResponseRetry.Message(it.role, it.text)
                                                },
                                                offlineJob?.isActive == true || modelImportRunning ||
                                                    offlineModelTestRunning
                                            )
                                            if (stillEligible == retryQuestion &&
                                                history.size >= 2) {
                                                history.removeAt(history.lastIndex)
                                                history.removeAt(history.lastIndex)
                                                persist()
                                                submit(retryQuestion)
                                                input = draft
                                            }
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = "Retry last interrupted offline answer"
                                        }
                                    ) {
                                        Text("↻ Retry last response")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { startVoice() },
                                        modifier = Modifier.semantics {
                                            contentDescription = if (listening) "Stop voice dictation"
                                                else "Start voice dictation"
                                        }
                                    ) {
                                        Text(if (listening) "■" else "🎙")
                                    }
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = {
                                            input = it
                                            if (it.isNotBlank()) commandDeckExpanded = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("Message Astra...") },
                                        maxLines = 4,
                                        shape = RoundedCornerShape(24.dp),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = {
                                            if (offlineJob?.isActive != true) submit(input)
                                        })
                                    )
                                    if (offlineJob?.isActive == true) {
                                        FilledTonalIconButton(
                                            onClick = { offlineJob?.cancel() },
                                            modifier = Modifier.semantics {
                                                contentDescription = "Stop offline response"
                                            }
                                        ) {
                                            Text("■")
                                        }
                                    } else {
                                        FilledIconButton(
                                            onClick = { submit(input) },
                                            enabled = input.isNotBlank(),
                                            modifier = Modifier.semantics {
                                                contentDescription = "Send message"
                                            }
                                        ) {
                                            Text("➤")
                                        }
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
                                            "Offline AI models",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Astra supports quantized Qwen3 models and runs " +
                                            "its inference directly on this Android phone. " +
                                            "No computer, gateway, subscription, or internet " +
                                            "connection is needed for AI conversation. " +
                                            "Built-in tasks, notes and calculators run locally first."
                                        )
                                        val modelIncluded = onDeviceBrain.isModelBundled()
                                        val advancedInstalled =
                                            onDeviceBrain.hasAdvancedModel()
                                        val modelImported = AstraModelImport.appearsInstalled(
                                            applicationContext
                                        )
                                        Text(
                                            when {
                                                advancedInstalled ->
                                                    "QWEN3 1.7B AVAILABLE · PREFERRED MODEL"
                                                modelIncluded || modelImported ->
                                                    "QWEN3 0.6B AVAILABLE · COMPACT FALLBACK"
                                                else -> "NO LOCAL MODEL · IMPORT THE FREE 1.7B MODEL"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (modelIncluded || modelImported || advancedInstalled)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            "For data-saving updates, import Qwen3 GGUF once " +
                                                "from Downloads, or select your previous large " +
                                                "Astra APK / GitHub ZIP. The AI model is checked " +
                                                "locally and kept across correctly signed app updates. " +
                                                "Nothing is downloaded by the import process.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Astra automatically uses Qwen3 1.7B when available. " +
                                                "The 0.6B model is a recovery fallback, not a " +
                                                "performance limit. Neither model needs paid APIs " +
                                                "or subscriptions. Download the free 1.7B GGUF " +
                                                "once, then import it here; future signed updates " +
                                                "preserve the model.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedButton(
                                            enabled = !modelImportRunning &&
                                                offlineJob?.isActive != true &&
                                                !offlineModelTestRunning,
                                            onClick = { confirmOneTimeModelDownload = true }
                                        ) {
                                            Text("Get free Qwen3 1.7B model")
                                        }
                                        if (modelDownloadJob?.isActive == true) {
                                            TextButton(
                                                onClick = { modelDownloadJob?.cancel() }
                                            ) {
                                                Text("Pause model download")
                                            }
                                        }
                                        OutlinedButton(
                                            enabled = !modelImportRunning &&
                                                offlineJob?.isActive != true &&
                                                !offlineModelTestRunning,
                                            onClick = {
                                                importModelLauncher.launch(arrayOf("*/*"))
                                            }
                                        ) {
                                            Text(
                                                if (modelImportRunning) "Importing offline model..."
                                                else "Import offline model once"
                                            )
                                        }
                                        if (modelImportStatus.isNotBlank()) {
                                            Text(
                                                modelImportStatus,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            "The full APK includes model weights. With the smaller APK, " +
                                            "you can import a GGUF or previous large APK/ZIP " +
                                            "without downloading the model again. " +
                                            "Keep at least 1 GB of free storage for installation " +
                                            "and extraction. Responses may be slow on older phones. " +
                                            "Astra does not transmit these messages to a server.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                enabled = !offlineModelTestRunning &&
                                                    !languageBenchmarkRunning &&
                                                    !modelImportRunning &&
                                                    offlineJob?.isActive != true &&
                                                    onDeviceBrain.hasLocalModel(),
                                                onClick = {
                                                    offlineModelTestRunning = true
                                                    offlineModelTestStatus = "LOADING AND TESTING LOCAL MODEL..."
                                                    // Commit synchronously before JNI starts: a fatal native
                                                    // signal terminates the process without reaching catch/finally.
                                                    offlineTestPrefs.edit()
                                                        .putBoolean("astra_offline_test_incomplete", true)
                                                        .putString("astra_offline_test_stage", "starting")
                                                        .commit()
                                                    val began = System.nanoTime()
                                                    researchScope.launch {
                                                        var recordedGeneration = false
                                                        try {
                                                            val answer = onDeviceBrain.reply(
                                                                listOf(
                                                                    AstraOnDeviceBrain.Turn(
                                                                        "user",
                                                                        AstraOfflineSelfTest.CHALLENGE
                                                                    )
                                                                ),
                                                                Int.MIN_VALUE,
                                                                diagnosticMaxTokens =
                                                                    AstraGenerationPolicy.DIAGNOSTIC_TOKEN_LIMIT,
                                                                onPartial = { progress ->
                                                                    // Store only generic model stage, never
                                                                    // private model output or chat prompts.
                                                                    if (progress.startsWith("Verifying the local offline model") ||
                                                                        progress.startsWith("Loading the language model")) {
                                                                        offlineTestPrefs.edit()
                                                                            .putString("astra_offline_test_stage",
                                                                                if (progress.startsWith("Loading"))
                                                                                    "native model loading"
                                                                                else "model verification")
                                                                            .commit()
                                                                    } else if (!recordedGeneration &&
                                                                        progress.isNotBlank()) {
                                                                        // Do not persist the generated content.
                                                                        recordedGeneration = true
                                                                        offlineTestPrefs.edit()
                                                                            .putString("astra_offline_test_stage",
                                                                                "token generation")
                                                                            .commit()
                                                                    }
                                                                }
                                                            )
                                                            val elapsed =
                                                                (System.nanoTime() - began) / 1_000_000
                                                            val testResult = AstraOfflineSelfTest.evaluate(
                                                                answer, elapsed
                                                            )
                                                            val actualModel = onDeviceBrain.loadedModelLabel()
                                                                ?: "unknown local model"
                                                            offlineModelTestStatus =
                                                                testResult.message +
                                                                    "\nModel used: " + actualModel +
                                                                    "\nNo prompts were sent to an AI server."
                                                        } catch (error: Exception) {
                                                            offlineModelTestStatus =
                                                                "OFFLINE TEST FAILED: " +
                                                                    (error.message ?: "Unknown inference error")
                                                        } finally {
                                                            // A Kotlin exception is recoverable. Only a
                                                            // process-level crash leaves the checkpoint set.
                                                            offlineTestPrefs.edit()
                                                                .putBoolean("astra_offline_test_incomplete", false)
                                                                .remove("astra_offline_test_stage")
                                                                .commit()
                                                            offlineModelTestRunning = false
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text("Test on-device AI")
                                            }
                                            if (offlineModelTestRunning) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                        Text(
                                            "To verify true offline generation, turn on airplane " +
                                                "mode before tapping Test on-device AI. This test " +
                                                "never contacts the internet or another computer.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (offlineModelTestStatus.isNotBlank()) {
                                            Text(
                                                offlineModelTestStatus,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        HorizontalDivider()
                                        Text(
                                            "Conversation quality checks",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "Runs ten checks through the actual on-device language " +
                                                "model: arithmetic, Russian, Setswana, French, " +
                                                "German and Spanish translations, two-turn " +
                                                "recall, Russian grammar, compound interest " +
                                                "and logical deduction. " +
                                                "Run with airplane mode on to verify offline behavior. " +
                                                "This is a small spot check, not proof of unrestricted " +
                                                "understanding or continual language-model training.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                enabled = !languageBenchmarkRunning &&
                                                    !offlineModelTestRunning &&
                                                    !modelImportRunning &&
                                                    offlineJob?.isActive != true &&
                                                    onDeviceBrain.hasLocalModel(),
                                                onClick = {
                                                    languageBenchmarkRunning = true
                                                    languageBenchmarkStatus =
                                                        "Running ten local conversation checks..."
                                                    languageBenchmarkJob = researchScope.launch {
                                                        val outcomes = mutableListOf<
                                                            AstraLanguageBenchmark.Outcome
                                                        >()
                                                        try {
                                                            suspend fun sample(
                                                                probe: AstraLanguageBenchmark.Probe,
                                                                prompt: String,
                                                                generation: Int
                                                            ) {
                                                                languageBenchmarkStatus =
                                                                    "Running " + probe.title + "..."
                                                                val started = System.nanoTime()
                                                                val result = onDeviceBrain.reply(
                                                                    listOf(
                                                                        AstraOnDeviceBrain.Turn(
                                                                            "user", prompt
                                                                        )
                                                                    ),
                                                                    generation,
                                                                    diagnosticMaxTokens =
                                                                        AstraGenerationPolicy.DIAGNOSTIC_TOKEN_LIMIT
                                                                )
                                                                val elapsed =
                                                                    (System.nanoTime() -
                                                                        started) / 1_000_000
                                                                outcomes.add(
                                                                    AstraLanguageBenchmark.outcome(
                                                                        probe, result, elapsed
                                                                    )
                                                                )
                                                            }

                                                            sample(
                                                                AstraLanguageBenchmark.Probe.ARITHMETIC,
                                                                AstraLanguageBenchmark.MATH_PROMPT,
                                                                Int.MIN_VALUE + 210
                                                            )
                                                            sample(
                                                                AstraLanguageBenchmark.Probe.RUSSIAN,
                                                                AstraLanguageBenchmark.RUSSIAN_PROMPT,
                                                                Int.MIN_VALUE + 211
                                                            )

                                                            languageBenchmarkStatus =
                                                                "Running two-turn context check..."
                                                            val beforeMemory = System.nanoTime()
                                                            val memoryGeneration = Int.MIN_VALUE + 212
                                                            val setup = onDeviceBrain.reply(
                                                                listOf(
                                                                    AstraOnDeviceBrain.Turn(
                                                                        "user",
                                                                        AstraLanguageBenchmark.MEMORY_SETUP
                                                                    )
                                                                ),
                                                                memoryGeneration,
                                                                diagnosticMaxTokens =
                                                                    AstraGenerationPolicy.DIAGNOSTIC_TOKEN_LIMIT
                                                            )
                                                            val remembered = onDeviceBrain.reply(
                                                                listOf(
                                                                    AstraOnDeviceBrain.Turn(
                                                                        "user",
                                                                        AstraLanguageBenchmark.MEMORY_SETUP
                                                                    ),
                                                                    AstraOnDeviceBrain.Turn(
                                                                        "assistant", setup
                                                                    ),
                                                                    AstraOnDeviceBrain.Turn(
                                                                        "user",
                                                                        AstraLanguageBenchmark.MEMORY_QUESTION
                                                                    )
                                                                ),
                                                                memoryGeneration,
                                                                diagnosticMaxTokens =
                                                                    AstraGenerationPolicy.DIAGNOSTIC_TOKEN_LIMIT
                                                            )
                                                            outcomes.add(
                                                                AstraLanguageBenchmark.outcome(
                                                                    AstraLanguageBenchmark.Probe.CONTEXT,
                                                                    remembered,
                                                                    (System.nanoTime() -
                                                                        beforeMemory) / 1_000_000
                                                                )
                                                            )

                                                            sample(
                                                                AstraLanguageBenchmark.Probe.GRAMMAR,
                                                                AstraLanguageBenchmark.GRAMMAR_PROMPT,
                                                                Int.MIN_VALUE + 213
                                                            )
                                                            sample(
                                                                AstraLanguageBenchmark.Probe.COMPOUND_INTEREST,
                                                                AstraLanguageBenchmark.COMPOUND_INTEREST_PROMPT,
                                                                Int.MIN_VALUE + 214
                                                            )
                                                            sample(
                                                                AstraLanguageBenchmark.Probe.DEDUCTION,
                                                                AstraLanguageBenchmark.DEDUCTION_PROMPT,
                                                                Int.MIN_VALUE + 215
                                                            )
                                                            sample(
                                                                AstraLanguageBenchmark.Probe.SETSWANA,
                                                                AstraLanguageBenchmark.SETSWANA_PROMPT,
                                                                Int.MIN_VALUE + 216
                                                            )
                                                            sample(
                                                                AstraLanguageBenchmark.Probe.FRENCH,
                                                                AstraLanguageBenchmark.FRENCH_PROMPT,
                                                                Int.MIN_VALUE + 217
                                                            )
                                                            sample(
                                                                AstraLanguageBenchmark.Probe.GERMAN,
                                                                AstraLanguageBenchmark.GERMAN_PROMPT,
                                                                Int.MIN_VALUE + 218
                                                            )
                                                            sample(
                                                                AstraLanguageBenchmark.Probe.SPANISH,
                                                                AstraLanguageBenchmark.SPANISH_PROMPT,
                                                                Int.MIN_VALUE + 219
                                                            )
                                                            languageBenchmarkStatus =
                                                                AstraLanguageBenchmark.render(
                                                                    outcomes,
                                                                    onDeviceBrain.loadedModelLabel()
                                                                )
                                                            // Persist only anonymous numeric outcomes.
                                                            // A failed/cancelled run never creates a record.
                                                            runCatching {
                                                                val updated = AstraBenchmarkTrends.append(
                                                                    benchmarkHistory, outcomes,
                                                                    onDeviceBrain.loadedModelLabel(),
                                                                    System.currentTimeMillis()
                                                                )
                                                                storage.setString(
                                                                    "astra_private_benchmark_trends", updated
                                                                )
                                                                benchmarkHistory = updated
                                                            }
                                                        } catch (cancel: CancellationException) {
                                                            languageBenchmarkStatus =
                                                                "Conversation checks cancelled. " +
                                                                    "Partial results are not scored."
                                                            throw cancel
                                                        } catch (error: Exception) {
                                                            languageBenchmarkStatus =
                                                                "Conversation checks interrupted: " +
                                                                    (error.message ?: "Local inference error")
                                                        } finally {
                                                            languageBenchmarkRunning = false
                                                            languageBenchmarkJob = null
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text("Run language checks")
                                            }
                                            if (languageBenchmarkRunning) {
                                                TextButton(
                                                    onClick = {
                                                        languageBenchmarkJob?.cancel()
                                                    }
                                                ) { Text("Stop checks") }
                                            }
                                        }
                                        if (languageBenchmarkStatus.isNotBlank()) {
                                            Text(
                                                languageBenchmarkStatus,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            if (!languageBenchmarkRunning) {
                                                TextButton(
                                                    onClick = {
                                                        copyText(languageBenchmarkStatus)
                                                    }
                                                ) { Text("Copy check results") }
                                            }
                                        }
                                        TextButton(
                                            enabled = !languageBenchmarkRunning,
                                            onClick = {
                                                showBenchmarkHistory = !showBenchmarkHistory
                                            }
                                        ) {
                                            Text(if (showBenchmarkHistory) "Hide past phone checks"
                                                else "View past phone checks")
                                        }
                                        if (showBenchmarkHistory) {
                                            Text(
                                                AstraBenchmarkTrends.render(benchmarkHistory),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Text(
                                            "Device diagnostics",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "Create a local report containing Android version, " +
                                                "device model, available RAM/storage, model files " +
                                                "and the result of the offline self-test. " +
                                                "No conversations, notes, passwords or device IDs " +
                                                "are included. Review the report before sharing.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val report = AstraDeviceDiagnostics.render(
                                                        AstraDeviceDiagnostics.capture(
                                                            applicationContext,
                                                            ASTRA_VERSION,
                                                            offlineModelTestStatus,
                                                            onDeviceBrain.loadedModelLabel(),
                                                            languageBenchmarkStatus
                                                        )
                                                    )
                                                    copyText(report)
                                                }
                                            ) {
                                                Text("Copy diagnostics")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val report = AstraDeviceDiagnostics.render(
                                                        AstraDeviceDiagnostics.capture(
                                                            applicationContext,
                                                            ASTRA_VERSION,
                                                            offlineModelTestStatus,
                                                            onDeviceBrain.loadedModelLabel(),
                                                            languageBenchmarkStatus
                                                        )
                                                    )
                                                    shareText(report)
                                                }
                                            ) {
                                                Text("Share report")
                                            }
                                        }
                                        HorizontalDivider()
                                    }

                                    item {
                                        HorizontalDivider()
                                        Text(
                                            "Expand offline language lessons",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Astra already includes 191 offline lessons and " +
                                                "768 vocabulary cards across nine languages. " +
                                                "Add up to 10,000 privately imported words for " +
                                                "each language; new four-word lessons are " +
                                                "generated automatically. No AI model download " +
                                                "or internet connection is required.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Box {
                                            OutlinedButton(
                                                enabled = !vocabularyImportRunning,
                                                onClick = {
                                                    vocabularyLanguageMenuOpen = true
                                                }
                                            ) {
                                                Text(
                                                    "Vocabulary: " +
                                                        selectedVocabularyLanguage.replaceFirstChar {
                                                            it.uppercase()
                                                        }
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = vocabularyLanguageMenuOpen,
                                                onDismissRequest = {
                                                    vocabularyLanguageMenuOpen = false
                                                }
                                            ) {
                                                AstraVocabularyStore.supportedLanguages.sorted()
                                                    .forEach { language ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(language.replaceFirstChar {
                                                                    it.uppercase()
                                                                })
                                                            },
                                                            onClick = {
                                                                selectedVocabularyLanguage = language
                                                                importedVocabularyCount =
                                                                    runCatching {
                                                                        vocabularyStore.list(language).size
                                                                    }.getOrDefault(0)
                                                                vocabularyLanguageMenuOpen = false
                                                            }
                                                        )
                                                    }
                                            }
                                        }
                                        Text(
                                            "Imported words in this language: " +
                                                importedVocabularyCount + " / " +
                                                AstraVocabularyStore.MAX_CUSTOM_WORDS_PER_LANGUAGE,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Choose a local UTF-8 .tsv or .txt file. " +
                                                "Each line must contain two columns separated " +
                                                "by a TAB: foreign phrase, then English meaning. " +
                                                "An optional 'phrase TAB translation' header " +
                                                "is accepted. Imported terms stay on this phone. " +
                                                "The ordinary JSON backup saves progress, " +
                                                "but a separate TSV bundle below preserves " +
                                                "your entire imported vocabulary across upgrades.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedButton(
                                            enabled = !vocabularyImportRunning,
                                            onClick = {
                                                importVocabularyLauncher.launch(arrayOf("*/*"))
                                            }
                                        ) {
                                            Text(
                                                if (vocabularyImportRunning)
                                                    "Importing vocabulary..."
                                                else "Import extra vocabulary (TSV)"
                                            )
                                        }
                                        OutlinedButton(
                                            enabled = !vocabularyImportRunning,
                                            onClick = {
                                                exportVocabularyBundleLauncher.launch(
                                                    "Astra-all-language-vocabulary.tsv"
                                                )
                                            }
                                        ) { Text("Export all language vocabulary (TSV)") }
                                        OutlinedButton(
                                            enabled = !vocabularyImportRunning,
                                            onClick = {
                                                restoreVocabularyBundleLauncher.launch(
                                                    arrayOf("text/tab-separated-values", "text/plain", "*/*")
                                                )
                                            }
                                        ) { Text("Restore all language vocabulary (TSV)") }
                                        if (vocabularyImportStatus.isNotBlank()) {
                                            Text(
                                                vocabularyImportStatus,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        if (importedVocabularyCount > 0) {
                                            TextButton(
                                                enabled = !vocabularyImportRunning,
                                                onClick = { confirmClearVocabulary = true }
                                            ) {
                                                Text("Remove imported words for this language")
                                            }
                                        }
                                    }

                                    item {
                                        HorizontalDivider()
                                        Text(
                                            "Shared phone files (optional)",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Grant once through Android Settings to search " +
                                                "shared filenames under phone storage without " +
                                                "selecting each file. Astra only reads file " +
                                                "names when you type 'search phone report'. " +
                                                "Android grants broad read/write permission, " +
                                                "but Astra's filename search only reads metadata. " +
                                                "No background scanning, copying or uploads. " +
                                                "Other apps' private data is inaccessible. " +
                                                "Revoke the grant in Android Settings.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            if (allFilesGranted) "Access: granted" else "Access: off",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedButton(onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                    Uri.parse("package:" + packageName)
                                                )
                                                try {
                                                    allFilesPermissionLauncher.launch(intent)
                                                } catch (_: Exception) {
                                                    allFilesPermissionLauncher.launch(Intent(
                                                        Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                                    ))
                                                }
                                            }
                                        }) {
                                            Text(if (allFilesGranted) "Review / revoke file access"
                                                else "Grant one-time shared-file access")
                                        }
                                    }

                                    item {
                                        HorizontalDivider()
                                        Text(
                                            "Your private book library",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Import a downloaded UTF-8 TXT, Markdown or " +
                                                "unencrypted EPUB book. Astra indexes it entirely " +
                                                "on this phone and never uploads its contents. " +
                                                "The book is reference context for the offline AI, " +
                                                "not training data for Qwen3's model weights.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Books: $importedBookCount. " +
                                                "Ask 'ask book Russian grammar' to retrieve from them.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Use imported books in normal AI chat",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Switch(
                                                checked = useBooksInChat,
                                                enabled = importedBookCount > 0,
                                                onCheckedChange = { value ->
                                                    // Stop an active answer before retracting
                                                    // permission to include private books.
                                                    if (!value) offlineJob?.cancel()
                                                    useBooksInChat = value
                                                    storage.setBoolean(
                                                        "astra_use_books_in_chat", value
                                                    )
                                                }
                                            )
                                        }
                                        Text(
                                            "Automatic book context is off by default. " +
                                                "Explicit 'ask book ...' works without this switch. " +
                                                "Only short matching passages are provided to the " +
                                                "local model; no remote search or paid service is used.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Optional: authorize one Astra Library folder once. " +
                                                "Astra remembers read-only access to that folder, " +
                                                "not your entire phone. Android may prevent " +
                                                "selecting protected folders or storage roots. " +
                                                "Files are scanned only when you tap Import.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedButton(
                                            enabled = !bookImportRunning,
                                            onClick = {
                                                linkLibraryFolderLauncher.launch(null)
                                            }
                                        ) {
                                            Text(if (linkedLibraryFolder)
                                                "Change authorized library folder"
                                            else "Authorize library folder once")
                                        }
                                        if (linkedLibraryFolder) {
                                            OutlinedButton(
                                                enabled = !bookImportRunning,
                                                onClick = {
                                                    bookImportRunning = true
                                                    folderImportStatus =
                                                        "Importing books from authorized folder..."
                                                    researchScope.launch {
                                                        try {
                                                            val result = withContext(
                                                                Dispatchers.IO
                                                            ) {
                                                                AstraFolderAccess.importBooks(
                                                                    applicationContext, localBooks
                                                                )
                                                            }
                                                            importedBookCount =
                                                                localBooks.listBooks().size
                                                            folderImportStatus = result.message()
                                                        } catch (cancel: CancellationException) {
                                                            throw cancel
                                                        } catch (failure: Exception) {
                                                            linkedLibraryFolder =
                                                                AstraFolderAccess.linked(
                                                                    applicationContext
                                                                )
                                                            folderImportStatus =
                                                                "Folder import failed: " +
                                                                    (failure.message ?:
                                                                        "The folder was unavailable.")
                                                        } finally {
                                                            bookImportRunning = false
                                                        }
                                                    }
                                                }
                                            ) { Text("Import books from authorized folder") }
                                            TextButton(onClick = {
                                                try {
                                                    AstraFolderAccess.unlink(
                                                        applicationContext
                                                    )
                                                    linkedLibraryFolder = false
                                                    folderImportStatus =
                                                        "Folder access revoked. " +
                                                        "Already imported private books remain."
                                                } catch (failure: Exception) {
                                                    folderImportStatus =
                                                        failure.message ?: "Cannot revoke access."
                                                }
                                            }) { Text("Forget folder authorization") }
                                        }
                                        if (folderImportStatus.isNotBlank()) {
                                            Text(folderImportStatus,
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        OutlinedButton(
                                            enabled = !bookImportRunning,
                                            onClick = {
                                                importBookLauncher.launch(arrayOf("*/*"))
                                            }
                                        ) {
                                            Text(if (bookImportRunning) "Importing book..."
                                                else "Import document (TXT, EPUB, HTML, CSV, JSON, XML, DOCX…)")
                                        }
                                        if (bookImportStatus.isNotBlank()) {
                                            Text(bookImportStatus,
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (importedBookCount > 0) {
                                            localBooks.listBooks().take(8).forEach { book ->
                                                Text(
                                                    "• " + book.title + " — " +
                                                        book.chunks + " passages",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            OutlinedButton(
                                                enabled = !bookImportRunning,
                                                onClick = { confirmClearBooks = true }
                                            ) { Text("Remove imported books") }
                                        }
                                    }

                                    item {
                                        HorizontalDivider()
                                        Text(
                                            "Local knowledge & continual neural learning",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Astra can train a small on-phone neural topic " +
                                                "adapter from your opted-in conversations. This " +
                                                "updates real neural weights for choosing open-course " +
                                                "context, but does NOT retrain Qwen3 itself. Original " +
                                                "chat messages are not stored in this adapter. " +
                                                "Training is disabled unless you enable it.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Learn topics from conversations",
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Switch(
                                                checked = neuralTrainingEnabled,
                                                onCheckedChange = { enabled ->
                                                    neuralTrainingEnabled = enabled
                                                    storage.setBoolean(
                                                        "astra_neural_learning_opt_in", enabled
                                                    )
                                                }
                                            )
                                        }
                                        Text(
                                            "Local neural updates: $neuralTrainingSteps",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedButton(onClick = {
                                            neuralTrainingEnabled = false
                                            storage.setBoolean(
                                                "astra_neural_learning_opt_in", false
                                            )
                                            neuralLearner.reset()
                                            neuralTrainingSteps = 0
                                        }) { Text("Clear learned neural weights") }
                                        HorizontalDivider()
                                        Text(
                                            "Russian open-course library",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "Includes compact, source-attributed Russian " +
                                                "reference material. You can refresh extracts " +
                                                "from free Wikibooks and Wiktionary pages. " +
                                                "Only fixed public page names are requested; " +
                                                "your chats, tasks and notes are never uploaded. " +
                                                "Sources remain CC BY-SA 4.0 with attribution.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedButton(
                                            enabled = !knowledgeSyncRunning,
                                            onClick = {
                                                knowledgeSyncRunning = true
                                                knowledgeSyncStatus = "Fetching public course pages..."
                                                researchScope.launch {
                                                    try {
                                                        val count = AstraOpenLibrarySync.refresh(
                                                            applicationContext
                                                        ) { completed, total ->
                                                            withContext(Dispatchers.Main) {
                                                                knowledgeSyncStatus =
                                                                    "Public pages: $completed/$total"
                                                            }
                                                        }
                                                        knowledgeSyncStatus =
                                                            "$count licensed pages saved for offline context."
                                                    } catch (cancel: CancellationException) {
                                                        throw cancel
                                                    } catch (error: Exception) {
                                                        knowledgeSyncStatus =
                                                            error.message ?: "Library update failed."
                                                    } finally {
                                                        knowledgeSyncRunning = false
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                if (knowledgeSyncRunning) "Updating open library..."
                                                else "Update free Russian library"
                                            )
                                        }
                                        if (knowledgeSyncStatus.isNotBlank()) {
                                            Text(
                                                knowledgeSyncStatus,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        HorizontalDivider()
                                    }

                                    item {

                                        Text(
                                            "Astra v$ASTRA_VERSION",
                                            style =
                                                MaterialTheme.typography.titleLarge
                                        )
                                    }

                                    item {

                                        Text(
                                            "A local-first assistant for tasks, notes, daily planning, shopping, habits, language practice, mathematics, finance and unit conversions. Explicit Wikipedia research requires the internet and sends only the requested topic. Open-ended questions are answered by the bundled offline Qwen3 model."
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
                                                "Preferred AI response language",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Box {
                                                OutlinedButton(
                                                    onClick = {
                                                        aiReplyLanguageMenuOpen = true
                                                    }
                                                ) {
                                                    Text(aiReplyLanguage.display)
                                                }
                                                DropdownMenu(
                                                    expanded = aiReplyLanguageMenuOpen,
                                                    onDismissRequest = {
                                                        aiReplyLanguageMenuOpen = false
                                                    }
                                                ) {
                                                    AstraReplyLanguage.Choice.entries.forEach { choice ->
                                                        DropdownMenuItem(
                                                            text = { Text(choice.display) },
                                                            onClick = {
                                                                aiReplyLanguage = choice
                                                                aiReplyLanguageMenuOpen = false
                                                                storage.setString(
                                                                    "astra_ai_reply_language",
                                                                    choice.id
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                "Applies to open-ended on-device Qwen3 answers; " +
                                                    "fixed skills and translated lesson cards are unchanged. " +
                                                    "Try 'reply in Setswana', 'reply in French', " +
                                                    "or 'reply in auto' in chat. " +
                                                    "The smaller model may not produce fluent answers " +
                                                    "in every language. Voice settings are independent.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
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
                                                                selectOfflineVoice()
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                "Recognition and text-to-speech need separately installed Android language packs. " +
                                                    "A listed language is not proof of installed offline support. " +
                                                    "System speech fallback may use the internet when allowed.",
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
                                            "Import replaces the current saved data after confirmation. " +
                                            "IMPORTANT: This JSON backup does NOT include imported GGUF " +
                                            "model weights. Before uninstalling Astra for a signing-key " +
                                            "change, keep the original GGUF or FULL APK outside app-private " +
                                            "storage so you can re-import it without downloading again. " +
                                            "Export private vocabulary separately as an all-language " +
                                            "TSV file in the language settings above."
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
                                            "Astra runs a small language model on this device for open-ended questions. Autonomous background agents, scheduled reminders and live market quotes are not included."
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
