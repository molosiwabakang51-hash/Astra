package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConverterTest {
    @Test fun physicalConversionsUseDefinedFactors() {
        assertEquals("5 km = 3.106856 mi", UnitConverter.respond("convert 5 km to miles"))
        assertEquals("1 kg = 2.204623 lb", UnitConverter.respond("1 kg to lb"))
        assertEquals("100 °C = 212 °F", UnitConverter.respond("convert 100 celsius to fahrenheit"))
        assertEquals("32 °F = 0 °C", UnitConverter.respond("32 f to c"))
    }

    @Test fun invalidConversionsGiveUsefulMessage() {
        assertTrue(UnitConverter.respond("convert 5 km to kg")!!.contains("Cannot convert"))
        assertTrue(UnitConverter.respond("convert 2 apples to pears")!!.contains("Unknown unit"))
        assertTrue(UnitConverter.respond("convert -1 kelvin to celsius")!!.contains("absolute zero"))
    }

    @Test fun normalCommandsPassThrough() {
        assertNull(UnitConverter.respond("add task call home"))
    }
}
