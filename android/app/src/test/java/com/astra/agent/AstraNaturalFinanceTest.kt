package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraNaturalFinanceTest {
    @Test fun convertsSevenYearPulaQuestionIntoAuditableAnswer() {
        val question = "What will P50,000 grow to at 10% over 7 years?"
        val input = AstraNaturalFinance.parse(question)!!
        assertEquals(AstraNaturalFinance.Operation.FUTURE_VALUE, input.operation)
        assertEquals(50_000.0, input.amount!!, 0.001)
        assertEquals(10.0, input.annualPercent, 0.001)
        assertEquals(7.0, input.years!!, 0.001)
        assertEquals(1, input.compoundsPerYear)
        assertFalse(input.frequencyWasSpecified)
        val answer = AstraNaturalFinance.respond(question)!!
        assertTrue(answer.contains("P97,435.86"))
        assertTrue(answer.contains("P47,435.86"))
        assertTrue(answer.contains("annually (assumed)"))
        assertTrue(answer.contains("FV = P × (1 + r/n)"))
    }

    @Test fun calculatesMonthlyCompoundingWithExplicitFrequency() {
        val answer = AstraNaturalFinance.respond(
            "Calculate compound interest on BWP 10,000 at 10% for 2 years compounded monthly"
        )!!
        assertTrue(answer.contains("Compounding: monthly"))
        assertFalse(answer.contains("monthly (assumed)"))
        assertTrue(answer.contains("P12,203.91"))
    }

    @Test fun presentValueUsesDiscountingRatherThanGrowth() {
        val question = "How much should I invest today to reach P100,000 at 10% in 7 years?"
        val request = AstraNaturalFinance.parse(question)!!
        assertEquals(AstraNaturalFinance.Operation.PRESENT_VALUE, request.operation)
        val reply = AstraNaturalFinance.respond(question)!!
        assertTrue(reply.contains("PRESENT VALUE"))
        assertTrue(reply.contains("P51,315.81"))
        assertTrue(reply.contains("PV = FV / (1 + r/n)"))
    }

    @Test fun handsetPresentValueQuestionWithWrittenOneYearCalculatesP100() {
        val prompt = "What is the present value of P110 received in one year at a 10% discount rate?"
        val parsed = AstraNaturalFinance.parse(prompt)!!
        assertEquals(AstraNaturalFinance.Operation.PRESENT_VALUE, parsed.operation)
        assertEquals(110.0, parsed.amount!!, 0.001)
        assertEquals(10.0, parsed.annualPercent, 0.001)
        assertEquals(1.0, parsed.years!!, 0.001)
        val result = AstraNaturalFinance.respond(prompt)!!
        assertTrue(result.contains("PRESENT VALUE · CALCULATED OFFLINE"))
        assertTrue(result.contains("Future target: P110.00"))
        assertTrue(result.contains("Amount needed today: P100.00"))
        assertFalse(result.contains("P1,100"))
    }

    @Test fun writtenYearHorizonsRequireExactlyOneUnambiguousPeriod() {
        assertEquals(2.0,
            AstraNaturalFinance.parse(
                "What is the present value of P121 received in two years at a 10% discount rate?"
            )!!.years!!, 0.001)
        assertNull(AstraNaturalFinance.respond(
            "What is the present value of P110 in one year and 3 years at 10%?"
        ))
        assertNull(AstraNaturalFinance.respond(
            "What is the present value of P110 in one year or two years at 10%?"
        ))
        assertNull(AstraNaturalFinance.respond(
            "What is the present value of P110 in eleven years at 10%?"
        ))
    }

    @Test fun explainsExactDoublingTimeWithoutAmount() {
        val reply = AstraNaturalFinance.respond(
            "How long will it take to double at 10% compounded monthly?"
        )!!
        assertTrue(reply.contains("DOUBLING TIME"))
        assertTrue(reply.contains("6.96 years"))
        assertTrue(reply.contains("ln(2)"))
    }

    @Test fun doublingTimeCanShowPrincipalAndTarget() {
        val reply = AstraNaturalFinance.respond(
            "How long to double P10,000 at 10% per year?"
        )!!
        assertTrue(reply.contains("P10,000.00"))
        assertTrue(reply.contains("P20,000.00"))
    }

    @Test fun preservesCurrencyWithoutImplyingFxConversion() {
        val usd = AstraNaturalFinance.respond(
            "If I invest $2,000 at 5% for 3 years, how much will I have?"
        )!!
        assertTrue(usd.contains("$2,315.25"))
        assertFalse(usd.contains("P2,315.25"))
        val gbp = AstraNaturalFinance.respond(
            "What will £1,000 grow to at 10% for 3 years?"
        )!!
        assertTrue(gbp.contains("£1,331.00"))
    }

    @Test fun zeroRatesAndZeroYearsAreHandledWithoutNaN() {
        val zero = AstraNaturalFinance.respond(
            "What will P1,000 grow to at 0% for 10 years?"
        )!!
        assertTrue(zero.contains("Future value: P1,000.00"))
        val zeroYears = AstraNaturalFinance.respond(
            "What will P1,000 grow to at 10% for 0 years?"
        )!!
        assertTrue(zeroYears.contains("Future value: P1,000.00"))
        val noDoubling = AstraNaturalFinance.respond("How long to double at 0% per year")!!
        assertTrue(noDoubling.contains("cannot double"))
    }

    @Test fun refusesPeriodicContributionsOrMonthlyRatesAsAnnualLumpSums() {
        assertNull(AstraNaturalFinance.respond(
            "If I invest P1,000 plus monthly contributions at 10% for 7 years?"
        ))
        assertNull(AstraNaturalFinance.respond(
            "What will P2,000 grow to at 10% per month for 3 years?"
        ))
        assertNull(AstraNaturalFinance.respond(
            "If I deposit P500 every month at 10% for 5 years?"
        ))
    }

    @Test fun doesNotInterceptEducationAdviceTradingOrCommands() {
        for (message in listOf(
            "Explain compound interest",
            "What is bond duration?",
            "What should I invest in?",
            "What is the best stock to buy?",
            "Remember I invested P10,000 at 10%",
            "add task calculate compound interest",
            "compound interest 10000 10 2 12",
            "calculate 5 + 7",
            "I invested P10,000 yesterday",
            "What will my P10,000 grow to in 7 years?",
            "What will P10,000 grow to at 10%?",
            "What will P10,000 grow to at 10% for 7 years with P500 added?"
        )) {
            // Unsupported compound cashflow wording must not produce a
            // lump-sum figure; standard older commands are handled elsewhere.
            val result = AstraNaturalFinance.respond(message)
            assertNull("Unexpected intercept for: $message -> $result", result)
        }
    }

    @Test fun rateAndNumericLimitsPreventMisleadingResults() {
        assertNull(AstraNaturalFinance.respond(
            "What will P10,000 grow to at -100% for 5 years?"
        ))
        assertNull(AstraNaturalFinance.respond(
            "What will P10,000 grow to at 20% for 1000 years?"
        ))
        assertNull(AstraNaturalFinance.respond(
            "What will P10,000 grow to at 10% and 12% for 5 years?"
        ))
        assertNull(AstraNaturalFinance.respond(
            "What will P10,000 plus P5,000 grow to at 10% for 7 years?"
        ))
    }
}
