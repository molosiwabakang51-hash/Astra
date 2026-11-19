package com.astra.agent

import java.util.Locale

/**
 * Conservative script-independent language cues for offline TTS routing.
 *
 * This is NOT an ASR model, language translator, or a trained language
 * identifier. It recognizes only clear, extended Latin-script reply text.
 * A mixed-language reply or short quotation leaves the configured voice
 * unchanged. Exact regional voices remain mandatory downstream.
 */
internal object AstraSpokenLanguageHeuristics {
    private data class Profile(
        val language: SpeechLanguage,
        val words: Set<String>,
        val anchors: Set<String>
    )

    private fun words(vararg tokens: String) = tokens.toSet()

    private val profiles = listOf(
        Profile(SpeechLanguage.FRENCH,
            words("je", "suis", "vous", "nous", "avec", "pour", "dans", "les", "des",
                "est", "une", "votre", "mon", "mes", "peux", "pouvez", "aider", "voici",
                "comment", "ensuite", "étapes", "bien", "plus", "merci", "bonjour",
                "parce", "que", "cette", "cela", "être", "faire", "très", "aujourd"),
            words("suis", "vous", "nous", "avec", "pour", "peux", "pouvez",
                "voici", "étapes", "cette", "cela", "être", "très")),
        Profile(SpeechLanguage.SPANISH,
            words("puedo", "ayudarte", "hola", "gracias", "porque", "para", "con",
                "los", "las", "una", "unas", "tienes", "tiene", "aquí", "explicación",
                "primero", "después", "entonces", "cómo", "estás", "puedes",
                "quieres", "hacer", "siguiente", "pregunta", "este", "esta", "estas",
                "nuestro", "usted", "ustedes", "sin", "pero", "también", "hablar"),
            words("puedo", "ayudarte", "porque", "los", "las", "tienes",
                "aquí", "explicación", "después", "estás", "puedes", "quieres",
                "siguiente", "pregunta", "usted", "ustedes", "también")),
        Profile(SpeechLanguage.PORTUGUESE,
            words("posso", "ajudar", "obrigado", "obrigada", "olá", "você", "vocês",
                "não", "estão", "está", "isso", "aqui", "ainda", "também",
                "explicar", "explicação", "primeiro", "depois", "seu", "sua",
                "sobre", "com", "para", "uma", "este", "esta", "mais", "tem",
                "temos", "passos", "pergunta", "então", "como", "muito"),
            words("posso", "ajudar", "obrigado", "obrigada", "você", "vocês",
                "não", "estão", "isso", "ainda", "também", "explicar", "explicação",
                "depois", "então", "temos", "passos")),
        Profile(SpeechLanguage.GERMAN,
            words("ich", "kann", "dir", "dich", "deine", "ihnen", "sie", "hier",
                "sind", "nicht", "auch", "und", "eine", "einen", "einem", "diese",
                "dieses", "damit", "dabei", "zuerst", "danach", "dann", "schritte",
                "erklären", "helfen", "bitte", "danke", "wie", "wenn", "weil",
                "für", "über", "wird", "werden", "möchte", "machen"),
            words("ich", "kann", "dir", "deine", "ihnen", "hier", "sind",
                "nicht", "eine", "einen", "dabei", "zuerst", "danach",
                "schritte", "erklären", "helfen", "für", "möchte")),
        Profile(SpeechLanguage.ITALIAN,
            words("sono", "posso", "aiutarti", "ciao", "grazie", "questo", "questa",
                "qui", "primo", "prima", "dopo", "spiegare", "come", "puoi",
                "possiamo", "perché", "quindi", "anche", "della", "degli", "sono",
                "molto", "adesso", "seguente", "risposta", "domanda", "passaggi",
                "con", "per", "una", "tutti", "sapere", "fare", "essere"),
            words("sono", "aiutarti", "questo", "questa", "spiegare", "puoi",
                "possiamo", "perché", "quindi", "anche", "della", "degli",
                "seguente", "risposta", "domanda", "passaggi")),
        Profile(SpeechLanguage.TURKISH,
            words("merhaba", "teşekkürler", "nasıl", "yardımcı", "olabilirim",
                "sana", "size", "için", "bir", "ve", "veya", "ama", "şimdi",
                "önce", "sonra", "burada", "bunlar", "bunu", "şöyle", "cevap",
                "soru", "sorunuz", "açıklama", "yapmak", "öğrenmek",
                "gerekir", "istiyorsanız", "adımlar", "olacak"),
            words("merhaba", "teşekkürler", "nasıl", "yardımcı", "olabilirim",
                "sana", "size", "için", "şimdi", "önce", "sonra", "burada",
                "bunlar", "şöyle", "sorunuz", "açıklama", "öğrenmek", "adımlar")),
        Profile(SpeechLanguage.SWAHILI,
            words("habari", "asante", "tafadhali", "ninaweza", "kukusaidia",
                "unaweza", "sasa", "hapa", "kwa", "na", "wa", "ya", "kwanza",
                "baadaye", "hatua", "swali", "lako", "jibu", "hili", "hii",
                "yako", "yangu", "tunaweza", "kujifunza", "kuelewa", "kufanya",
                "nzuri", "sana", "maana", "jinsi", "lakini", "ndiyo"),
            words("habari", "asante", "tafadhali", "ninaweza", "kukusaidia",
                "unaweza", "kwanza", "baadaye", "hatua", "swali", "lako",
                "hili", "tunaweza", "kujifunza", "kuelewa", "jinsi")),
        Profile(SpeechLanguage.SETSWANA,
            words("dumela", "dumela", "dumela", "dumela", "dumelang",
                "leboga", "tsogile", "jang", "thuso", "thuta", "thuto",
                "tsela", "gore", "ka", "le", "tsa", "ke", "nna", "teng",
                "bona", "go", "kgona", "ntlha", "morago", "jaanong",
                "batho", "botshelo", "dipotso", "potso", "karabo", "botlhe"),
            words("dumela", "dumelang", "leboga", "tsogile", "jang", "thuso",
                "thuta", "thuto", "tsela", "gore", "kgona", "jaanong",
                "batho", "botshelo", "dipotso", "potso", "karabo"))
    )

    private val tokenPattern = Regex("""[\p{L}]+(?:['’][\p{L}]+)?""")
    private val englishMarkers = words(
        "the", "this", "that", "your", "you", "are", "is", "with",
        "and", "from", "about", "will", "would", "should", "can",
        "please", "here", "which", "what", "these", "there", "them",
        "have", "was", "were", "their", "after", "before", "because"
    )

    fun detect(text: String, configured: SpeechLanguage): SpeechLanguage? {
        if (text.length !in 40..50_000) return null
        val tokens = tokenPattern.findAll(text.take(900))
            .map { it.value.lowercase(Locale.ROOT) }
            .take(160).toList()
        if (tokens.size < 8) return null
        val english = tokens.count { it in englishMarkers }
        data class Result(val profile: Profile, val score: Int, val matches: Int)
        val matched = profiles.mapNotNull { profile ->
            val matches = tokens.count { it in profile.words }
            val anchors = tokens.filter { it in profile.anchors }
            if (matches < 5 || anchors.toSet().size < 3 ||
                matches * 3 < tokens.size || matches <= english) null
            else Result(profile, matches + anchors.size * 2, matches)
        }.sortedByDescending { it.score }
        val best = matched.firstOrNull() ?: return null
        if (matched.size > 1 && best.score - matched[1].score < 4) return null
        return when (best.profile.language) {
            SpeechLanguage.FRENCH ->
                if (configured == SpeechLanguage.FRENCH_CANADA) configured
                else SpeechLanguage.FRENCH
            SpeechLanguage.SPANISH ->
                if (configured == SpeechLanguage.SPANISH_MEXICO) configured
                else SpeechLanguage.SPANISH
            SpeechLanguage.PORTUGUESE ->
                if (configured == SpeechLanguage.PORTUGUESE_BRAZIL) configured
                else SpeechLanguage.PORTUGUESE
            else -> best.profile.language
        }
    }
}
