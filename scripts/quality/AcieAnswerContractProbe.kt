package com.astra.agent

import java.util.Base64

/** Synthetic host diagnostic only. No answer key or model prompt modification. */
fun main() {
    fun decode(): String {
        val line = readLine() ?: error("Missing diagnostic input")
        require(line.length <= 100_000)
        return String(Base64.getDecoder().decode(line), Charsets.UTF_8)
    }
    val question = decode()
    val answer = decode()
    val normalized = AstraAnswerContract.normalize(question, answer)
    println(Base64.getEncoder().encodeToString(normalized.toByteArray(Charsets.UTF_8)))
}
