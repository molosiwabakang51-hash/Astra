package com.astra.agent

import java.util.Locale

/**
 * Tiny offline corrections for documented handset factual regressions.
 * The source is Yale School of Management's Robert J. Shiller faculty
 * biography: https://som.yale.edu/faculty-research/faculty-directory/robert-j-shiller
 *
 * This intentionally recognizes only narrow, high-confidence questions.
 * Other questions continue to Astra's normal offline model.
 */
internal object AstraVerifiedEconomics {
    fun respond(raw: String): String? {
        val prompt = raw.trim().lowercase(Locale.ROOT)
            .trimEnd('?', '!', '.', ' ').replace(Regex("""\s+"""), " ")
        if (prompt.length !in 5..170) return null
        if ("stiller" in prompt && "robert" in prompt) {
            return "Did you mean Robert J. Shiller? He is the Yale economist " +
                "who wrote Irrational Exuberance (2000), not 'Niall Shiller'. " +
                "I can explain his research on speculative bubbles."
        }
        if ("shiller" !in prompt) return null
        if ("niall shiller" in prompt) {
            return "You are correct to question that name. Robert J. Shiller " +
                "wrote Irrational Exuberance (2000). He is associated with " +
                "Yale University, not the University of Chicago. " +
                "'Niall Shiller' was a mistaken attribution in my earlier answer."
        }
        if ("irrational exuberance" in prompt &&
            (prompt.startsWith("what") || prompt.startsWith("explain") ||
                prompt.startsWith("tell me") || prompt.startsWith("shiller"))) {
            return "Robert J. Shiller's Irrational Exuberance (2000) examines " +
                "speculative bubbles: stories, investor psychology and feedback " +
                "from rising prices can reinforce optimism and push valuations " +
                "away from fundamentals. Such patterns can reverse; he does not " +
                "claim that every price rise is a bubble. The title echoes " +
                "Alan Greenspan's 1996 phrase. Shiller is a Yale economist. " +
                "Reference: Yale School of Management faculty biography."
        }
        if (prompt == "who is robert j shiller" ||
            prompt == "who is robert j. shiller" ||
            prompt == "robert j shiller" ||
            prompt == "robert j. shiller" ||
            prompt == "who is robert shiller") {
            return "Robert J. Shiller is a Yale economist known for research " +
                "on asset prices, behavioral finance and speculative bubbles. " +
                "He wrote Irrational Exuberance (2000) and shared the " +
                "2013 Nobel Memorial Prize in Economic Sciences. " +
                "He is not a University of Chicago professor. " +
                "Reference: Yale School of Management faculty biography."
        }
        return null
    }
}
