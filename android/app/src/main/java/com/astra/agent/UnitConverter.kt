package com.astra.agent

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Offline unit conversions using fixed physical definitions, not live data.
 * Currency conversions are intentionally excluded because exchange rates change.
 */
internal object UnitConverter {
    private data class UnitDef(
        val symbol: String,
        val dimension: String,
        val baseFactor: Double
    )

    private val units = mutableMapOf<String, UnitDef>().apply {
        fun register(symbol: String, dimension: String, factor: Double, vararg aliases: String) {
            val definition = UnitDef(symbol, dimension, factor)
            (aliases.toList() + symbol).forEach { put(it, definition) }
        }

        register("mm", "length", 0.001, "millimeter", "millimeters", "millimetre", "millimetres")
        register("cm", "length", 0.01, "centimeter", "centimeters", "centimetre", "centimetres")
        register("m", "length", 1.0, "meter", "meters", "metre", "metres")
        register("km", "length", 1000.0, "kilometer", "kilometers", "kilometre", "kilometres")
        register("mi", "length", 1609.344, "mile", "miles")
        register("ft", "length", 0.3048, "foot", "feet")
        register("in", "length", 0.0254, "inch", "inches")
        register("yd", "length", 0.9144, "yard", "yards")
        register("g", "mass", 0.001, "gram", "grams")
        register("kg", "mass", 1.0, "kilogram", "kilograms")
        register("lb", "mass", 0.45359237, "lbs", "pound", "pounds")
        register("oz", "mass", 0.028349523125, "ounce", "ounces")
        register("ml", "volume", 0.001, "milliliter", "milliliters", "millilitre", "millilitres")
        register("l", "volume", 1.0, "liter", "liters", "litre", "litres")
        register("cup", "volume", 0.2365882365, "cups")
        register("gal", "volume", 3.785411784, "gallon", "gallons")
        register("s", "time", 1.0, "sec", "second", "seconds")
        register("min", "time", 60.0, "minute", "minutes", "mins")
        register("h", "time", 3600.0, "hr", "hour", "hours", "hrs")
        register("day", "time", 86400.0, "days")
        register("°C", "temperature", 1.0, "c", "celsius", "°c")
        register("°F", "temperature", 1.0, "f", "fahrenheit", "°f")
        register("K", "temperature", 1.0, "k", "kelvin")
    }

    private val command = Regex(
        """(?i)^(?:convert\s+)?([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*([a-z°]+)\s+(?:to|into)\s+([a-z°]+)$"""
    )

    fun respond(raw: String): String? {
        val match = command.matchEntire(raw.trim()) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull()
            ?: return "Enter a valid number to convert."
        if (!amount.isFinite()) return "Enter a finite number to convert."

        val source = units[match.groupValues[2].lowercase(Locale.ROOT)]
        val destination = units[match.groupValues[3].lowercase(Locale.ROOT)]
        if (source == null || destination == null) {
            return "Unknown unit. Try: convert 5 km to miles, convert 75 kg to lb, " +
                "convert 25 c to f, or convert 2 hours to minutes."
        }
        if (source.dimension != destination.dimension) {
            return "Cannot convert " + source.dimension + " into " + destination.dimension + "."
        }

        val result = if (source.dimension == "temperature") {
            val celsius = when (source.symbol) {
                "°C" -> amount
                "°F" -> (amount - 32.0) * 5.0 / 9.0
                else -> amount - 273.15
            }
            if (celsius < -273.15 - 1e-8) return "Temperature cannot be below absolute zero."
            when (destination.symbol) {
                "°C" -> celsius
                "°F" -> celsius * 9.0 / 5.0 + 32.0
                else -> celsius + 273.15
            }
        } else {
            amount * source.baseFactor / destination.baseFactor
        }

        if (!result.isFinite()) return "The converted value is too large to display."
        val suffix = if (source.symbol == "cup" || source.symbol == "gal" ||
            destination.symbol == "cup" || destination.symbol == "gal"
        ) " (US customary volume)" else ""

        return number(amount) + " " + source.symbol + " = " +
            number(result) + " " + destination.symbol + suffix
    }

    private fun number(value: Double): String {
        val adjusted = if (value == 0.0) 0.0 else value
        return BigDecimal.valueOf(adjusted)
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}
