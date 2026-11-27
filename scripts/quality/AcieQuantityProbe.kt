package com.astra.agent

import java.util.Base64

/** Standalone CPU diagnostic bridge; no chat access and no model-generated code execution. */
fun main() {
    val line = readlnOrNull() ?: return
    val input = try {
        String(Base64.getDecoder().decode(line), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        println("REJECT")
        return
    }
    val checked = AstraQuantityTraceVerifier.verify(input)
    if (checked == null) println("REJECT")
    else println("${checked.result}|${checked.unit}|${checked.steps}")
}
