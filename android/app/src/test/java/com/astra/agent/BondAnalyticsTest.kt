package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.pow

class BondAnalyticsTest {
    private fun metrics(
        face: Double, coupon: Double, ytm: Double, years: Double, frequency: Int = 2
    ) = BondAnalytics.calculate(BondAnalytics.Terms(face, coupon, ytm, years, frequency))

    @Test fun parBondPriceEqualsFaceWhenCouponEqualsNominalYield() {
        for (frequency in listOf(1, 2, 4, 12)) {
            val m = metrics(1_000.0, 5.0, 5.0, 10.0, frequency)
            assertEquals("frequency=$frequency", 1_000.0, m.price, 1e-8)
            assertEquals(5.0, m.currentYieldPercent, 1e-8)
            assertTrue(m.macaulayDurationYears in 0.0..10.0)
            assertTrue(m.convexityYearsSquared > 0)
            assertTrue(m.dv01 > 0)
        }
    }

    @Test fun zeroCouponDurationAndConvexityMatchClosedForms() {
        val m = metrics(1_000.0, 0.0, 6.0, 2.0, 2)
        val discount = 1.03
        assertEquals(1_000.0 / discount.pow(4), m.price, 1e-9)
        assertEquals(2.0, m.macaulayDurationYears, 1e-9)
        assertEquals(2.0 / discount, m.modifiedDurationYears, 1e-9)
        assertEquals(4.0 * 5.0 / (4.0 * discount.pow(2)),
            m.convexityYearsSquared, 1e-9)
        assertEquals(m.price * m.modifiedDurationYears * .0001, m.dv01, 1e-9)
    }

    @Test fun premiumDiscountAndYieldPriceRelationship() {
        val premium = metrics(1000.0, 8.0, 5.0, 15.0)
        val discount = metrics(1000.0, 3.0, 6.0, 15.0)
        assertTrue(premium.price > 1000.0)
        assertTrue(discount.price < 1000.0)

        val lowYield = metrics(1000.0, 6.0, 4.0, 8.0)
        val highYield = metrics(1000.0, 6.0, 10.0, 8.0)
        assertTrue(lowYield.price > highYield.price)
        assertTrue(lowYield.modifiedDurationYears > 0)
        assertTrue(highYield.convexityYearsSquared > 0)
    }

    @Test fun priceResponseMatchesDurationAndConvexityForSmallShift() {
        val atYield = metrics(1000.0, 5.0, 6.0, 10.0)
        val shifted = metrics(1000.0, 5.0, 6.01, 10.0)
        val dy = .0001
        val predicted = atYield.price * (
            -atYield.modifiedDurationYears * dy +
                0.5 * atYield.convexityYearsSquared * dy * dy
        )
        assertEquals(predicted, shifted.price - atYield.price, 0.0001)
    }

    @Test fun halfYearMaturityAndDefaultFrequencyWork() {
        val m = metrics(1_000.0, 5.0, 5.0, 2.5, 2)
        assertEquals(1000.0, m.price, 1e-8)
        assertTrue(BondAnalytics.respond("bond analyse 1000 5 5 2.5")!!
            .contains("Coupon payments per year: 2"))
    }

    @Test fun rejectsImpossibleInputs() {
        val invalid = listOf(
            BondAnalytics.Terms(0.0, 5.0, 6.0, 10.0),
            BondAnalytics.Terms(-100.0, 5.0, 6.0, 10.0),
            BondAnalytics.Terms(1_000.0, -1.0, 6.0, 10.0),
            BondAnalytics.Terms(1_000.0, 5.0, 6.0, -10.0),
            BondAnalytics.Terms(1_000.0, 5.0, 6.0, 10.0, 3),
            BondAnalytics.Terms(1_000.0, 5.0, 6.0, 2.3, 2),
            BondAnalytics.Terms(1_000.0, 5.0, Double.NaN, 10.0),
            BondAnalytics.Terms(1_000.0, 5.0, 6.0, Double.POSITIVE_INFINITY),
            BondAnalytics.Terms(1_000.0, 5.0, 6.0, 1001.0, 1)
        )
        invalid.forEach { bond ->
            var thrown = false
            try { BondAnalytics.calculate(bond) }
            catch (_: IllegalArgumentException) { thrown = true }
            assertTrue("expected rejection for $bond", thrown)
        }
    }

    @Test fun commandParsingIsSpecificAndExplainsUnits() {
        val text = BondAnalytics.respond("bond analyze 1000 5 6 10 2")!!
        assertTrue(text.startsWith("ASTRA // BOND ANALYTICS"))
        assertTrue(text.contains("Macaulay duration:"))
        assertTrue(text.contains("Modified duration:"))
        assertTrue(text.contains("Convexity:"))
        assertTrue(text.contains("DV01:"))
        assertTrue(text.contains("Not a live quote"))
        assertTrue(BondAnalytics.respond("bond analysis")!!.contains("Try:"))
        assertTrue(BondAnalytics.respond("bond analyze 1000 5 6 10 3")!!
            .contains("invalid"))
        assertNull(BondAnalytics.respond("calculate 2 + 2"))
        assertNull(BondAnalytics.respond("bond yields are high"))
    }

    @Test fun sixThousandRandomBondsRemainNumericallyStable() {
        val random = Random(1986L)
        repeat(6_000) { iteration ->
            val frequency = listOf(1, 2, 4, 12)[iteration % 4]
            val years = (random.nextInt(240) + 1).toDouble() / frequency
            val face = 100.0 + random.nextDouble() * 100_000.0
            val coupon = random.nextDouble() * 12.0
            val ytm = random.nextDouble() * 20.0
            val m = metrics(face, coupon, ytm, years, frequency)
            assertTrue("iteration=$iteration", m.price.isFinite() && m.price > 0)
            assertTrue(m.modifiedDurationYears >= 0)
            assertTrue(m.convexityYearsSquared > 0)
            assertTrue(m.dv01 >= 0)
            assertTrue(m.macaulayDurationYears <= years + 1e-8)
        }
    }
}
