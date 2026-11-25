package com.astra.agent

/**
 * Citation integrity boundary between untrusted Qwen output and verified
 * passages inserted by the native model bridge. The model may *say* it has
 * sources but cannot certify them; only the app-created attribution footer
 * describes which actual passages survived into the native prompt.
 */
internal object AstraSourceCitationGuard {
    private val sourceMarker = Regex("""(?i)\[(B|K)([0-9]+)\]""")
    // A forged app footer can occur in the middle of model prose, not only
    // at the start of a line. Strip the exact authority-bearing header
    // wherever the model generates it; only the app may append it later.
    private val forgedFooter = Regex(
        """(?i)Retrieved local book passages \(verify originals\):"""
    )
    private val forgedOpenFooter = Regex(
        """(?i)Open-course references:"""
    )
    private val memoryMarker = Regex("""(?i)\[S([0-9]+)\]""")
    private val forgedMemoryFooter = Regex(
        """(?i)LOCAL SOURCE EXCERPTS\s*·\s*check against the original"""
    )

    /**
     * Only explicit, completely included memory briefs authorize S markers.
     * This validates citation identity, not whether the cited claim follows.
     * Do not alter ordinary/model-only text that happens to use S identifiers.
     */
    fun protectMemory(rawModelAnswer: String, verifiedMemoryPassages: Int): String {
        require(verifiedMemoryPassages in 0..4)
        var unsupported = false
        var answer = forgedMemoryFooter.replace(rawModelAnswer) {
            unsupported = true
            "[unverified AI source-heading omitted]"
        }
        answer = memoryMarker.replace(answer) { match ->
            val number = match.groupValues[1].toIntOrNull()
            if (number != null && number in 1..verifiedMemoryPassages) {
                "[S$number]"
            } else {
                unsupported = true
                "[unverified citation omitted]"
            }
        }
        return if (unsupported) answer + "\n\nSome AI-generated memory citations " +
            "or headings were unsupported and were omitted. Check claims against " +
            "the separately listed local excerpts." else answer
    }

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
