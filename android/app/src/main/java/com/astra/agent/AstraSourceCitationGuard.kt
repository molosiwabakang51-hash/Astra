package com.astra.agent

/**
 * Citation integrity boundary between untrusted Qwen output and verified
 * passages inserted by the native model bridge. The model may *say* it has
 * sources but cannot certify them; only the app-created attribution footer
 * describes which actual passages survived into the native prompt.
 */
internal object AstraSourceCitationGuard {
    private val sourceMarker = Regex("""(?i)\[(B|K)([0-9]+)\]""")
    private val forgedFooter = Regex(
        """(?im)^[ \t]*Retrieved local book passages \(verify originals\):[ \t]*\r?$"""
    )
    private val forgedOpenFooter = Regex(
        """(?im)^[ \t]*Open-course references:[ \t]*\r?$"""
    )

    fun protect(
        rawModelAnswer: String,
        verifiedPrivatePassages: Int,
        verifiedOpenPassages: Int
    ): String {
        require(verifiedPrivatePassages in 0..2 &&
            verifiedOpenPassages in 0..3) { "Unexpected evidence count." }
        var spoofedFooter = false
        var answer = forgedFooter.replace(rawModelAnswer) {
            spoofedFooter = true
            "[unverified AI source-heading omitted]"
        }
        answer = forgedOpenFooter.replace(answer) {
            spoofedFooter = true
            "[unverified AI source-heading omitted]"
        }
        var unsupported = false
        answer = sourceMarker.replace(answer) { match ->
            val kind = match.groupValues[1].uppercase(java.util.Locale.ROOT)
            val number = match.groupValues[2].toIntOrNull()
            val allowed = if (kind == "B")
                verifiedPrivatePassages else verifiedOpenPassages
            if (number != null && number in 1..allowed) {
                "[$kind$number]"
            } else {
                unsupported = true
                "[unverified citation omitted]"
            }
        }
        if (spoofedFooter || unsupported) {
            answer += "\n\nSome AI-generated source markers or headings were " +
                "unsupported and were omitted. Verify claims against " +
                "the separately listed local passages."
        }
        return answer
    }
}
