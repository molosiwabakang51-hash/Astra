package com.astra.agent

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Fixed-coupon bond pricing at a coupon payment date.
 *
 * No market feeds, day-count calendars, accrued-interest adjustments,
 * callable features, taxes, credit adjustments or curve bootstrapping.
 * Annual coupon and nominal annual yield are expressed in percent.
 */
internal object BondAnalytics {
    internal data class Terms(
        val face: Double,
        val annualCouponPercent: Double,
        val annualYieldPercent: Double,
        val yearsToMaturity: Double,
        val paymentsPerYear: Int = 2
    )

    internal data class Metrics(
        val price: Double,
        val annualCouponAmount: Double,
        val currentYieldPercent: Double,
        val macaulayDurationYears: Double,
        val modifiedDurationYears: Double,
        val convexityYearsSquared: Double,
        val dv01: Double
    )

    private val command = Regex(
        """(?i)^bond\s+(?:analy[sz]e|analysis|price|duration|convexity)\s+([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s+([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s+([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s+([+-]?(?:\d+(?:\.\d*)?|\.\d+))(?:\s+(\d+))?$"""
    )
    private val supportedFrequencies = setOf(1, 2, 4, 12)

    fun respond(raw: String): String? {
        val input = raw.trim().trimEnd('?', '!', '.').trim()
        if (input.matches(Regex("""(?i)^bond\s+(?:analy[sz]e|analysis|price|duration|convexity)$"""))) {
            return usage()
        }
        val match = command.matchEntire(input) ?: return null
        val face = match.groupValues[1].toDoubleOrNull() ?: return usage()
        val coupon = match.groupValues[2].toDoubleOrNull() ?: return usage()
        val ytm = match.groupValues[3].toDoubleOrNull() ?: return usage()
        val years = match.groupValues[4].toDoubleOrNull() ?: return usage()
        val frequency = match.groupValues[5].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 2

        return try {
            val terms = Terms(face, coupon, ytm, years, frequency)
            val result = calculate(terms)
            "ASTRA // BOND ANALYTICS\n\n" +
                "Face value: " + number(face, 2) + "\n" +
                "Annual coupon: " + number(coupon, 4) + "%\n" +
                "Annual YTM (nominal): " + number(ytm, 4) + "%\n" +
                "Years to maturity: " + number(years, 4) + "\n" +
                "Coupon payments per year: " + frequency + "\n\n" +
                "PRICE: " + number(result.price, 4) + "\n" +
                "Current yield: " + number(result.currentYieldPercent, 4) + "%\n" +
                "Macaulay duration: " + number(result.macaulayDurationYears, 4) + " years\n" +
                "Modified duration: " + number(result.modifiedDurationYears, 4) + " years\n" +
                "Convexity: " + number(result.convexityYearsSquared, 4) + " years²\n" +
                "DV01: " + number(result.dv01, 6) + " price units for a 1bp yield move\n\n" +
                "A 1bp yield increase implies an approximate price decrease of " +
                number(result.dv01, 6) + " (first-order duration estimate).\n" +
                "Assumes fixed coupons, a flat YTM, cash flows at period-end and valuation " +
                "immediately after a coupon date. Not a live quote or investment recommendation."
        } catch (invalid: IllegalArgumentException) {
            "Bond parameters are invalid: " + (invalid.message ?: "Check the inputs.") +
                "\n" + usage()
        }
    }

    internal fun calculate(input: Terms): Metrics {
        require(input.face.isFinite() && input.face > 0 && input.face <= 1e12) {
            "Face value must be positive and at most 1 trillion."
        }
        require(input.annualCouponPercent.isFinite() &&
            input.annualCouponPercent in 0.0..100.0
        ) { "Coupon rate must be between 0% and 100%." }
        require(input.annualYieldPercent.isFinite() &&
            input.annualYieldPercent in -99.0..500.0
        ) { "Annual yield must be between -99% and 500%." }
        require(input.yearsToMaturity.isFinite() && input.yearsToMaturity > 0) {
            "Time to maturity must be positive."
        }
        require(input.paymentsPerYear in supportedFrequencies) {
            "Payments per year must be 1, 2, 4, or 12."
        }

        val periodCountDouble = input.yearsToMaturity * input.paymentsPerYear
        require(periodCountDouble.isFinite() && periodCountDouble in 1.0..1000.0) {
            "Use 1 to 1000 coupon periods."
        }
        val periods = periodCountDouble.roundToInt()
        require(abs(periodCountDouble - periods) < 1e-8) {
            "Maturity must align with a whole number of coupon periods."
        }
        val y = input.annualYieldPercent / 100.0
        val periodicYield = y / input.paymentsPerYear
        val discountBase = 1.0 + periodicYield
        require(discountBase > 0.0) {
            "Yield cannot produce a nonpositive discount factor."
        }

        val annualCoupon = input.face * input.annualCouponPercent / 100.0
        val periodCoupon = annualCoupon / input.paymentsPerYear
        var price = 0.0
        var weightedTime = 0.0
        var convexityNumerator = 0.0

        for (period in 1..periods) {
            val payment = periodCoupon + if (period == periods) input.face else 0.0
            val presentValue = payment / discountBase.pow(period)
            val timeYears = period.toDouble() / input.paymentsPerYear
            price += presentValue
            weightedTime += presentValue * timeYears
            convexityNumerator += presentValue *
                period.toDouble() * (period + 1).toDouble()
        }
        require(price.isFinite() && price > 0.0) {
            "Resulting bond price is not finite or positive."
        }

        val macaulay = weightedTime / price
        val modified = macaulay / discountBase
        val convexity = convexityNumerator /
            (price * input.paymentsPerYear.toDouble().pow(2) * discountBase.pow(2))
        val dv01 = price * modified * 0.0001
        val currentYield = annualCoupon / price * 100.0

        require(listOf(macaulay, modified, convexity, dv01, currentYield).all {
            it.isFinite()
        }) { "Calculated duration or convexity exceeds numeric limits." }

        return Metrics(price, annualCoupon, currentYield, macaulay, modified, convexity, dv01)
    }

    private fun number(value: Double, scale: Int): String =
        BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()

    private fun usage(): String =
        "Try: bond analyze 1000 5 6 10 2\n" +
            "Order: face value, annual coupon %, annual nominal YTM %, years, " +
            "coupon payments per year (optional, default 2). " +
            "Payments per year: 1, 2, 4 or 12."
}
