package com.astra.agent

import java.util.Base64

/** Public synthetic diagnostic bridge to the actual production calculator. */
fun main(args: Array<String>) {
    require(args.size == 1)
    fun decode(): String {
        val line = readLine() ?: error("Missing diagnostic input")
        require(line.length <= 16000)
        return String(Base64.getDecoder().decode(line), Charsets.UTF_8)
    }
    fun emit(text: String) {
        println(Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)))
    }
    val question = decode()
    when (args[0]) {
        "retry_gate" -> {
            // Host-only candidate. A numeric-format request is not proof
            // of solvability. Exclude ambiguous number notation and
            // questions with fewer than two distinct bounded literals.
            val literal = Regex("""(?<![\p{L}\p{N}_.])[0-9]{1,9}(?:\.[0-9]{1,4})?(?![\p{L}\p{N}_]|\.[0-9])""")
            val values = literal.findAll(question).map {
                it.value.toBigDecimal().stripTrailingZeros()
            }.toSet()
            val proposal = decode().trim()
            val syntax = Regex("""CALC: [0-9 .()+*/-]{1,120}""").matches(proposal)
            val allowed = AstraCalculationAssist.requestsNumericOnly(question) &&
                !Regex("""[0-9],[0-9]""").containsMatchIn(question) &&
                values.size >= 2 && syntax
            println(if (allowed) "ALLOW" else "REJECT")
        }
        "plan" -> emit(AstraCalculationAssist.planningPrompt(question))
        "evaluate" -> {
            val result = AstraCalculationAssist.evaluate(question, decode())
            if (result == null) {
                println("REJECT")
            } else {
                println("ACCEPT")
                emit(result.result)
                emit(AstraCalculationAssist.synthesisPrompt(question, result))
            }
        }
        else -> error("Unknown diagnostic operation")
    }
}
