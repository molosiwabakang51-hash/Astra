package com.astra.agent

import java.util.Base64

/** Host-only bridge: production prompt and visible-output code; no answer key. */
fun main() {
    val operation = readLine() ?: error("Missing operation")
    val encoded = readLine() ?: error("Missing input")
    require(encoded.length <= 128_000)
    val input = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    val result = when (operation) {
        "baseline" -> AstraConversationContext.userPrompt(input, emptyList(), false, true)
        "framing" -> AstraConversationContext.userPrompt(
            AstraScalarFraming.prepare(input, true, false, false, false, true),
            emptyList(), false, true)
        "visible" -> AstraOfflinePolicy.publicReply(input)
        else -> error("Unknown operation")
    }
    println(Base64.getEncoder().encodeToString(result.toByteArray(Charsets.UTF_8)))
}
