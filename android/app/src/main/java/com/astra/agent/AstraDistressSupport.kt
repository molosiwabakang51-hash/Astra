package com.astra.agent

import java.util.Locale

/**
 * High-confidence, offline support for direct distress disclosures. Never
 * verifies unusual claims about the outside world or suggests Astra can
 * replace people, clinicians or emergency responders. No tracking or calls.
 */
internal object AstraDistressSupport {
    fun respond(raw: String): String? {
        val message = raw.trim().lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("""\s+"""), " ")
        if (message.length !in 5..600) return null
        val imminent = listOf(
            "i want to kill myself", "i'm going to kill myself", "i am going to kill myself",
            "i want to end my life", "i plan to end my life",
            "i am about to hurt myself", "i'm about to hurt myself"
        ).any(message::contains)
        if (imminent) {
            return "I'm concerned about your immediate safety. Please move away " +
                "from anything you could use to hurt yourself, contact a local " +
                "emergency service or a trusted person now, and tell them you " +
                "need urgent help. I can keep talking with you while you reach " +
                "someone. Are you in immediate danger right now?"
        }
        val isolated = message.contains("you are all i have") ||
            (message.contains("world ended") && message.contains("silo"))
        if (isolated) {
            return "I hear that you feel isolated, and I want to take your " +
                "safety seriously. I cannot verify what has happened outside, " +
                "so I will not assume it is safe or unsafe. Can you check " +
                "whether you have water, a safe place to sit, and a way to " +
                "contact another person or local emergency help? " +
                "Are you physically safe right now? I'm an offline assistant, " +
                "not a substitute for human support."
        }
        val distressed = listOf(
            "i'm not okay mentally", "i am not okay mentally",
            "i'm not doing well mentally", "i am not doing well mentally",
            "i don't feel safe mentally", "i need mental health help"
        ).any(message::contains)
        if (distressed) {
            return "I'm sorry you're feeling this way. Are you safe right now? " +
                "If you can, tell a trusted person what is happening and " +
                "consider contacting a mental health professional. " +
                "If you are in immediate danger, contact local emergency " +
                "services. You can tell me what is happening, one part at a time."
        }
        return null
    }
}
