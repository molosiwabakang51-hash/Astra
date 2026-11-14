package com.astra.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * User-directed, local JSON backup and restore for Astra's existing preference
 * store. The allowlist deliberately excludes future passwords, tokens and keys.
 *
 * Codec is independent from Android activities and can be JVM-tested.
 */
internal object AstraBackupCodec {
    const val MAX_BYTES = 8_000_000
    private const val FORMAT = "astra-local-backup"
    private const val VERSION = 1

    private val arrays = listOf("history", "notes", "tasks", "completed_tasks", "chat_archive")
    private val strings = listOf(
        "astra_shopping_items", "astra_habits", "astra_habit_date",
        "astra_habit_completed_today", "speech_language"
    )
    private val booleans = listOf(
        "auto_send_voice", "speak_responses", "local_only_tts", "allow_system_speech"
    )
    private val ints = listOf(
        "russian_correct", "russian_next_question", "russian_pending_question"
    )

    internal data class Payload(
        val values: Map<String, Any>,
        val historyCount: Int,
        val noteCount: Int,
        val taskCount: Int,
        val completedCount: Int
    ) {
        fun preview(): String =
            "$historyCount chat messages, $noteCount notes, $taskCount open tasks, " +
                "$completedCount completed tasks, plus preferences, shopping lists, " +
                "habits and Russian lesson progress."
    }

    fun encode(source: Map<String, *>): String {
        val data = JSONObject()
        for (key in arrays) {
            val raw = (source[key] as? String) ?: "[]"
            val array = try {
                JSONArray(raw)
            } catch (_: Exception) {
                throw IllegalArgumentException("Saved $key data cannot be exported.")
            }
            data.put(key, array)
        }
        for (key in strings) {
            data.put(key, (source[key] as? String) ?: if (key == "speech_language") "en-US" else "")
        }
        for (key in booleans) {
            data.put(key, (source[key] as? Boolean) ?: (key == "local_only_tts"))
        }
        for (key in ints) {
            data.put(key, ((source[key] as? Int) ?: 0).coerceAtLeast(0))
        }
        val output = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("data", data)
            .toString(2)

        if (output.toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES) {
            throw IllegalArgumentException("Backup exceeds the 8 MB safety limit.")
        }
        // Applying the exact same validation to exported data prevents silent
        // creation of backups that cannot later be restored.
        parse(output)
        return output
    }

    fun parse(raw: String): Payload {
        if (raw.toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES) {
            throw IllegalArgumentException("Backup exceeds the 8 MB safety limit.")
        }
        val root = try {
            JSONObject(raw)
        } catch (_: Exception) {
            throw IllegalArgumentException("Not a valid Astra JSON backup.")
        }
        if (root.optString("format") != FORMAT || root.optInt("version") != VERSION) {
            throw IllegalArgumentException("This is not a supported Astra backup.")
        }
        val data = root.optJSONObject("data")
            ?: throw IllegalArgumentException("The backup is missing its data.")

        val result = linkedMapOf<String, Any>()
        val counts = linkedMapOf<String, Int>()
        for (key in arrays) {
            // Older backups predate multiple conversations; import as an empty archive.
            val array = data.optJSONArray(key)
                ?: if (key == "chat_archive" && !data.has(key)) JSONArray()
                else throw IllegalArgumentException("The backup is missing a valid $key list.")
            if (array.length() > (if (key == "chat_archive") 60 else 2_000)) {
                throw IllegalArgumentException("The $key list is too long to restore.")
            }
            for (i in 0 until array.length()) {
                if (key == "chat_archive") {
                    val chat = array.optJSONObject(i)
                        ?: throw IllegalArgumentException("Invalid archived conversation.")
                    val title = chat.opt("title")
                    val messages = chat.optJSONArray("messages")
                    if (title !is String || title.length > 100 || messages == null ||
                        messages.length() > 2_000
                    ) throw IllegalArgumentException("Invalid archived conversation.")
                    for (j in 0 until messages.length()) {
                        val message = messages.optJSONObject(j)
                            ?: throw IllegalArgumentException("Invalid archived message.")
                        if (message.optString("role") !in setOf("You", "Astra") ||
                            message.opt("text") !is String ||
                            (message.opt("text") as String).length > 12_000
                        ) throw IllegalArgumentException("Invalid archived message.")
                    }
                } else if (key == "history") {
                    val entry = array.optJSONObject(i)
                        ?: throw IllegalArgumentException("Invalid chat entry in backup.")
                    val role = entry.optString("role")
                    val text = entry.opt("text")
                    if (role !in setOf("You", "Astra") ||
                        text !is String || text.length > 12_000
                    ) {
                        throw IllegalArgumentException("Invalid chat message in backup.")
                    }
                } else {
                    val entry = array.opt(i)
                    if (entry !is String || entry.length > 12_000) {
                        throw IllegalArgumentException("Invalid $key item in backup.")
                    }
                }
            }
            result[key] = array.toString()
            counts[key] = array.length()
        }

        for (key in strings) {
            val value = data.opt(key)
            if (value !is String || value.length > 500_000) {
                throw IllegalArgumentException("Invalid $key setting in backup.")
            }
            result[key] = value
        }
        for (key in booleans) {
            val value = data.opt(key)
            if (value !is Boolean) {
                throw IllegalArgumentException("Invalid $key setting in backup.")
            }
            result[key] = value
        }
        for (key in ints) {
            val value = data.opt(key)
            if (value !is Number ||
                value.toLong() !in 0L..10_000_000L ||
                value.toDouble() != value.toLong().toDouble()
            ) {
                throw IllegalArgumentException("Invalid $key progress value in backup.")
            }
            result[key] = value.toInt()
        }
        return Payload(
            values = result,
            historyCount = counts.getValue("history"),
            noteCount = counts.getValue("notes"),
            taskCount = counts.getValue("tasks"),
            completedCount = counts.getValue("completed_tasks")
        )
    }

    /** Avoid loading arbitrarily large user-selected files into memory. */
    fun readLimited(stream: InputStream): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (bytes.size().toLong() + count > MAX_BYTES) {
                throw IllegalArgumentException("Backup exceeds the 8 MB safety limit.")
            }
            bytes.write(buffer, 0, count)
        }
        return bytes.toString("UTF-8")
    }
}
