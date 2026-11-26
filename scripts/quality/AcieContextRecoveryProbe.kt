package com.astra.agent

import java.util.Base64

/** Host-only synthetic conversation recovery; no answers or private history supplied. */
fun main() {
    fun decode(): String {
        val line = readLine() ?: error("Missing diagnostic input")
        require(line.length <= 32_000)
        return String(Base64.getDecoder().decode(line), Charsets.UTF_8)
    }
    val question = decode()
    val count = (readLine() ?: error("Missing turn count")).toInt()
    require(count in 0..64)
    val turns = List(count) {
        val role = readLine() ?: error("Missing role")
        require(role == "user" || role == "assistant")
        AstraConversationContext.Turn(role, decode())
    }
    val result = AstraConversationContext.userPrompt(question, turns, true, true)
    println(Base64.getEncoder().encodeToString(result.toByteArray(Charsets.UTF_8)))
}
