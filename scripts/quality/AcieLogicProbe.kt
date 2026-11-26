package com.astra.agent

import java.util.Base64

fun main() {
    val line = readLine() ?: error("Missing argument")
    require(line.length <= 16000)
    val question = String(Base64.getDecoder().decode(line), Charsets.UTF_8)
    val result = AstraPropositionalVerifier.verify(question)
    println(result?.verdict?.name ?: "REJECT")
}
