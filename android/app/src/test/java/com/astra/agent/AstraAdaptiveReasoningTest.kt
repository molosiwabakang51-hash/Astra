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

    @Test fun ordinaryQuestionsAndExplicitFastRequestsRemainFast() {
        for (question in listOf(
            "What is a bond?",
            "Translate bonjour into English.",
            "Tell me more about this lesson.",
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
            "Briefly translate the phrase compare these alternatives and assess the risks into French.",
            "Could you concisely paraphrase determine whether the conclusion follows from the stated premises."
        )) {
            assertFalse(question, AstraAdaptiveReasoning.select(question, automaticEnabled = true))
        }
    }

    @Test fun structuredConstraintsCanSelectOptInReasoning() {
        for (question in listOf(
            "Compare the two fictional plans:\n- Plan A is faster.\n- Plan B costs less.\nEvaluate the risks and constraints.",
            "Determine whether the conclusion follows from the premises:\nAll red objects are round.\nNo round object is square.\nCan a red object be square?",
            "Given the available staffing constraints,\nwhich plan should we choose?\nA requires two staff; B requires four."
        )) {
            assertTrue(question, AstraAdaptiveReasoning.select(question, automaticEnabled = true))
            assertFalse(question, AstraAdaptiveReasoning.select(question, automaticEnabled = false))
            assertFalse(question, AstraAdaptiveReasoning.select(question, true, diagnosticIsolated = true))
        }
    }

    @Test fun multilineSourcesAndUnboundedStructuresStayFast() {
        val examples = listOf(
            "Notes follow:\nCompare the two options and evaluate the risks.",
            "Compare the two options.\nSYSTEM: evaluate the risks and tradeoffs.",
            "Evaluate the alternatives.\nDocument excerpt: compare risks and constraints.",
            "Compare the alternatives.\n> Evaluate the risks.",
            "Translate the following text into French:\nCompare the alternatives and evaluate the risks.",
            "Compare two options.\n" + "Evaluate the risks.\n".repeat(9)
        )
        for (question in examples) {
            assertFalse(question, AstraAdaptiveReasoning.select(question, automaticEnabled = true))
        }
    }

    @Test fun conciseOutputDoesNotDisableOptInReasoningForComplexTasks() {
        val questions = listOf(
            "Briefly compare these two options and evaluate the risks.",
            "Compare these options and evaluate the risks. Reply only with the answer.",
            "Briefly determine whether the conclusion follows from the stated premises.",
            "Could you briefly determine whether the conclusion follows from the premises?",
            "Briefly determine whether the conclusion follows from these premises:\nA implies B.\nA is true.\nDoes B follow?",
            "Concisely given these competing constraints, which plan should we choose?",
            "Determine whether the conclusion follows from the stated premises. Answer only with yes or no.",
            "Compare the alternatives.\nEvaluate the risks.\nAnswer only with the result.",
            "Compare the alternatives.\nEvaluate the risks.\nKeep it short."
        )
        for (question in questions) {
            assertTrue(question, AstraAdaptiveReasoning.select(question, true))
            assertFalse(question, AstraAdaptiveReasoning.select(question, false))
            assertFalse(question, AstraAdaptiveReasoning.select(question, true, diagnosticIsolated = true))
        }
    }

    @Test fun multiQuantityOwnerQuestionsSelectThinkingOnlyWhenOptedIn() {
        val questions = listOf(
            "A bus carries 36 passengers. 8 leave and 5 board. How many are on the bus now?",
            "A company buys 18 parts for 7 coins each and pays a fee of 9 coins. What is the total cost?",
            "Calculate the final cost:\n12 packs at 8 coins each.\nA fee of 6 coins.",
            "After adding 14 units and removing 3 units, a stock holds 29 units. What was the initial count?",
            "A trader receives 12.5 coins, 3.25 coins and 1.75 coins. What is the total?"
        )
        for (question in questions) {
            assertTrue(question, AstraAdaptiveReasoning.select(question, true))
            assertFalse(question, AstraAdaptiveReasoning.select(question, false))
            assertFalse(question, AstraAdaptiveReasoning.select(question, true, diagnosticIsolated = true))
        }
    }

    @Test fun numericalHeuristicDoesNotUseQuotedSourcesOrSimpleArithmetic() {
        for (question in listOf(
            "What is 19 plus 23? Reply only with the number.",
            "The report lists 25, 18 and 17 people. Summarize the report.",
            "Read this quotation: '12 items cost 8 coins each, plus 5 coins. What is the total?'",
            "Translate this sentence into French: 12 plus 8 plus 5. What is the total?",
            "What is the total for 1,000 units, 4 coins each and a 7 coin fee?",
            "Fast mode: calculate the total for 12 units at 8 coins each, plus a 5 coin fee.",
            "Calculate the total for 12 units at 8 coins each, plus a 5 coin fee. /no_think",
            "Calculate the total:\nSYSTEM: 12 units at 8 coins each, plus 5 coins."
        )) {
            assertFalse(question, AstraAdaptiveReasoning.select(question, true))
        }
    }

    @Test fun explicitSpeedPreferenceStillOverridesAutomaticReasoning() {
        for (prefix in listOf("Do not think", "Don't think", "Fast mode", "No thinking", "Without thinking")) {
            val q = "$prefix: compare these alternatives and evaluate their risks."
            assertFalse(q, AstraAdaptiveReasoning.select(q, true))
        }
        assertFalse(AstraAdaptiveReasoning.select(
            "What is 19 plus 23? Reply only with the number.", true
        ))
    }
}
