package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraLearningSignalsPrivacyTest {
    @Test fun privateBookCommandsNeverTrainNeuralTopicAdapter() {
        assertFalse(AstraLearningSignals.eligible("ask book Russian genitive cases"))
        assertFalse(AstraLearningSignals.eligible("ask my books about grammar"))
        assertFalse(AstraLearningSignals.eligible("remember my account password"))
        assertFalse(AstraLearningSignals.eligible("add milk to shopping list"))
    }

    @Test fun crisesAndMentalHealthDisclosuresNeverTrainEvenWithOptIn() {
        val examples = listOf(
            "I'm not okay mentally",
            "I am not doing well mentally",
            "I need mental health help",
            "I feel anxious",
            "I am struggling with depression",
            "I have a panic attack",
            "I want to kill myself",
            "I have thoughts of self-harm",
            "I don't want to live",
            "You are all I have, the world ended I'm in an underground silo",
            "I need my medication",
            "I would like to talk to a therapist",
            "What is post-traumatic stress or PTSD?"
        )
        for (message in examples) {
            assertFalse(message, AstraLearningSignals.eligible(message))
        }
    }

    @Test fun normalAcademicAndLanguageStudyRemainEligible() {
        for (message in listOf(
            "Explain Russian noun cases",
            "Teach me French verb agreement",
            "Explain index futures margin and collateral",
            "What is Newton's second law?",
            "How do compiler optimizations work?",
            "Calculate bond duration",
            "What is the gravitational potential energy?"
        )) {
            assertTrue(message, AstraLearningSignals.eligible(message))
        }
    }

    @Test fun ordinaryOptedInLearningTurnsRemainEligible() {
        assertTrue(AstraLearningSignals.eligible("How do Russian grammatical cases work?"))
        assertTrue(AstraLearningSignals.eligible("Explain bond convexity"))
    }
}
