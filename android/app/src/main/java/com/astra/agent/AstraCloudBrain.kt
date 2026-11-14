package com.astra.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Network client for a user-controlled Astra gateway. Never embeds an AI
 * provider secret or paid model account. The private gateway authenticates
 * mobile clients and forwards requests to Ollama running on the owner's own PC.
 */
internal class AstraCloudBrain {
    data class Turn(val role: String, val content: String)

    suspend fun reply(endpoint: String, turns: List<Turn>, accessToken: String): String =
        withContext(Dispatchers.IO) {
            val address = endpoint.trim()
            require(isSafeEndpoint(address)) {
                "Configure an HTTPS Astra gateway. Direct provider API URLs are not allowed."
            }
            require(accessToken.length >= 32 && accessToken.length <= 512 &&
                accessToken.all { it.code in 33..126 } &&
                !accessToken.contains(' ') ) {
                "Enter the gateway access token from your private gateway deployment."
            }
            val bounded = sanitizeTurns(turns)
            require(bounded.any { it.role == "user" }) { "No user message supplied." }
            val payload = JSONObject().put("messages", JSONArray().apply {
                bounded.forEach {
                    put(JSONObject().put("role", it.role).put("content", it.content))
                }
            }).toString()

            val connection = (URL(address).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 12_000
                connection.readTimeout = 105_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer " + accessToken)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                require(status in 200..299) {
                    "Astra gateway returned HTTP $status. Check gateway access or quota."
                }
                val bytes = connection.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 96_000) { "AI response too large." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val answer = JSONObject(String(bytes, Charsets.UTF_8))
                    .optString("reply").trim()
                require(answer.isNotEmpty() && answer.length <= 16_000) {
                    "Gateway response was empty or too large."
                }
                answer
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        fun isSafeEndpoint(raw: String): Boolean {
            return try {
                val url = java.net.URI(raw)
                val host = url.host?.lowercase() ?: return false
                url.scheme.equals("https", true) &&
                    url.userInfo == null &&
                    url.fragment == null &&
                    url.port in listOf(-1, 443) &&
                    host != "localhost" &&
                    host != "127.0.0.1" &&
                    host != "::1" &&
                    !host.endsWith(".local") &&
                    !host.endsWith(".internal") &&
                    !host.endsWith(".openai.com") &&
                    !host.endsWith(".anthropic.com") &&
                    !host.endsWith(".googleapis.com") &&
                    !host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))
            } catch (_: Exception) { false }
        }

        fun sanitizeTurns(turns: List<Turn>): List<Turn> =
            turns.takeLast(16).mapNotNull { turn ->
                val role = turn.role.lowercase()
                val value = turn.content.trim().take(3_000)
                if (role in setOf("user", "assistant") && value.isNotBlank()) {
                    Turn(role, value)
                } else null
            }
    }
}
