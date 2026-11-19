package com.astra.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Conversation archive. The current conversation continues to live in the
 * legacy "history" preference; only inactive conversations live here.
 * This preserves upgrades from all earlier Astra versions.
 */
internal object AstraChatArchive {
    internal const val MAX_CHATS = 40
    internal const val MAX_MESSAGES = 2_000

    data class Message(val role: String, val text: String)
    data class Chat(val id: String, val title: String, val messages: List<Message>)

    fun decode(raw: String): List<Chat> {
        return try {
            val array = JSONArray(raw)
            val output = mutableListOf<Chat>()
            val ids = mutableSetOf<String>()
            for (i in 0 until minOf(array.length(), MAX_CHATS)) {
                val item = array.optJSONObject(i) ?: continue
                val id = AstraUnicodeBounds.prefix(item.optString("id"), 80)
                if (id.isBlank() || !ids.add(id)) continue
                val entries = item.optJSONArray("messages") ?: continue
                val messages = mutableListOf<Message>()
                for (j in 0 until minOf(entries.length(), MAX_MESSAGES)) {
                    val entry = entries.optJSONObject(j) ?: continue
                    val role = entry.optString("role")
                    val text = entry.optString("text")
                    if (role in setOf("You", "Astra")) {
                        messages.add(Message(role, text))
                    }
                }
                output.add(Chat(id, AstraUnicodeBounds.prefix(item.optString("title"), 100), messages))
            }
            output
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(chats: List<Chat>): String {
        val array = JSONArray()
        chats.take(MAX_CHATS).forEach { chat ->
            val messages = JSONArray()
            // Preserve full local and model-generated messages. The old 12k
            // limit rejected valid 16k model replies and could crash "New chat".
            // Ignore malformed roles rather than failing an entire archive.
            chat.messages.takeLast(MAX_MESSAGES)
                .filter { it.role in setOf("You", "Astra") }
                .forEach { message ->
                    messages.put(
                        JSONObject().put("role", message.role)
                            .put("text", message.text)
                    )
                }
            array.put(
                JSONObject().put("id", AstraUnicodeBounds.prefix(chat.id, 80))
                    .put("title", AstraUnicodeBounds.prefix(chat.title, 100))
                    .put("messages", messages)
            )
        }
        return array.toString()
    }

    fun title(messages: List<Message>): String =
        messages.firstOrNull { it.role == "You" && it.text.isNotBlank() }
            ?.text?.replace(Regex("\\s+"), " ")?.let {
                AstraUnicodeBounds.prefix(it, 55)
            }
            ?: "New conversation"
}
