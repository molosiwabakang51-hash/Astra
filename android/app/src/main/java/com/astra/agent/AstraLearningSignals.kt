package com.astra.agent

/**
 * Narrow consent gate for automatic online SGD.
 * No commands that manipulate private content, credentials or account data
 * are fed to the neural adapter. Other opted-in free-form queries only update
 * numeric weights; full text is never retained by the adapter.
 */
internal object AstraLearningSignals {
    private val sensitive = Regex(
        """(?i)\b(password|passcode|passphrase|secret|api key|token|access key|pin code|pin number|credit card|bank account|medical record|diagnosis|passport|identity number|social security|private key|seed phrase)\b"""
    )
    // Even when the owner opts into local topic training, health disclosures,
    // crisis messages and dependency or isolation disclosures must never
    // modify the neural adapter. Numeric weights can still encode information
    // about a training sample; absence of a verbatim transcript is insufficient.
    private val personalWellbeing = Regex(
        """(?i)\b(?:suicid(?:e|al)|self[-\s]?harm|self[-\s]?injur\w*|""" +
            """depress(?:ion|ed|ive)?|anxi(?:ety|ous)|panic attack|""" +
            """bipolar|schizophren\w*|trauma(?:tic)?|ptsd|""" +
            """mental(?:ly)?(?:\s+health)?|therap(?:y|ist)|""" +
            """counsell?ing|counsell?or|psychiatr\w*|""" +
            """prescription|medication|antidepressant|""" +
            """hurt myself|kill myself|end my life|want to die|""" +
            """don't want to live|not okay mentally|not doing well mentally|""" +
            """you are all i have|world ended|underground silo)\b"""
    )

    private val privateCommands = Regex(
        """(?i)^(?:remember\b|note\b|save note\b|add task\b|create task\b|shopping list\b|add .* to shopping list|remove .* from shopping list|show notes\b|show tasks\b|copy chat\b|share chat\b|clear chat\b|ask my memory\b|search my memory\b|ask book\b|ask my books\b|reset\b|learn(?:ing)? status\b|start neural learning\b|stop neural learning\b)"""
    )

    fun eligible(message: String): Boolean {
        val value = message.trim()
        return value.length in 3..350 &&
            !sensitive.containsMatchIn(value) &&
            !personalWellbeing.containsMatchIn(value) &&
            AstraDistressSupport.respond(value) == null &&
            !privateCommands.containsMatchIn(value)
    }
}
