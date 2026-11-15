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

    @Test fun ordinaryOptedInLearningTurnsRemainEligible() {
        assertTrue(AstraLearningSignals.eligible("How do Russian grammatical cases work?"))
        assertTrue(AstraLearningSignals.eligible("Explain bond convexity"))
    }
}
