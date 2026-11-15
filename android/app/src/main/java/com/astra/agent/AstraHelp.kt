package com.astra.agent

/** Short conversational help; the full reference remains under "commands". */
internal object AstraHelp {
    fun concise(): String =
        "I can help you learn, plan, calculate, and organise things offline. " +
            "Try 'languages', 'learn setswana', 'russian', " +
            "'explain bond duration', 'calculate 12 * 5', " +
            "'add milk to shopping list', or 'what should I do next'. " +
            "With a local Qwen3 model installed, you can also ask open-ended " +
            "questions naturally. Type 'workflow help' to safely preview " +
            "several local actions, or 'commands' for the full reference."
}
