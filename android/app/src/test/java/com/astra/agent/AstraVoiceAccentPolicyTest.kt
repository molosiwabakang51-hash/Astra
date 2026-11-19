package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraVoiceAccentPolicyTest {
    private fun candidate(
        tag: String,
        network: Boolean = false,
        installed: Boolean = true,
        quality: Int = 400,
        name: String = tag
    ) = AstraVoiceAccentPolicy.Candidate(name, tag, network, installed, quality)

    @Test fun localeMatchRequiresExactLanguageAndCountry() {
        assertTrue(AstraVoiceAccentPolicy.sameAccent("en-GB", "en-GB"))
        assertTrue(AstraVoiceAccentPolicy.sameAccent("en-GB", "en-GB-x-voice"))
        assertTrue(AstraVoiceAccentPolicy.sameAccent("fr-CA", "fr-CA"))
        assertTrue(AstraVoiceAccentPolicy.sameAccent("pt-BR", "pt-BR"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("en-GB", "en-US"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("fr-FR", "fr-CA"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("pt-PT", "pt-BR"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("es-MX", "es-ES"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("en-GB", "en"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("en-GB", "fr-GB"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("en", "en-US"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("en-GB", ""))
    }

    @Test fun neverSubstitutesAnotherAccentOrUninstalledVoice() {
        val alternatives = listOf(
            candidate("en-US"),
            candidate("en-AU"),
            candidate("fr-FR"),
            candidate("en-GB", installed = false, name = "needs-download")
        )
        assertNull(AstraVoiceAccentPolicy.choose("en-GB", alternatives, true))
        assertNull(AstraVoiceAccentPolicy.choose("en-GB", alternatives, false))
        assertNull(AstraVoiceAccentPolicy.choose("sw-KE", alternatives, true))
    }

    @Test fun anInstalledOfflineVoiceIsPreferredEvenWhenNetworkAllowed() {
        val options = listOf(
            candidate("fr-FR", network = true, quality = 500, name = "online"),
            candidate("fr-FR", network = false, quality = 300, name = "local"),
            candidate("fr-CA", network = false, quality = 500, name = "canada"),
            candidate("fr-FR", network = false, installed = false, quality = 600, name = "missing")
        )
        assertEquals("local", AstraVoiceAccentPolicy.choose("fr-FR", options, true)?.name)
        assertEquals("local", AstraVoiceAccentPolicy.choose("fr-FR", options, false)?.name)
        assertEquals("canada", AstraVoiceAccentPolicy.choose("fr-CA", options, true)?.name)
    }

    @Test fun networkVoiceIsOnlyConsideredWithExplicitOfflineOnlyDisabled() {
        val options = listOf(candidate("ja-JP", network = true))
        assertNull(AstraVoiceAccentPolicy.choose("ja-JP", options, true))
        assertEquals("ja-JP", AstraVoiceAccentPolicy.choose("ja-JP", options, false)?.name)
    }

    @Test fun matchingInstalledLocalVoicesUseQualityAndStableOrdering() {
        val voices = listOf(
            candidate("de-DE", name = "z", quality = 400),
            candidate("de-DE", name = "b", quality = 450),
            candidate("de-DE", name = "a", quality = 450)
        )
        assertEquals("a", AstraVoiceAccentPolicy.choose("de-DE", voices, true)?.name)
    }

    @Test fun explicitAiLanguageNeverUsesVoiceOfUnrelatedLanguage() {
        val policy = AstraVoiceAccentPolicy
        assertEquals(SpeechLanguage.FRENCH,
            policy.forReply(SpeechLanguage.ENGLISH_US,
                AstraReplyLanguage.Choice.FRENCH, "Bonjour, je suis Astra."))
        assertEquals(SpeechLanguage.FRENCH_CANADA,
            policy.forReply(SpeechLanguage.FRENCH_CANADA,
                AstraReplyLanguage.Choice.FRENCH, "Bonjour."))
        assertEquals(SpeechLanguage.PORTUGUESE_BRAZIL,
            policy.forReply(SpeechLanguage.PORTUGUESE_BRAZIL,
                AstraReplyLanguage.Choice.PORTUGUESE, "Olá!"))
        assertEquals(SpeechLanguage.SPANISH_MEXICO,
            policy.forReply(SpeechLanguage.SPANISH_MEXICO,
                AstraReplyLanguage.Choice.SPANISH, "Hola!"))
        assertEquals(SpeechLanguage.ENGLISH_UK,
            policy.forReply(SpeechLanguage.ENGLISH_UK,
                AstraReplyLanguage.Choice.ENGLISH, "Hello."))
        assertEquals(SpeechLanguage.ARABIC,
            policy.forReply(SpeechLanguage.ENGLISH_US,
                AstraReplyLanguage.Choice.ARABIC, "مرحبا."))
        assertEquals(SpeechLanguage.KOREAN,
            policy.forReply(SpeechLanguage.ENGLISH_US,
                AstraReplyLanguage.Choice.KOREAN, "안녕하세요."))
    }

    @Test fun automaticModeAvoidsForeignQuotesConfusingMainLanguage() {
        val policy = AstraVoiceAccentPolicy
        assertEquals(SpeechLanguage.ENGLISH_UK,
            policy.forReply(SpeechLanguage.ENGLISH_UK,
                AstraReplyLanguage.Choice.AUTO,
                "Your French word is Bonjour and German is Danke. The answer is simple."))
        assertEquals(SpeechLanguage.RUSSIAN,
            policy.forReply(SpeechLanguage.ENGLISH_US,
                AstraReplyLanguage.Choice.AUTO,
                "Здравствуйте! Меня зовут Астра. Как вам помочь?"))
        assertEquals(SpeechLanguage.JAPANESE,
            policy.forReply(SpeechLanguage.ENGLISH_US,
                AstraReplyLanguage.Choice.AUTO,
                "こんにちは。今日は何を勉強しますか？"))
    }

    @Test fun mandarinVoicesRejectContradictoryScriptsWithinSameRegion() {
        assertTrue(AstraVoiceAccentPolicy.sameAccent("zh-CN", "zh-CN"))
        assertTrue(AstraVoiceAccentPolicy.sameAccent("zh-CN", "zh-Hans-CN"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("zh-CN", "zh-Hant-CN"))
        assertTrue(AstraVoiceAccentPolicy.sameAccent("zh-TW", "zh-Hant-TW"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("zh-TW", "zh-Hans-TW"))
        assertTrue(AstraVoiceAccentPolicy.sameAccent("zh-Hans-CN", "zh-Hans-CN"))
        assertFalse(AstraVoiceAccentPolicy.sameAccent("zh-Hans-CN", "zh-CN"))
        val options = listOf(
            candidate("zh-Hant-CN", name = "traditional"),
            candidate("zh-Hans-CN", name = "simplified", quality = 320),
            candidate("zh-Hans-CN", name = "network", network = true, quality = 500)
        )
        assertEquals("simplified",
            AstraVoiceAccentPolicy.choose("zh-CN", options, offlineOnly = true)?.name)
        assertNull(AstraVoiceAccentPolicy.choose(
            "zh-CN", options.filter { it.name == "traditional" }, offlineOnly = true
        ))
    }
}
