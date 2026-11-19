package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraSpokenLanguageHeuristicsTest {
    private val auto = AstraReplyLanguage.Choice.AUTO

    @Test fun detectsClearLatinScriptRepliesAndMatchesTheirSpokenLanguage() {
        val cases = mapOf(
            SpeechLanguage.FRENCH to
                "Bonjour, je suis Astra et je peux vous aider avec votre question. Voici les étapes que vous pouvez suivre pour apprendre.",
            SpeechLanguage.SPANISH to
                "Hola, puedo ayudarte con tu pregunta. Primero, aquí tienes una explicación; después puedes hacer otra pregunta para aprender mejor.",
            SpeechLanguage.PORTUGUESE to
                "Olá, posso ajudar com sua pergunta. Primeiro, aqui estão os passos para entender isso; depois você pode continuar.",
            SpeechLanguage.GERMAN to
                "Ich kann dir dabei helfen. Hier sind die nächsten Schritte und danach kannst du deine Frage noch genauer erklären.",
            SpeechLanguage.ITALIAN to
                "Ciao, posso aiutarti con questa domanda. Qui puoi trovare una spiegazione, quindi possiamo continuare con altri passaggi.",
            SpeechLanguage.TURKISH to
                "Merhaba, sana yardımcı olabilirim. Şimdi burada bu sorunuz için önce bir açıklama, sonra adımlar ve başka bir soru.",
            SpeechLanguage.SWAHILI to
                "Habari, ninaweza kukusaidia kwa swali lako. Hapa kuna hatua za kwanza; unaweza kujifunza jinsi ya kufanya hivi.",
            SpeechLanguage.SETSWANA to
                "Dumela, ke nna Astra. Ke kgona go thusa ka potso ya gago. Jaanong re ka ithuta thuto eno gore o bone tsela ya karabo."
        )
        for ((expected, sentence) in cases) {
            assertEquals(expected.displayName, expected,
                AstraVoiceAccentPolicy.forReply(SpeechLanguage.ENGLISH_US, auto, sentence))
        }
    }

    @Test fun automaticRoutingKeepsUserSelectedRegionalAccents() {
        val french = "Bonjour, je suis Astra et je peux vous aider avec votre question. Voici les étapes que vous pouvez suivre pour apprendre."
        val spanish = "Hola, puedo ayudarte con tu pregunta. Primero, aquí tienes una explicación; después puedes hacer otra pregunta para aprender mejor."
        val portuguese = "Olá, posso ajudar com sua pergunta. Primeiro, aqui estão os passos para entender isso; depois você pode continuar."
        assertEquals(SpeechLanguage.FRENCH_CANADA,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.FRENCH_CANADA, auto, french))
        assertEquals(SpeechLanguage.SPANISH_MEXICO,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.SPANISH_MEXICO, auto, spanish))
        assertEquals(SpeechLanguage.PORTUGUESE_BRAZIL,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.PORTUGUESE_BRAZIL, auto, portuguese))
    }

    @Test fun shortSamplesEnglishExplanationsAndMixedQuotesFailClosed() {
        for (reply in listOf(
            "Bonjour",
            "Hola, gracias!",
            "Your French word is Bonjour and the German word is Danke. The answer is simple.",
            "The phrase je suis très content de vous aider means I am pleased to help. Please practise this French phrase using the vocabulary cards and listen to your recording.",
            "Your textbook includes the Spanish words hola and gracias. The written answer should be in English so we can check the reasoning.",
            "Dumela is a Setswana greeting. This English explanation describes when you might use it, not a Setswana conversation.",
            "Asante and habari are Swahili examples, but the remaining text is English and explains pronunciation step by step."
        )) {
            assertEquals(reply, SpeechLanguage.ENGLISH_UK,
                AstraVoiceAccentPolicy.forReply(SpeechLanguage.ENGLISH_UK, auto, reply))
        }
        assertNull(AstraSpokenLanguageHeuristics.detect("Bonjour", SpeechLanguage.ENGLISH_US))
    }

    @Test fun japaneseMixedKanjiAndKanaDoesNotBecomeMandarin() {
        assertEquals(SpeechLanguage.JAPANESE,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.ENGLISH_US, auto,
                "日本語を勉強するとき、漢字とひらがなを使って文章を書きます。"))
        assertEquals(SpeechLanguage.MANDARIN,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.ENGLISH_US, auto,
                "你好，我可以帮助你学习中文。以下是一些简单的步骤。"))
    }

    @Test fun distinguishesClearArabicAndUrduScriptWhileAvoidingShortSwitches() {
        assertEquals(SpeechLanguage.ARABIC,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.ENGLISH_US, auto,
                "مرحباً، يمكنني مساعدتك في شرح هذا السؤال باللغة العربية، وهذه هي الخطوات الأساسية."))
        assertEquals(SpeechLanguage.URDU,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.ENGLISH_US, auto,
                "آپ کیسے ہیں؟ میں آپ کی مدد کر سکتی ہوں، اور ہم اردو میں بات کر سکتے ہیں۔"))
        assertEquals(SpeechLanguage.ENGLISH_US,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.ENGLISH_US, auto,
                "The Arabic word مرحبا means hello. Here is an English explanation."))
    }

    @Test fun explicitLanguageIsStillPreferredOverAutomaticGuessing() {
        val french = "Je suis Astra et je peux vous aider avec votre question. Voici les étapes que vous pouvez suivre pour apprendre."
        assertEquals(SpeechLanguage.ENGLISH_UK,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.ENGLISH_UK,
                AstraReplyLanguage.Choice.ENGLISH, french))
        assertEquals(SpeechLanguage.FRENCH_CANADA,
            AstraVoiceAccentPolicy.forReply(SpeechLanguage.FRENCH_CANADA,
                AstraReplyLanguage.Choice.FRENCH, "Hello, I can explain this in English."))
        assertTrue(AstraSpokenLanguageHeuristics.detect("", SpeechLanguage.ENGLISH_US) == null)
    }
}
