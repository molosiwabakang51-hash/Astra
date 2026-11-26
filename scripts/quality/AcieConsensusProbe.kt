package com.astra.agent

import java.util.Base64

fun main() {
    fun decode(): String {
        val line = readLine() ?: error("Missing diagnostic input")
        require(line.length <= 16000)
        return String(Base64.getDecoder().decode(line), Charsets.UTF_8)
    }
    val question = decode()
    val attempts = List(3) {
        val complete = when (readLine()) {
            "1" -> true
            "0" -> false
            else -> error("Invalid completion flag")
        }
        AstraReasoningConsensus.Attempt(decode(), complete)
    }
    println(AstraReasoningConsensus.select(question, attempts))
}
