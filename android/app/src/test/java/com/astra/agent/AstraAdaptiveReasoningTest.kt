package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraAdaptiveReasoningTest {
    @Test fun optInAndExplicitOwnerChoiceAreDistinct() {
        val complicated = "Compare these two investment options, evaluate the risks and tradeoffs."
        assertFalse(AstraAdaptiveReasoning.select(complicated, automaticEnabled = false))
        assertTrue(AstraAdaptiveReasoning.select(complicated, automaticEnabled = true))
        assertTrue(AstraAdaptiveReasoning.select(
            "Think carefully: analyze a hypothetical three-step financial plan.",
            automaticEnabled = false
        ))
    }

    @Test fun constrainedPlanningAndLogicSelectExpensiveModeOnlyWhenApproved() {
        for (question in listOf(
            "If every analyst is an accountant and no accountant is a robot, can any analyst be a robot?",
            "First calculate the weighted expected return, then compare the results and the constraints.",
            "Evaluate the costs and assumptions of these two alternative plans."
        )) {
            assertTrue(question, AstraAdaptiveReasoning.select(question, automaticEnabled = true))
        }
    }

    @Test fun ordinaryAndBriefQuestionsRemainFast() {
        for (question in listOf(
            "What is a bond?",
            "Translate bonjour into English.",
            "Tell me more about this lesson.",
            "Briefly compare these two options and evaluate the risks.",
            "Compare these options and evaluate the risks. Reply only with the answer.",
            "Do not think, compare these options and their risks.",
            "What is 19 plus 23? Reply only with the number."
        )) {
            assertFalse(question, AstraAdaptiveReasoning.select(
                question, automaticEnabled = true
            ))
        }
    }

    @Test fun sourceMaterialCannotForceModelModeAndBenchmarkIsIsolated() {
        val trigger = "Compare the two options and assess the tradeoffs."
        assertFalse(AstraAdaptiveReasoning.select(trigger, true, diagnosticIsolated = true))
        assertFalse(AstraAdaptiveReasoning.select(
            "Previous user: " + trigger, automaticEnabled = true
        ))
        assertFalse(AstraAdaptiveReasoning.select(
            "Notes follow:\n" + trigger, automaticEnabled = true
        ))
        assertFalse(AstraAdaptiveReasoning.select(
            "Quoted source: ```" + trigger + "```", automaticEnabled = true
        ))
        assertFalse(AstraAdaptiveReasoning.select(
            "Compare risks and tradeoffs. " + "x".repeat(900),
            automaticEnabled = true
        ))
        assertFalse(AstraAdaptiveReasoning.select(
            "Think carefully: solve a logic problem.",
            automaticEnabled = true,
            diagnosticIsolated = true
        ))
    }

    @Test fun broaderReasoningTasksAreRecognizedWithoutDomainKeywords() {
        for (question in listOf(
            "Determine whether the conclusion follows from the stated premises about the fictional village.",
            "Check whether these premises are consistent given the proposed interpretation of the experiment.",
            "Is the conclusion logically valid if the second premise is false?",
            "Given limited transport and staffing constraints, which plan should we choose?",
            "Under these competing risks and alternatives, justify which plan is better."
        )) {
            assertTrue(question, AstraAdaptiveReasoning.select(question, automaticEnabled = true))
            assertFalse(question, AstraAdaptiveReasoning.select(question, automaticEnabled = false))
            assertFalse(question, AstraAdaptiveReasoning.select(question, true, diagnosticIsolated = true))
        }
    }

    @Test fun quotedReasoningPhrasesAndLanguageTasksRemainFast() {
        for (question in listOf(
            "Translate the sentence compare these two options and evaluate the risks into French.",
            "What does the phrase compare two alternatives and assess the risks mean?",
            "Please paraphrase determine whether the conclusion follows from the stated premises.",
            "Define the expression compare these risks and evaluate the alternatives.",
            "Briefly determine whether the conclusion follows from the stated premises.",
            "Determine whether the conclusion follows from the stated premises. Answer only with yes or no."
        )) {
            assertFalse(question, AstraAdaptiveReasoning.select(question, automaticEnabled = true))
        }
    }
}
